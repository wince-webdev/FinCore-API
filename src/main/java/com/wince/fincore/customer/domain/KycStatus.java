package com.wince.fincore.customer.domain;

/**
 * Statut de la chaîne KYC du client — table {@code customer.kyc_status}
 * (cahier §6.1).
 *
 * <p>Statuts futurs : {@code REJECTED} sera utilisé par un endpoint de
 * révision (§7, RG-KYC-02) ; {@code VERIFIED} en associé. La phase 3
 * n'écrit que {@code PENDING} à la création — le client arrive « en attente »
 * de validation.
 */
public enum KycStatus {

    /** Dossier ouvert, pas encore validé. */
    PENDING,

    /** Validé par un TELLER/ADMIN. */
    VERIFIED,

    /** Rejeté — plus aucune action métier valide. */
    REJECTED
}
