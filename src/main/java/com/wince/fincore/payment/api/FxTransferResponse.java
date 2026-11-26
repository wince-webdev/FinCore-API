package com.wince.fincore.payment.api;

import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FxTransferResponse(
        UUID transactionId,
        String reference,
        BigDecimal amount,
        Currency sourceCurrency,
        BigDecimal convertedAmount,
        Currency destinationCurrency,
        BigDecimal exchangeRate,
        BigDecimal spreadPercent,
        BigDecimal fee,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter,
        Instant createdAt) {
}
