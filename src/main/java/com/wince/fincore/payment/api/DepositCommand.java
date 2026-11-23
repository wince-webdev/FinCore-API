package com.wince.fincore.payment.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/deposits} — F-05 — §8 — RG-DEP-01 à 05.
 *
 * <p>{@code amount} — montant strictement positif — échelle de la devise
 * {@code Account} — {@code Money.parse} — 400 {@code INVALID_AMOUNT_SCALE} — RG-DEP-03.
 */
public record DepositCommand(
        @NotNull(message = "le compte est obligatoire") UUID accountId,
        @NotNull(message = "le montant est obligatoire") BigDecimal amount,
        String description) {
}
