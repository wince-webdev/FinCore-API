package com.wince.fincore.payment.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(
        @NotNull(message = "le compte source est obligatoire") UUID sourceAccountId,
        String destinationAccountNumber,
        UUID destinationAccountId,
        @NotNull(message = "le montant est obligatoire") BigDecimal amount,
        @NotNull(message = "la devise est obligatoire") com.wince.fincore.shared.domain.Currency currency,
        String description) {
}
