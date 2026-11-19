package com.wince.fincore.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Objet valeur monétaire : un montant {@code BigDecimal} inséparable de sa
 * devise. C'est <strong>le</strong> type d'argent du projet — jamais un
 * {@code double}, jamais un {@code float} (§2.2 du cahier des charges).
 *
 * <p><strong>Pourquoi pas un simple {@code BigDecimal} ?</strong> Parce qu'en
 * Java rien n'empêche d'additionner 100 XOF et 50 EUR. Avec {@code Money},
 * le compilateur et les tests l'empêchent : l'addition de devises différentes
 * lève une exception <em>avant</em> toute écriture en base.</p>
 *
 * <p><strong>Invariants garantis par le constructeur :</strong></p>
 * <ul>
 *   <li>montant non nul, devise non nulle ;</li>
 *   <li>échelle exactement égale à l'échelle de la devise (XOF → 0 décimale,
 *       EUR/USD → 2), arrondi {@link RoundingMode#HALF_EVEN} (arrondi
 *       bancaire : 2,50 → 2 ; 3,50 → 4 — on arrondit vers le nombre pair,
 *       ce qui évite une dérive systématique sur des millions de calculs) ;</li>
 *   <li>égalité par valeur : {@code Money(100, XOF)} equals
 *       {@code Money(100.00…, XOF)} car l'échelle est normalisée.</li>
 * </ul>
 *
 * <p>Équivalent Symfony : un value object {@code Money} de la bibliothèque
 * moneyphp/money — même intention, moins de fonctionnalités.</p>
 *
 * @param amount montant arrondi à l'échelle de la devise
 * @param currency devise du montant
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "Le montant ne peut pas être null");
        Objects.requireNonNull(currency, "La devise ne peut pas être null");
        // Normalisation : le domaine ne stocke jamais une échelle « sale ».
        amount = amount.setScale(currency.scale(), RoundingMode.HALF_EVEN);
    }

    /** Crée un montant depuis un {@code BigDecimal} (arrondi automatique). */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /** Crée un montant depuis une chaîne (ex. {@code "1250.50"}). */
    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    /** Zéro dans la devise donnée — utile pour les cumuls et soldes initiaux. */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Construit un montant à partir d'une saisie utilisateur, en refusant une
     * échelle supérieure à celle de la devise (RG-DEP-03 →
     * {@code INVALID_AMOUNT_SCALE}).
     *
     * <p>À la différence du constructeur, qui arrondit silencieusement pour
     * les calculs internes, ce point d'entrée <strong>rejette</strong> :
     * saisir « 100.50 XOF » (2 décimales sur une devise à 0) doit renvoyer
     * 400 à l'API, pas être arrondi en 101 sans prévenir.</p>
     *
     * @throws FinCoreException {@code INVALID_AMOUNT_SCALE} si l'échelle dépasse
     * @throws FinCoreException {@code VALIDATION_FAILED} si la chaîne n'est
     *         pas un nombre valide
     */
    public static Money parse(String amount, Currency currency) {
        BigDecimal value;
        try {
            value = new BigDecimal(amount.trim());
        } catch (NumberFormatException | NullPointerException ex) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
                    "Le montant '" + amount + "' n'est pas un nombre valide.");
        }
        if (value.scale() > currency.scale()) {
            throw new FinCoreException(ErrorCode.INVALID_AMOUNT_SCALE,
                    "La devise " + currency + " autorise au plus "
                            + currency.scale() + " décimale(s), montant saisi : " + amount + ".");
        }
        return new Money(value, currency);
    }

    /** Somme — les deux montants doivent être dans la même devise. */
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /** Différence — les deux montants doivent être dans la même devise. */
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /** Opposé arithmétique (utile pour les écritures inverses de contre-passation). */
    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    /**
     * Multiplication par un facteur (taux de frais, taux de change), arrondie
     * en HALF_EVEN à l'échelle de la devise.
     */
    public Money multipliedBy(BigDecimal factor) {
        Objects.requireNonNull(factor, "Le facteur ne peut pas être null");
        return new Money(amount.multiply(factor), currency);
    }

    /** Vrai si le montant vaut strictement zéro. */
    public boolean isZero() {
        return amount.signum() == 0;
    }

    /** Vrai si le montant est strictement positif (règle : montant &gt; 0). */
    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /** Vrai si le montant est strictement négatif. */
    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /** Comparaison signée, même devise exigée. */
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return compareTo(other) >= 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isLessThanOrEqual(Money other) {
        return compareTo(other) <= 0;
    }

    /**
     * Ajoute une contrainte de devise. Levée en {@link IllegalArgumentException}
     * (et non {@link FinCoreException}) car c'est une <strong>erreur de
     * programme</strong>, pas une règle métier : aucun scénario utilisateur
     * légitime ne doit la déclencher — les tests l'attrapent.
     */
    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "Le montant à comparer ne peut pas être null");
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "Opération monétaire interdite entre " + currency + " et " + other.currency);
        }
    }
}
