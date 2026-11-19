package com.wince.fincore.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests du value object {@link Money} — invariants d'argent :
 * arrondi bancaire, échelle de devise, interdiction du cross-currency.
 */
class MoneyTest {

    // ── Construction & normalisation ─────────────────────────────────────

    @Test
    @DisplayName("XOF est arrondi à 0 décimale (HALF_EVEN : 1250.50 → 1250, 1251.50 → 1252)")
    void xof_is_rounded_to_zero_scale() {
        // Arrondi bancaire : on va vers le nombre PAIR. 1250 est pair → 1250.50 descend.
        assertThat(Money.of(new BigDecimal("1250.50"), Currency.XOF).amount())
                .isEqualByComparingTo("1250");
        // 1251 est impair → 1251.50 monte à 1252 (pair).
        assertThat(Money.of(new BigDecimal("1251.50"), Currency.XOF).amount())
                .isEqualByComparingTo("1252");
        assertThat(Money.of(new BigDecimal("1250.50"), Currency.XOF).amount().scale()).isZero();
    }

    @Test
    @DisplayName("EUR conserve 2 décimales")
    void eur_keeps_two_decimals() {
        Money m = Money.of(new BigDecimal("10"), Currency.EUR);
        assertThat(m.amount().scale()).isEqualTo(2);
        assertThat(m.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Arrondi bancaire HALF_EVEN : 2.5 → 2, 3.5 → 4")
    void half_even_rounding() {
        assertThat(Money.of("2.5", Currency.XOF).amount()).isEqualByComparingTo("2");
        assertThat(Money.of("3.5", Currency.XOF).amount()).isEqualByComparingTo("4");
        assertThat(Money.of("0.125", Currency.EUR).amount()).isEqualByComparingTo("0.12");
        assertThat(Money.of("0.135", Currency.EUR).amount()).isEqualByComparingTo("0.14");
    }

    @Test
    @DisplayName("Null refusé (montant ou devise)")
    void nulls_are_rejected() {
        assertThatThrownBy(() -> new Money(null, Currency.XOF))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Égalité par valeur après normalisation")
    void equality_after_normalization() {
        Money a = Money.of("100", Currency.XOF);
        Money b = Money.of("100.000", Currency.XOF);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ── Arithmétique ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Arithmétique")
    class Arithmetic {

        @Test
        void plus_and_minus() {
            Money a = Money.of("1000", Currency.XOF);
            Money b = Money.of("250", Currency.XOF);
            assertThat(a.plus(b).amount()).isEqualByComparingTo("1250");
            assertThat(a.minus(b).amount()).isEqualByComparingTo("750");
        }

        @Test
        void negate_and_zero() {
            Money m = Money.of("500", Currency.XOF);
            assertThat(m.negate().amount()).isEqualByComparingTo("-500");
            assertThat(Money.zero(Currency.XOF).isZero()).isTrue();
        }

        @Test
        void multipliedBy_rounds_half_even() {
            Money fee = Money.of("100", Currency.XOF).multipliedBy(new BigDecimal("0.35"));
            assertThat(fee.amount()).isEqualByComparingTo("35");
            // 0.05 × 2.5 = 0.125 → HALF_EVEN : le 2 est pair → 0.12 (pas 0.13)
            Money half = Money.of("0.05", Currency.EUR).multipliedBy(new BigDecimal("2.5"));
            assertThat(half.amount()).isEqualByComparingTo("0.12");
        }
    }

    // ── Signes ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Signes : isZero / isPositive / isNegative")
    void signs() {
        assertThat(Money.zero(Currency.XOF).isZero()).isTrue();
        assertThat(Money.of("1", Currency.XOF).isPositive()).isTrue();
        assertThat(Money.of("-1", Currency.XOF).isNegative()).isTrue();
        assertThat(Money.of("0", Currency.XOF).isPositive()).isFalse();
        assertThat(Money.of("0", Currency.XOF).isNegative()).isFalse();
    }

    @Test
    @DisplayName("Comparaisons : >, >=, <, <=")
    void comparisons() {
        Money small = Money.of("100", Currency.XOF);
        Money big = Money.of("200", Currency.XOF);
        assertThat(big.isGreaterThan(small)).isTrue();
        assertThat(big.isGreaterThanOrEqual(small)).isTrue();
        assertThat(small.isLessThan(big)).isTrue();
        assertThat(small.isLessThanOrEqual(big)).isTrue();
        assertThat(small.isLessThanOrEqual(Money.of("100", Currency.XOF))).isTrue();
        assertThat(small.compareTo(big)).isNegative();
    }

    // ── Cross-currency (RG-ACC-01) ───────────────────────────────────────

    @Nested
    @DisplayName("Interdiction des opérations inter-devises")
    class CrossCurrency {

        @Test
        @DisplayName("plus() entre XOF et EUR lève IllegalArgumentException")
        void plus_across_currencies_is_forbidden() {
            Money xof = Money.of("100", Currency.XOF);
            Money eur = Money.of("100", Currency.EUR);
            assertThatThrownBy(() -> xof.plus(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("XOF")
                    .hasMessageContaining("EUR");
        }

        @Test
        @DisplayName("minus() / compareTo() entre devises différentes refusés")
        void minus_and_compare_across_currencies_are_forbidden() {
            Money xof = Money.of("100", Currency.XOF);
            Money usd = Money.of("100", Currency.USD);
            assertThatThrownBy(() -> xof.minus(usd)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> xof.compareTo(usd)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── parse() : contrôle d'échelle (RG-DEP-03) ─────────────────────────

    @Nested
    @DisplayName("parse() — saisie utilisateur")
    class Parse {

        @Test
        @DisplayName("2 décimales en XOF → INVALID_AMOUNT_SCALE (400)")
        void too_many_decimals_in_xof_is_rejected() {
            assertThatThrownBy(() -> Money.parse("100.50", Currency.XOF))
                    .isInstanceOf(FinCoreException.class)
                    .satisfies(ex -> {
                        FinCoreException fce = (FinCoreException) ex;
                        assertThat(fce.errorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT_SCALE);
                    });
        }

        @Test
        @DisplayName("Échelle exacte acceptée")
        void exact_scale_is_accepted() {
            assertThat(Money.parse("100", Currency.XOF).amount()).isEqualByComparingTo("100");
            assertThat(Money.parse("100.50", Currency.EUR).amount()).isEqualByComparingTo("100.50");
        }

        @Test
        @DisplayName("Chaîne non numérique → VALIDATION_FAILED")
        void non_numeric_is_rejected() {
            assertThatThrownBy(() -> Money.parse("abc", Currency.XOF))
                    .isInstanceOf(FinCoreException.class)
                    .satisfies(ex ->
                            assertThat(((FinCoreException) ex).errorCode())
                                    .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }
}
