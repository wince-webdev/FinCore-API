package com.wince.fincore.payment.api;

import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;

public record FxQuoteResponse(
        BigDecimal amount,
        Currency sourceCurrency,
        BigDecimal convertedAmount,
        Currency destinationCurrency,
        BigDecimal exchangeRate,
        BigDecimal spreadPercent,
        BigDecimal fee,
        BigDecimal totalDebited) {
}
