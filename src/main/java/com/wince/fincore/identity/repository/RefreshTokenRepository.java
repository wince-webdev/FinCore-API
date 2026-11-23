package com.wince.fincore.identity.repository;

import com.wince.fincore.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès aux refresh tokens. Deux chemins critiques :
 * <ul>
 *   <li>{@code findByTokenHash} — le refresh (lookup par index UNIQUE) ;</li>
 *   <li>{@code revokeAllForUser} — la <strong>révocation de famille</strong>
 *       déclenchée par une réutilisation détectée (RG-AUTH-04).</li>
 * </ul>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Révoque tous les tokens encore actifs d'un utilisateur en une seule
     * requête UPDATE — c'est la sanction de la réutilisation d'un token
     * déjà tourné (RG-AUTH-04 / CA-AUTH-03).
     *
     * @return nombre de tokens révoqués
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :at
             WHERE t.user.id = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("at") Instant at);
}
