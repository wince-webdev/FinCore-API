package com.wince.fincore.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Utilisateur de l'application — table {@code app_user} (V1).
 *
 * <p>Les colonnes de <strong>verrouillage</strong> ({@code failed_attempts},
 * {@code locked_until}) sont les seules modifiables en dehors de la création
 * (RG-AUTH-05) : le reste est immuable après inscription.</p>
 *
 * <p>Équivalent Symfony : l'entité {@code User} du bundle Security, avec les
 * champs {@code locked} / {@code passwordRequestedAt} de la native lock.</p>
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /** Nécessaire pour JPA — jamais utilisé directement (règle §2.2). */
    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.failedAttempts = 0;
        this.createdAt = Instant.now();
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Normalisé en minuscules avant persistance (unicité insensible à la casse). */
    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    /** BCrypt coût 12 — jamais en clair, jamais loggé (RG-AUTH-01). */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Incrémenté à chaque échec de mot de passe ; déclenche le verrou à 5. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /** NULL = non verrouillé ; sinon expiration du verrou (RG-AUTH-05). */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Accesseurs (lecture seule hors méthodes métier) ──────────────────

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Mutations de verrouillage (RG-AUTH-05) ──────────────────────────

    /**
     * Invalide un essai : incrémente le compteur et pose le verrou si le
     * seuil de 5 échecs est atteint (verrou de 15 minutes).
     *
     * @return {@code true} si le compte vient d'être verrouillé
     */
    public boolean registerFailedAttempt(int maxAttempts, Instant lockUntil) {
        this.failedAttempts++;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = lockUntil;
            return true;
        }
        return false;
    }

    /** Réinitialise le compteur après une connexion réussie (ou un verrou expiré). */
    public void clearFailedAttempts() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    /** Vrai si le compte est verrouillé à l'instant {@code now}. */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
