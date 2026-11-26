package com.wince.fincore.payment.api;

import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;

public record QuoteResponse(
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalDebited,
        Currency currency,
        BigDecimal remainingLimit,
        BigDecimal remainingDailyLimit,
        BigDecimal sourceBalanceAfter) {
}
