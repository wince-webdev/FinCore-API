package com.wince.fincore.account.api;

/**
 * Réponse du pré-contrôle bénéficiaire — {@code GET /accounts/lookup?number=}
 * (§8, F-02 / F-06) — F-03.
 *
 * <p>« n'expose que son nom masqué (J*** D***) » (F-06, RG-KYC-03,
 * CA-KYC-01) — n'expose aucun numéro de compte, aucune donnée personnelle.
 */
public record AccountLookupResponse(String maskedName) {

    /** Projet — initiale + « *** » de chaque nom — ex. « J*** D*** ». */
    public static AccountLookupResponse of(String firstName, String lastName) {
        return new AccountLookupResponse(maskName(firstName) + " " + maskName(lastName));
    }

    /**
     * Masquage d'un nom — initiale + « *** » — ex. {@code J***} —
     * NULL si nom vide — « initiale sans structure » — mémorable.
     */
    private static String maskName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed.charAt(0) + "***";
    }
}
