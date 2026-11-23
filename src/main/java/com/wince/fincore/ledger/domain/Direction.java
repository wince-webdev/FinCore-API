package com.wince.fincore.ledger.domain;

/**
 * Direction d'une écriture — {@code ledger_entry.direction} (cahier §6.1).
 *
 * <p>Avec {@link #DEBIT} : solde du compte − montant — {@code balance_after}
 * plus petit que l'avant — {@link #CREDIT} : solde + montant.
 */
public enum Direction {

    /** Débit — la partie double sort de l'argent — {@code balance_after} − montant. */
    DEBIT,

    /** Crédit — l'argent entre — {@code balance_after} + montant. */
    CREDIT
}
