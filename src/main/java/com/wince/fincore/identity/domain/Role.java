package com.wince.fincore.identity.domain;

/**
 * Rôles de la matrice des droits (§5 du cahier des charges).
 *
 * <p>Le rôle est porté par le JWT (claim {@code role}) et arbitré dans
 * {@code SecurityConfig} via {@code hasRole(...)}. C'est aussi une
 * contrainte {@code CHECK} en base (V1) — double barrière Java + SQL.</p>
 *
 * <p>Équivalent Symfony : les <em>roles</em> d'un user Symfony
 * ({@code ROLE_ADMIN}…), sauf qu'ici le préfixe {@code ROLE_} est ajouté
 * par le convertisseur de authorities et n'est pas stocké dans le claim.</p>
 */
public enum Role {

    /** Client final — ne voit que ses propres données. */
    CUSTOMER,

    /** Agent de guichet — dépôts, retraits, ouverture de compte. */
    TELLER,

    /** Back-office — paramétrage, gel, contre-passation. */
    ADMIN,

    /** Lecture seule — audit, alertes, vérification d'intégrité. */
    AUDITOR
}
