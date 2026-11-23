package com.wince.fincore.customer.domain;

/**
 * Niveau KYC du client — table {@code customer.kyc_level} (cahier §6.1).
 *
 * <p>Enuméré fermé Java ({@code @Enumerated(EnumType.STRING)}) — ajouter un
 * niveau se fait en éditant l'enum <em>et</em> la migration SQL de façon
 * compatible (le CHECK d'une migration commitée ne se modifie pas).
 *
 * <p><strong>Relations avec la matrice de droits (§5)</strong> : le niveau
 * régit quels virements/retraits/avantages l'utilisateur peut avoir — voir
 * F-03 (Phase 4) « niveau KYC détermine les plafonds applicables » (RG-KYC-05).
 */
public enum KycLevel {

    /** Client réservé à l'inscription (§6.1). */
    LEVEL_1,

    /** Pièce d'identité fournie — validée par TELLER/ADMIN (RG-KYC-02). */
    LEVEL_2,

    /** Justificatif de domicile + source de revenus — ADMIN seul (RG-KYC-02). */
    LEVEL_3
}
