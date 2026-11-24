package com.wince.fincore.ledger.domain;

/**
 * Type d'opération — table {@code financial_transaction.type} (cahier §6.1, §7 F-04).
 *
 * <p>Énuméré stocké en {@code VARCHAR} — {@code EnumType.STRING} — portable.
 * La liste exhaustive appartient au {@code CHECK} de V5 — ajouter un type
 * = nouveau CHECK + nouveau déploiement — équivalent de {@code TransactionType}.
 */
public enum TransactionType {

    /** Dépôt — F-05 — RG-DEP-01. */
    DEPOSIT,

    /** Retrait — F-05 — RG-DEP-02. */
    WITHDRAWAL,

    /** Virement interne — F-06 (Phase 6). */
    TRANSFER,

    /** Virement avec change — F-07 (Phase 7). */
    FX_TRANSFER,

    /** Virement externe — F-08 (Phase 8). */
    EXTERNAL_TRANSFER,

    /** Frais — Phase 6 (contre-passation de virements — F-06). */
    FEE,

    /** Intérêts d'épargne — Phase 11 (F-14). */
    INTEREST,

    /** Contre-passation — F-09 (Phase 6). */
    REVERSAL
}
