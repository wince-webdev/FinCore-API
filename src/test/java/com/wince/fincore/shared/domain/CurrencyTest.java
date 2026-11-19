package com.wince.fincore.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests de l'enum {@link Currency} : échelles autorisées par devise. */
class CurrencyTest {

    @Test
    @DisplayName("XOF n'a pas de sous-unité : 0 décimale (RG-ACC-01)")
    void xof_has_zero_scale() {
        assertThat(Currency.XOF.scale()).isZero();
    }

    @Test
    @DisplayName("EUR et USD ont 2 décimales")
    void eur_and_usd_have_two_decimals() {
        assertThat(Currency.EUR.scale()).isEqualTo(2);
        assertThat(Currency.USD.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Les 3 devises du projet sont présentes (XOF, EUR, USD)")
    void the_three_project_currencies_exist() {
        assertThat(Currency.values())
                .extracting(Enum::name)
                .containsExactly("XOF", "EUR", "USD");
    }
}
