package com.wince.fincore.shared.domain;

/**
 * Devises supportées par FinCore (RG-ACC-01).
 *
 * <p>Chaque devise porte son <strong>échelle</strong> : le nombre de décimales
 * autorisées. XOF n'a pas de sous-unité pratique (0 décimale), EUR et USD en
 * ont 2. Cette information pilote l'arrondi de {@link Money} — un montant
 * d'échelle trop élevée est une erreur métier ({@code INVALID_AMOUNT_SCALE}),
 * pas un arrondi silencieux.</p>
 */
public enum Currency {

    /** Franc CFA (BCEAO) — 0 décimale, monnaie par défaut du projet. */
    XOF(0),

    /** Euro — 2 décimales. */
    EUR(2),

    /** Dollar américain — 2 décimales. */
    USD(2);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    /**
     * Nombre de décimales autorisées pour cette devise (0, 2…).
     * Utilisé par {@link Money} pour arrondir en arrondi bancaire HALF_EVEN.
     */
    public int scale() {
        return scale;
    }
}
