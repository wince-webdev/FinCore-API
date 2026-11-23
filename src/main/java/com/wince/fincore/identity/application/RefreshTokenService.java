package com.wince.fincore.identity.application;

import com.wince.fincore.identity.domain.AppUser;
import com.wince.fincore.identity.domain.RefreshToken;
import com.wince.fincore.identity.repository.RefreshTokenRepository;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cycle de vie des <strong>refresh tokens</strong> (RG-AUTH-03, RG-AUTH-04).
 *
 * <p>Un refresh token est une chaîne opaque de 256 bits d'entropie
 * (32 octets aléatoires en base64url). Le serveur ne conserve que son
 * <strong>SHA-256</strong> : une fuite de base de données ne permet pas
 * de rejouer un token.</p>
 *
 * <p><strong>Rotation</strong> : chaque `/refresh` consomme le token
 * présenté (le révoque) et en émet un nouveau. <strong>Réutilisation</strong>
 * d'un token déjà consommé = signal de vol → tous les tokens actifs de
 * l'utilisateur sont révoqués d'un coup (« famille »).</p>
 *
 * <p>Équivalent Symfony : la logique de rotation du bundle
 * {@code gesdinet/refresh-token-bundle}.</p>
 */
@Service
public class RefreshTokenService {

    /** 256 bits d'entropie — indevinable, y compris en ligne. */
    private static final int TOKEN_BYTES = 32;

    /**
     * Résultat d'une rotation : le nouveau jeton <strong>en clair</strong>
     * et l'identifiant de son propriétaire.
     *
     * <p>On transporte l'UUID plutôt que l'entité {@link AppUser} : l'entité
     * serait détachée (transaction close) et sa relation lazy provoquerait
     * une {@code LazyInitializationException} dès la première lecture d'un
     * champ dans le contrôleur.</p>
     */
    public record Rotation(String refreshToken, UUID userId) {
    }

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    /**
     * Transaction « à part » pour la sanction de réutilisation : elle doit
     * être <strong>committée</strong> avant que {@link #rotate} ne lève
     * l'exception (sinon le rollback annulerait la révocation — CA-AUTH-03).
     */
    private final TransactionTemplate revokeFamilyInNewTransaction;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${fincore.security.refresh-ttl-days:7}") long refreshTokenTtlDays) {
        this.repository = repository;
        this.clock = clock;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
        this.revokeFamilyInNewTransaction = new TransactionTemplate(transactionManager);
        this.revokeFamilyInNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Émet un nouveau refresh token pour l'utilisateur.
     *
     * @return la valeur <strong>en clair</strong> à renvoyer au client —
     *         seule fois où elle quitte le serveur
     */
    @Transactional
    public String issue(AppUser user) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String clearValue = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken token = new RefreshToken(user, sha256Hex(clearValue),
                clock.instant().plus(refreshTokenTtl));
        repository.save(token);
        return clearValue;
    }

    /**
     * Consomme (révoque) le token présenté et en émet un nouveau.
     *
     * <p>C'est le cœur de la rotation RG-AUTH-04 : le token passé ne
     * peut plus servir, il est remplacé dans la chaîne.</p>
     *
     * <p><strong>Le piège des deux transactions (CA-AUTH-03)</strong> :
     * la sanction « réutilisation détectée » doit rester en base <em>après</em>
     * le 401 renvoyé au client. Or l'exception métier provoque le rollback
     * de cette méthode : si la révocation était exécutée ici, elle serait
     * annulée et le jeton volé resterait valide. Elle part donc dans une
     * transaction séparée ({@code REQUIRES_NEW}) qui commit avant la levée de
     * l'exception.</p>
     *
     * @return le nouveau jeton en clair et son propriétaire
     * @throws FinCoreException {@code UNAUTHENTICATED} si le token est
     *         inconnu, expiré, ou <strong>déjà consommé</strong>
     */
    @Transactional
    public Rotation rotate(String presentedToken) {
        RefreshToken current = requireValid(presentedToken);

        // Réutilisation détectée → sanction : révocation de toute la
        // famille (tous les tokens actifs de l'utilisateur), committée
        // dans une transaction autonome AVANT de lever l'exception.
        if (current.isRevoked()) {
            // userId lu AVANT la transaction séparée (l'EntityManager externe
            // est suspendu pendant son exécution — pas de lazy load dedans).
            UUID userId = current.getUser().getId();
            int revoked = revokeFamilyInNewTransaction.execute(
                    status -> repository.revokeAllForUser(userId, clock.instant()));
            throw new FinCoreException(ErrorCode.UNAUTHENTICATED,
                    "Refresh token déjà utilisé : la session a été révoquée ("
                            + revoked + " jeton(s)).");
        }

        String newClearValue = issue(current.getUser());
        // La valeur n'étant plus disponible, on relie la chaîne par le hash
        // du successeur : on re-le lookup pour récupérer l'entité fraîche.
        RefreshToken successor = repository.findByTokenHash(sha256Hex(newClearValue))
                .orElseThrow(() -> new FinCoreException(ErrorCode.INTERNAL_ERROR,
                        "Rotation de refresh token échouée."));
        current.revokeAsReplacedBy(successor.getId(), clock.instant());
        repository.save(current);
        return new Rotation(newClearValue, current.getUser().getId());
    }

    /** Révoque un token (logout) — silencieux si déjà révoqué. */
    @Transactional
    public void revoke(String presentedToken) {
        findByClearValue(presentedToken).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(clock.instant());
                repository.save(token);
            }
        });
    }

    /**
     * Valide le token présenté : existe, non expiré, non révoqué.
     *
     * @throws FinCoreException {@code UNAUTHENTICATED} dans tous les cas
     *         d'échec — message identique pour ne pas révéler pourquoi
     */
    @Transactional
    public RefreshToken requireValid(String presentedToken) {
        Optional<RefreshToken> found = findByClearValue(presentedToken);
        if (found.isEmpty()) {
            throw new FinCoreException(ErrorCode.UNAUTHENTICATED,
                    "Refresh token invalide.");
        }
        RefreshToken token = found.get();
        Instant now = clock.instant();
        if (token.isExpiredAt(now)) {
            throw new FinCoreException(ErrorCode.UNAUTHENTICATED,
                    "Refresh token expiré.");
        }
        // Un token révoqué mais présenté via /refresh (pas /logout) est
        // une réutilisation : rotate() appliquera la sanction.
        return token;
    }

    private Optional<RefreshToken> findByClearValue(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(sha256Hex(presentedToken.trim()));
    }

    /** SHA-256 en hex — la seule forme stockée en base. */
    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 est garanti par le JDK — jamais atteignable.
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
    }
}
