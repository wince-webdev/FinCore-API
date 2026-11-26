package com.wince.fincore.payment.api;

import com.wince.fincore.ledger.domain.TransactionStatus;
import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID transactionId,
        String reference,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal feeAmount,
        BigDecimal totalDebited,
        Currency currency,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter,
        Instant createdAt) {

    public static TransferResponse from(com.wince.fincore.ledger.domain.FinancialTransaction transaction,
                                       BigDecimal sourceBalanceAfter, BigDecimal destinationBalanceAfter,
                                       BigDecimal fee, BigDecimal totalDebited) {
        return new TransferResponse(
                transaction.getId(), transaction.getId(), transaction.getReference(), transaction.getStatus(),
                transaction.getAmount(), fee, fee, totalDebited, transaction.getCurrency(),
                sourceBalanceAfter, destinationBalanceAfter, transaction.getCreatedAt());
    }
}
