package com.wince.fincore.account.domain;

/**
 * Type de compte — table {@code account.kind} (cahier §6.1).
 *
 * <p>{@code CURRENT} : courant (conventionnel), {@code SAVINGS} : épargne
 * (S)OBLATION, {@code WALLET} : portefeuille mobile money. {@code SYSTEM}
 * : comptes systèmes — créés au démarrage (RG-ACC-06) — jamais ouvert par
 * l'API.
 *
 * <p>Énuméré stocké en {@code VARCHAR} (stratégie {@code EnumType.STRING}) —
 * portable et lisible dans la base.
 */
public enum AccountKind {

    /** Courant — {@code CURRENT} (cahier §6.1). */
    CURRENT,

    /** Épargne (sorte d'épargne bancaire) — {@code SAVINGS} (cahier §6.1). */
    SAVINGS,

    /** Portefeuille mobile money — {@code WALLET} (cahier §6.1). */
    WALLET,

    /** Compte système — caisse, frais, compensation… — {@code SYSTEM} (cahier §6.1). */
    SYSTEM
}
