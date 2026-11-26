package com.wince.fincore.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires de {@link AccountNumberGenerator} — F-03 / RG-ACC-03.
 *
 * <p>{@code isValid()} est une « pure function » — directement rejouable —
 * la définition du mod 97 (97 − n mod 97) est ici vérifiée sur un exemple
 * généré.
 */
class AccountNumberGeneratorTest {

    @Test
    @DisplayName("RG-ACC-03 : generate() produit des numéros valides — BJ + 10 chiffres + clé mod 97")
    void generated_numbers_are_valid() {
        for (int i = 0; i < 100; i++) {
            String number = AccountNumberGenerator.generate();
            assertThat(number)
                    .hasSize(AccountNumberGenerator.TOTAL_LENGTH)
                    .startsWith(AccountNumberGenerator.PREFIX);
            assertThat(AccountNumberGenerator.isValid(number))
                    .as("le numéro généré doit valider").isTrue();
        }
    }

    @Test
    @DisplayName("RG-ACC-03 : 100 numéros aléatoires = 100 numéros distincts")
    void generated_numbers_are_distinct() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            numbers.add(AccountNumberGenerator.generate());
        }
        assertThat(numbers).hasSize(100);
    }

    @Test
    @DisplayName("CA-ACC-01 / RG-ACC-03 : clé erronée → isValid() = false")
    void invalid_key_is_rejected() {
        String number = AccountNumberGenerator.generate();
        // Corrumper la clé — dernier caractère — résultat toujours différent du valide.
        String corrupted = number.substring(0, number.length() - 1)
                + (number.charAt(number.length() - 1) == '0' ? '9' : '0');
        assertThat(AccountNumberGenerator.isValid(corrupted)).isFalse();
    }

    @Test
    @DisplayName("RG-ACC-03 : numéros mal formés → isValid() = false (préfixe, taille, clé 00)")
    void malformed_numbers_are_rejected() {
        assertThat(AccountNumberGenerator.isValid(null)).isFalse();
        assertThat(AccountNumberGenerator.isValid("")).isFalse();
        assertThat(AccountNumberGenerator.isValid("AH123456789000")).isFalse();  // mauvais préfixe
        assertThat(AccountNumberGenerator.isValid("BJ123456789")).isFalse();    // 13 car.
        assertThat(AccountNumberGenerator.isValid("BJ1234567890123")).isFalse(); // 15 car.

        String generated = AccountNumberGenerator.generate();
        // ClǸ "00" �?" impossible par construction (clǸ �^^ 1..97).
        assertThat(AccountNumberGenerator.isValid(generated.substring(0, 12) + "00")).isFalse();
        // TronquǸ.
        assertThat(AccountNumberGenerator.isValid(generated.substring(0, 12))).isFalse();
    }

    @Test
    @DisplayName("RG-ACC-03 : convention de la clé — 97 − (n mod 97) — sur un exemple généré")
    void check_key_convention_on_generated_example() {
        String number = AccountNumberGenerator.generate();
        String body = number.substring(AccountNumberGenerator.PREFIX.length(),
                AccountNumberGenerator.PREFIX.length() + AccountNumberGenerator.BODY_DIGITS);
        String key = number.substring(
                AccountNumberGenerator.PREFIX.length() + AccountNumberGenerator.BODY_DIGITS);
        // ICC classique : clé = 97 − (n mod 97) — si n ≡ 0 (97), clé = 97 — borne 1..97.
        int expected = 97 - new BigInteger(body).mod(BigInteger.valueOf(97)).intValue();
        assertThat(expected).isBetween(1, 97);
        assertThat(Integer.parseInt(key)).isEqualTo(expected);
    }
}
