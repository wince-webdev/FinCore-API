package com.wince.fincore.identity.application;

import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.api.LoginRequest;
import com.wince.fincore.identity.api.RegisterRequest;
import com.wince.fincore.identity.api.AuthResponse;
import com.wince.fincore.identity.domain.AppUser;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.identity.repository.AppUserRepository;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Cas d'usage F-01 : <strong>inscription, connexion, rafraîchissement,
 * déconnexion</strong> (RG-AUTH-01 à RG-AUTH-06).
 *
 * <p>Le service orchestre les trois pièces déjà en place :
 * {@link PasswordPolicy} (règles de robustesse), {@link JwtService}
 * (access token 15 min) et {@link RefreshTokenService} (refresh token
 * opaque 7 jours, rotation, révocation de famille). Les règles de
 * verrouillage (RG-AUTH-05) vivent ici car elles combinent un état de
 * l'utilisateur, l'horloge et le hachage — aucune des trois pièces
 * isolées ne peut les implémenter.</p>
 *
 * <p><strong>Pas de {@code @Transactional} sur la classe</strong> : chaque
 * écriture est déjà atomique au niveau du dépôt (une sauvegarde = une
 * transaction). En revanche la rotation des refresh tokens est
 * transactionnelle <em>dans</em> {@link RefreshTokenService}, là où deux
 * écritures doivent être liées.</p>
 *
 * <p>Équivalent Symfony : le contrôleur Security + le
 * {@code UserAuthenticator} du bundle Security.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    /**
     * Empreinte BCrypt d'un mot de passe jetable, calculée au démarrage.
     * Elle sert à chronométrer une connexion vers un utilisateur
     * <strong>inconnu</strong> comme si le mot de passe avait été vérifié
     * (contre-mesure anti-énumération, RG-AUTH-05).
     */
    private final String unknownUserHash;

    public AuthService(
            AppUserRepository userRepository,
            CustomerRepository customerRepository,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            Clock clock,
            @Value("${fincore.security.max-failed-attempts:5}") long maxFailedAttempts,
            @Value("${fincore.security.lock-minutes:15}") long lockMinutes) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
        this.maxFailedAttempts = (int) maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // ── F-01 · Inscription ──────────────────────────────────────────────

    /**
     * Inscription publique : crée <strong>uniquement</strong> un
     * utilisateur {@code CUSTOMER} (RG-AUTH-02) et ouvre immédiatement une
     * session — le client n'a pas à se reconnecter juste après s'être
     * inscrit.
     *
     * @throws FinCoreException {@code VALIDATION_FAILED} (400) si le mot de
     *         passe est faible (liste des règles manquantes) ou si l'e-mail
     *         est déjà utilisé
     */
    public AuthResponse register(RegisterRequest request) {
        passwordPolicy.validate(request.password());
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailAlreadyUsed();
        }

        AppUser user = new AppUser(email,
                passwordEncoder.encode(request.password()), Role.CUSTOMER);

        // F-02 · RG-KYC-01 : le profil du client est construit AVANT toute
        // écriture — il arrive en LEVEL_1 / PENDING à l'inscription.
        Customer customer = new Customer(UUID.randomUUID(), user.getId(),
                request.firstName(), request.lastName(), request.birthDate(), request.phone());

        // RG-KYC-04 : majeure à l'inscription — vérifiée AVANT la
        // persistance : un mineur ne crée ni AppUser ni Customer.
        if (!customer.isAdult(LocalDate.ofInstant(clock.instant(), ClockConfig.BANKING_ZONE))) {
            throw new FinCoreException(ErrorCode.CUSTOMER_UNDERAGE,
                    "Le client a moins de 18 ans à l'inscription (RG-KYC-04).");
        }

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException race) {
            // Deux inscriptions simultanées pour la même adresse : c'est
            // l'UNIQUE en base qui tranche, et on répond exactement comme
            // pour le contrôle préalable (un seul chemin visible).
            throw emailAlreadyUsed();
        }

        customerRepository.save(customer);

        log.info("Inscription : userId={} role=CUSTOMER", user.getId());
        return issueTokens(user);
    }

    // ── F-01 · Connexion ────────────────────────────────────────────────

    /**
     * Connexion : vérifie le mot de passe, applique le verrouillage après
     * 5 échecs (RG-AUTH-05) puis émet les deux jetons (RG-AUTH-03).
     *
     * @throws FinCoreException {@code UNAUTHENTICATED} (401) message
     *         <strong>identique</strong> pour « utilisateur inconnu » et
     *         « mauvais mot de passe » ; {@code ACCOUNT_LOCKED} (423) tant
     *         que le verrou n'a pas expiré (CA-AUTH-02)
     */
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        Optional<AppUser> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            // Contre-mesure : on passe par le même hachage que pour un
            // compte existant, sinon le délai de réponse suffirait à
            // savoir si l'adresse est inscrite.
            passwordEncoder.matches(request.password(), unknownUserHash);
            throw invalidCredentials();
        }

        AppUser user = found.get();
        if (!user.isEnabled()) {
            throw invalidCredentials();
        }

        Instant now = clock.instant();
        if (user.isLockedAt(now)) {
            throw accountLocked(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean lockedNow = user.registerFailedAttempt(maxFailedAttempts, now.plus(lockDuration));
            userRepository.save(user);
            if (lockedNow) {
                log.warn("Compte verrouillé après {} échec(s) : userId={}",
                        user.getFailedAttempts(), user.getId());
                throw accountLocked(user.getLockedUntil());
            }
            throw invalidCredentials();
        }

        user.clearFailedAttempts();
        userRepository.save(user);
        return issueTokens(user);
    }

    // ── F-01 · Rotation du refresh token ────────────────────────────────

    /**
     * Échange un refresh token contre une <strong>nouvelle paire</strong>
     * de jetons (RG-AUTH-04) : le token présenté est consommé et un
     * access token frais est émis.
     *
     * @throws FinCoreException {@code UNAUTHENTICATED} (401) si le token est
     *         inconnu, expiré ou <strong>déjà utilisé</strong> — auquel cas
     *         toute la famille de jetons est révoquée (CA-AUTH-03)
     */
    public AuthResponse refresh(String presentedRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(presentedRefreshToken);
        // On recharge l'utilisateur (relation lazy = transaction close) et
        // on vérifie qu'il est toujours actif : un compte désactivé entre
        // deux ne doit plus obtenir d'access token.
        AppUser user = userRepository.findById(rotation.userId())
                .filter(AppUser::isEnabled)
                .orElseThrow(AuthService::invalidCredentials);
        return response(user, jwtService.issueAccessToken(user), rotation.refreshToken());
    }

    // ── F-01 · Déconnexion ──────────────────────────────────────────────

    /**
     * Révoque le refresh token présenté (logout) — silencieux si le jeton
     * est déjà révoqué ou inconnu : se déconnecter ne doit jamais exposer
     * d'erreur.
     */
    public void logout(String presentedRefreshToken) {
        refreshTokenService.revoke(presentedRefreshToken);
    }

    // ── Internes ────────────────────────────────────────────────────────

    /** Accouplement des deux jetons : une seule fabrique de réponse. */
    private AuthResponse issueTokens(AppUser user) {
        return response(user,
                jwtService.issueAccessToken(user),
                refreshTokenService.issue(user));
    }

    private AuthResponse response(AppUser user, String accessToken, String refreshToken) {
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                "Bearer",
                accessToken,
                jwtService.accessTokenTtl().toSeconds(),
                refreshToken);
    }

    /** E-mail trimé et en minuscules : l'unicité est insensible à la casse. */
    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** 401 volontairement non informatif (RG-AUTH-05). */
    private static FinCoreException invalidCredentials() {
        return new FinCoreException(ErrorCode.UNAUTHENTICATED, "Identifiants invalides.");
    }

    /** 423 avec l'échéance du verrou (CA-AUTH-02). */
    private static FinCoreException accountLocked(Instant lockedUntil) {
        return new FinCoreException(
                ErrorCode.ACCOUNT_LOCKED,
                "Compte verrouillé suite à des tentatives échouées. Réessayez après expiration du verrou.",
                Map.of("lockedUntil", lockedUntil.toString()));
    }

    /** 400 : e-mail déjà pris — champ `email` du corps d'erreur RFC 9457. */
    private static FinCoreException emailAlreadyUsed() {
        return new FinCoreException(
                ErrorCode.VALIDATION_FAILED,
                "Un compte existe déjà avec cette adresse e-mail.",
                Map.of("errors", Map.of("email", "adresse déjà utilisée")));
    }
}
