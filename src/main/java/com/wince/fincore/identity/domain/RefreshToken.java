package com.wince.fincore.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token opaque — table {@code refresh_token} (V2).
 *
 * <p><strong>Le token lui-même n'est jamais stocké</strong> : seule sa
 * empreinte SHA-256 (64 hex) est conservée (RG-AUTH-03). Côté API, le
 * client reçoit la valeur en clair ; en base, une fuite de dump reste
 * inutilisable.</p>
 *
 * <p><strong>Rotation (RG-AUTH-04)</strong> : à chaque utilisation, le
 * token est marqué {@code revokedAt} et relié à son successeur via
 * {@code replacedBy}. Si un token déjà réutilisé est présenté, c'est une
 * fuite : toute la famille (tous les tokens actifs de l'utilisateur) est
 * révoquée.</p>
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    /** Nécessaire pour JPA. */
    protected RefreshToken() {
    }

    public RefreshToken(AppUser user, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** SHA-256 hex du token opaque — jamais le token en clair. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** NULL = actif ; sinon le moment de la rotation/révocation. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** ID du token émis en remplacement (chaîne de rotation). */
    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Accesseurs ───────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────

    /** Marque le token comme utilisé (rotation) en le reliant à son successeur. */
    public void revokeAsReplacedBy(UUID successorId, Instant at) {
        this.revokedAt = at;
        this.replacedBy = successorId;
    }

    /** Révocation simple (logout, révocation de famille). */
    public void revoke(Instant at) {
        this.revokedAt = at;
    }

    /** Vrai si le token est expiré à l'instant {@code now}. */
    public boolean isExpiredAt(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** Vrai si le token a déjà été utilisé ou révoqué. */
    public boolean isRevoked() {
        return revokedAt != null;
    }
}
