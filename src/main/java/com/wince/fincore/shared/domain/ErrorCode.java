package com.wince.fincore.shared.domain;

/**
 * Codes d'erreur métier de FinCore — table exhaustive du §9 du cahier des
 * charges. Un seul endroit pour : le code JSON, le statut HTTP, le titre
 * français et l'URI de type RFC 9457.
 *
 * <p><strong>Pourquoi une enum et pas des constantes ?</strong> Parce qu'une
 * enum est fermée : ajouter un code sans le documenter ici est impossible, et
 * le {@code switch} sur une enum force le compilateur à traiter tous les cas
 * (utile pour la table des statuts HTTP).</p>
 *
 * <p>Équivalent Symfony : un ensemble de constantes dans une classe
 * {@code ApiError} + un mapping vers des codes HTTP.</p>
 */
public enum ErrorCode {

    // ── 400 ──────────────────────────────────────────────────────────────
    VALIDATION_FAILED(400, "Données invalides", "validation-failed"),
    IDEMPOTENCY_KEY_REQUIRED(400, "Clé d'idempotence manquante", "idempotency-key-required"),
    INVALID_AMOUNT_SCALE(400, "Échelle du montant invalide", "invalid-amount-scale"),
    INVALID_ACCOUNT_NUMBER(400, "Numéro de compte invalide", "invalid-account-number"),

    // ── 401 / 403 / 404 ─────────────────────────────────────────────────
    UNAUTHENTICATED(401, "Authentification requise", "unauthenticated"),
    ACCESS_DENIED(403, "Accès refusé", "access-denied"),
    RESOURCE_NOT_FOUND(404, "Ressource introuvable", "resource-not-found"),

    // ── 409 ──────────────────────────────────────────────────────────────
    REQUEST_IN_PROGRESS(409, "Requête en cours de traitement", "request-in-progress"),
    ILLEGAL_STATE_TRANSITION(409, "Transition d'état illégale", "illegal-state-transition"),
    ACCOUNT_NOT_ACTIVE(409, "Compte non actif", "account-not-active"),

    // ── 422 ──────────────────────────────────────────────────────────────
    INSUFFICIENT_FUNDS(422, "Solde insuffisant", "insufficient-funds"),
    LIMIT_EXCEEDED(422, "Plafond dépassé", "limit-exceeded"),
    IDEMPOTENCY_KEY_REUSED(422, "Clé d'idempotence réutilisée", "idempotency-key-reused"),

    // ── 423 ──────────────────────────────────────────────────────────────
    ACCOUNT_LOCKED(423, "Compte verrouillé", "account-locked"),

    // ── 500 ──────────────────────────────────────────────────────────────
    /** Anomalie interne : le grand livre est déséquilibré — alerte critique. */
    LEDGER_UNBALANCED(500, "Grand livre déséquilibré", "ledger-unbalanced"),
    /** Erreur interne non qualifiée — jamais de stack trace côté client. */
    INTERNAL_ERROR(500, "Erreur interne", "internal-error");

    /** Base des URI de type RFC 9457 (section « type » du corps d'erreur). */
    public static final String TYPE_BASE = "https://fincore.example/errors/";

    private final int httpStatus;
    private final String title;
    private final String slug;

    ErrorCode(int httpStatus, String title, String slug) {
        this.httpStatus = httpStatus;
        this.title = title;
        this.slug = slug;
    }

    /** Statut HTTP associé à ce code (400, 422, 423, 500…). */
    public int httpStatus() {
        return httpStatus;
    }

    /** Titre court en français, affichable tel quel (RFC 9457 « title »). */
    public String title() {
        return title;
    }

    /** URI de type lisible par un humain : {@code …/errors/insufficient-funds}. */
    public String type() {
        return TYPE_BASE + slug;
    }
}
