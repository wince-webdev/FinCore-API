package com.wince.fincore.ledger.domain;

/**
 * Statut d'une transaction du grand livre — {@code financial_transaction.status}
 * (cahier §6.1).
 *
 * <p>{@code PENDING} — réservé aux opérations asynchrones (virements externes —
 * F-08, Phase 8 — réconciliation — F-13, Phase 9). À la Phase 5, dépôts/retraits
 * passent directement en {@code COMPLETED}.
 */
public enum TransactionStatus {

    /** En attente d'exécution — virements externes (F-08 — Phase 8). */
    PENDING,

    /** Exécutée — écritures en partie double POSTÉES — dépôts/retraits (F-05). */
    COMPLETED,

    /** Échouée — rejets métier — F-09 — contre-passation possible. */
    FAILED,

    /** Contre-passée — F-09 — RG-LED-03 — écritures inversées enregistrées. */
    REVERSED
}
