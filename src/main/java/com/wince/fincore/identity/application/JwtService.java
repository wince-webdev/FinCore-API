package com.wince.fincore.identity.application;

import com.wince.fincore.identity.domain.AppUser;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.stereotype.Service;

/**
 * Émission des <strong>access tokens JWT</strong> (RG-AUTH-06) :
 * signature HS256, claims {@code sub}, {@code role}, {@code iat},
 * {@code exp}, {@code jti}.
 *
 * <p><strong>Pourquoi « access » token ?</strong> Le JWT est court
 * (15 min) : s'il est volé, la fenêtre d'exploitation est minime. La
 * session longue (7 jours) vit dans le <em>refresh token</em> opaque,
 * stocké haché côté serveur — on peut le révoquer instantanément.</p>
 *
 * <p><strong>Pourquoi HS256 et pas RS256 ?</strong> Une seule paire de
 * clés côté serveur suffit : il n'y a qu'un seul émetteur/vérificateur
 * (l'API). RS256 devient utile quand des services tiers doivent
 * <em>vérifier</em> sans posséder le secret de signature.</p>
 *
 * <p>Équivalent Symfony : le composant {@code lexik/jwt-bundle} avec
 * l'algorithme {@code hmac_sha256}.</p>
 */
@Service
public class JwtService {

    private static final String ISSUER = "fincore";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final Duration accessTokenTtl;

    /**
     * @param secret clé HS256 ≥ 256 bits, injectée par variable
     *        d'environnement {@code FINCORE_JWT_SECRET} (jamais en dur
     *        dans le code — règle §2.3 « secrets hors code »)
     * @param accessTokenTtlMinutes validité de l'access token (15 min,
     *        RG-AUTH-03)
     */
    public JwtService(
            @Value("${fincore.security.jwt-secret}") String secret,
            @Value("${fincore.security.access-ttl-minutes:15}") long accessTokenTtlMinutes,
            Clock clock) {
        // HS256 exige ≥ 256 bits (32 octets) — échec net au démarrage
        // plutôt qu'un token silencieusement faible.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "fincore.security.jwt-secret doit faire au moins 256 bits (32 octets).");
        }
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        // Le DÉCODEUR partage exactement la même clé : c'est la seule façon
        // d'être sûr que signer et vérifier ne divergent jamais (une clé
        // différente de l'autre côté = tous les jetons rejetés en 401).
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.clock = clock;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    /**
     * Décodeur exposé comme bean {@link JwtDecoder} : Spring Security
     * (resource server) s'en sert pour valider les en-têtes
     * {@code Authorization: Bearer …}.
     */
    public JwtDecoder jwtDecoder() {
        return jwtDecoder;
    }

    /**
     * Émet un access token pour l'utilisateur donné (RG-AUTH-06).
     *
     * <ul>
     *   <li>{@code sub} — identifiant UUID de l'utilisateur (jamais l'email :
     *       un email change, l'id non) ;</li>
     *   <li>{@code role} — le rôle porté pour l'arbitrage des droits ;</li>
     *   <li>{@code iat}/{@code exp} — fenêtre de 15 minutes ;</li>
     *   <li>{@code jti} — identifiant unique du jeton (traçabilité).</li>
     * </ul>
     */
    public String issueAccessToken(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .claim("role", user.getRole().name())
                .id(UUID.randomUUID().toString()) // jti
                .build();
        return jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Durée de validité de l'access token (renvoyée au client en `expiresIn`). */
    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }
}
