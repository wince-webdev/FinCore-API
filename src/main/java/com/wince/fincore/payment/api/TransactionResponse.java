package com.wince.fincore.payment.api;

import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionStatus;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de réponse — {@code POST /api/v1/deposits}, {@code POST /api/v1/withdrawals} — F-05 — §8.
 *
 * <p>Miroir de {@code financial_transaction} (§6.1) — avec {@code completed_at}.
 * Équivalent DTO d'API — {@code Money} non — Le montant est un {@code BigDecimal} et la devise à côté.
 */
public record TransactionResponse(
        UUID id, String reference, TransactionType type, TransactionStatus status,
        BigDecimal amount, BigDecimal feeAmount, Currency currency,
        UUID sourceAccountId, UUID destinationAccountId, Instant createdAt, Instant completedAt) {

    /** Projet — from — {@link FinancialTransaction}. */
    public static TransactionResponse from(FinancialTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getReference(), transaction.getType(),
                transaction.getStatus(), transaction.getAmount(), transaction.getFeeAmount(),
                transaction.getCurrency(), transaction.getSourceAccountId(), transaction.getDestinationAccountId(),
                transaction.getCreatedAt(), transaction.getCompletedAt());
    }
}
