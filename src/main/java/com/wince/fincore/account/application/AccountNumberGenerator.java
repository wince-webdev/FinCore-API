package com.wince.fincore.account.application;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Génération et validation des numéros de compte — F-03 / RG-ACC-03.
 *
 * <p>Format : « {@code BJ} » + 10 chiffres aléatoires + 2 chiffres de clé
 * de contrôle (cahier §6.1, RG-ACC-03). La clé est
 * {@code 97 − (n mod 97)} — convention ICC classique : quand
 * {@code n ≡ 0 (97)} la clé vaut 97 — d'où la borne 1..97 sur les clés
 * produites par {@link #generate()}.
 *
 * <p>« Validation de la clé à chaque saisie d'un numéro de bénéficiaire »
 * (RG-ACC-03) — fonction {@link #isValid(String)} rejouable
 * indépendamment — exécutée par {@code AccountService.lookupBeneficiary}
 * ({@code GET /accounts/lookup} — §8) pour le pré-contrôle bénéficiaire.
 *
 * <p>Équivalent PHP/Symfony : méthode de validation RIB statique.
 *
 * <p>À la différence de {@code AccountService}, cette classe est une
 * classe utilitaire « statique » : pas de dépendance Spring, testable
 * isolation — équivalent à une méthode privée référencée par plusieurs
 * appelants — page {@code pure functions}.
 */
public final class AccountNumberGenerator {

    /** Préfixe national — objet du nommage banking (§6.1, RG-ACC-03). */
    public static final String PREFIX = "BJ";

    /** Nombre de chiffres du numéro de compte — mod 97 calculé sur ceux-là (§6.1). */
    public static final int BODY_DIGITS = 10;

    /** Deux chiffres de clé de contrôle (§6.1, RG-ACC-03). */
    public static final int KEY_DIGITS = 2;

    /** Longueur totale — {@code BJ} + {@code BODY_DIGITS} + {@code KEY_DIGITS}. */
    public static final int TOTAL_LENGTH = PREFIX.length() + BODY_DIGITS + KEY_DIGITS; // 14

    private static final int MOD = 97;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumberGenerator() {
    }

    /**
     * Génère un numéro de compte valide — RG-ACC-03.
     *
     * <p>Le premier chiffre du « nombre » est forced non nul
     * (1..9) — un numéro {@code BJ0000000000…} serait un nombre RIB
     * suranné — « aléatoires » à partir du chiffre 2.
     */
    public static String generate() {
        String body = generateBody();
        return PREFIX + body + String.format("%0" + KEY_DIGITS + "d", checkKey(body));
    }

    /**
     * Valide le format + la clé — « pure function » (CA-ACC-01, RG-ACC-03).
     *
     * <ul>
     *   <li>préfixe {@code BJ}, longueur {@code TOTAL_LENGTH} ;</li>
     *   <li>corps = 10 chiffres, clé = 2 chiffres ;</li>
     *   <li>clé == 97 − (n mod 97) — le {@code UNIQUE} en base reste l'arbitre
     *       final de l'existence (RG-LED/§6.2).</li>
     * </ul>
     *
     * <p>À l'inverse du constructeur de {@code Money}, rien ne cherche à
     * <em>réparer</em> — un numéro inligible est rejeté vrai — « dur » sur
     * l'intégrité bancaire.
     */
    public static boolean isValid(String accountNumber) {
        if (accountNumber == null || !accountNumber.startsWith(PREFIX)) {
            return false;
        }
        if (accountNumber.length() != TOTAL_LENGTH) {
            return false;
        }
        String body = accountNumber.substring(PREFIX.length(), PREFIX.length() + BODY_DIGITS);
        String key = accountNumber.substring(PREFIX.length() + BODY_DIGITS, TOTAL_LENGTH);
        if (!body.matches("\\d{" + BODY_DIGITS + "}") || !key.matches("\\d{" + KEY_DIGITS + "}")) {
            return false;
        }
        return Integer.parseInt(key) == checkKey(body);
    }

    /** Clé de contrôle : {@code 97 − (n mod 97)} — toujours dans 1..97. */
    private static int checkKey(String body) {
        return MOD - new BigInteger(body).mod(BigInteger.valueOf(MOD)).intValue();
    }

    /** Le « nombre » : 10 chiffres aléatoires — premier chiffre non nul. */
    private static String generateBody() {
        StringBuilder body = new StringBuilder(BODY_DIGITS);
        for (int i = 0; i < BODY_DIGITS; i++) {
            int digit = (i == 0) ? 1 + RANDOM.nextInt(9) : RANDOM.nextInt(10);
            body.append(digit);
        }
        return body.toString();
    }
}
