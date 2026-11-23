package com.wince.fincore.account.domain;

/**
 * Statut d'un compte — table {@code account.status} (cahier §6.1).
 *
 * <ul>
 *   <li>{@code ACTIVE} : opérations client autorisées ;</li>
 *   <li>{@code FROZEN} : aucun débit ni crédit client autorisé (RG-ACC-04) ;</li>
 *   <li>{@code CLOSED} : statut définitif — plus aucune transition sortante
 *       (RG-ACC-04) — {@code closed_at} positionné (§6.1).</li>
 * </ul>
 *
 * <p>L'échelle des problèmes : {@code ACTIVE → FROZEN → ACTIVE}
 * (gel/dégel) et {@code ACTIVE|FROZEN → CLOSED} — {@code ILLEGAL_STATE_TRANSITION}
 * sur toute autre transition (RG-ACC-04).
 */
public enum AccountStatus {

    /** Actif — opérations autorisées. */
    ACTIVE,

    /** Gelé — aucun débit ni crédit client autorisé (RG-ACC-04). */
    FROZEN,

    /** Clôturé — statut définitif (RG-ACC-04). */
    CLOSED
}
