package com.wince.fincore.account.domain;

/**
 * Catégorie d'un compte système — table {@code account.system_code}
 * (cahier §6.1, RG-ACC-06).
 *
 * <p>Six comptes systèmes sont prévus, créés pour chaque devise (XOF,
 * EUR, USD) au démarrage : caisse ({@code CASH_VAULT}), produits de frais
 * ({@code FEE_REVENUE}), compensation de change ({@code FX_POSITION}),
 * règlement externe ({@code EXTERNAL_SETTLEMENT}), suspense
 * ({@code SUSPENSE}) et intérêts d'épargne ({@code INTEREST_EXPENSE}).
 *
 * <p>Énuméré stocké en {@code VARCHAR} — NULL pour les comptes clients
 * (§6.1 : « null pour comptes système » inversement).
 */
public enum SystemCode {

    /** Caisse — {@code CASH_VAULT} (cahier §6.1). */
    CASH_VAULT,

    /** Produits de frais — {@code FEE_REVENUE} (cahier §6.1). */
    FEE_REVENUE,

    /** Position de change — {@code FX_POSITION} (cahier §6.1). */
    FX_POSITION,

    /** Règlement externe — {@code EXTERNAL_SETTLEMENT} (cahier §6.1). */
    EXTERNAL_SETTLEMENT,

    /** Comptes suspense — {@code SUSPENSE} (cahier §6.1). */
    SUSPENSE,

    /** Intérêts d'épargne — {@code INTEREST_EXPENSE} (cahier §6.1). */
    INTEREST_EXPENSE
}
