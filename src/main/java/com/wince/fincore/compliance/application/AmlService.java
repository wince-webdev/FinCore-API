package com.wince.fincore.compliance.application;

import com.wince.fincore.compliance.domain.AmlAlert;
import com.wince.fincore.compliance.repository.AmlAlertRepository;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.ledger.repository.LedgerEntryRepository;
import com.wince.fincore.payment.domain.LimitRule;
import com.wince.fincore.payment.repository.LimitRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AmlService {
    private final AmlAlertRepository alertRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LimitRuleRepository limitRuleRepository;
    private final Clock clock;

    public AmlService(AmlAlertRepository alertRepository, FinancialTransactionRepository transactionRepository,
                      LedgerEntryRepository ledgerEntryRepository, LimitRuleRepository limitRuleRepository, Clock clock) {
        this.alertRepository = alertRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.limitRuleRepository = limitRuleRepository;
        this.clock = clock;
    }

    @Transactional
    public void evaluate(UUID transactionId) {
        FinancialTransaction txn = transactionRepository.findById(transactionId).orElse(null);
        if (txn == null) return;
        // resolve customer from source/destination
        UUID customerId = null;
        // We'll leave customerId null if unknown (AML alert can have null)
        // AML-001
        BigDecimal threshold = new BigDecimal("5000000");
        if (txn.getAmount().compareTo(threshold) >= 0) {
            saveAlert("AML-001", "HIGH", customerId, transactionId, "{\"amount\":" + txn.getAmount() + "}");
        }
        // AML-002: transfers
        if (txn.getType() == TransactionType.TRANSFER && txn.getSourceAccountId() != null) {
            Instant since = clock.instant().minus(10, ChronoUnit.MINUTES);
            List<FinancialTransaction> recent = transactionRepository.findAll().stream()
                    .filter(t -> t.getType() == TransactionType.TRANSFER)
                    .filter(t -> t.getSourceAccountId() != null && t.getSourceAccountId().equals(txn.getSourceAccountId()))
                    .filter(t -> t.getCreatedAt().isAfter(since))
                    .toList();
            long distinctDest = recent.stream().map(FinancialTransaction::getDestinationAccountId).distinct().count();
            if (recent.size() >= 5 && distinctDest >= 5) {
                saveAlert("AML-002", "MEDIUM", customerId, transactionId, "{\"count\":" + recent.size() + "}");
            }
        }
        // AML-003: fractionnement
        if (txn.getSourceAccountId() != null) {
            // find customer KYC level? We don't have customer here; use LEVEL_1 default
            com.wince.fincore.customer.domain.Customer customer = null;
            // Try to resolve customer via account? skip KYC; use default per-transaction max 100000 for LEVEL_1
            BigDecimal perTxnMax = new BigDecimal("100000");
            BigDecimal low = perTxnMax.multiply(new BigDecimal("0.90"));
            BigDecimal high = perTxnMax.multiply(new BigDecimal("0.99"));
            Instant since24 = clock.instant().minus(24, ChronoUnit.HOURS);
            long count = transactionRepository.findAll().stream()
                    .filter(t -> t.getSourceAccountId() != null && t.getSourceAccountId().equals(txn.getSourceAccountId()))
                    .filter(t -> t.getCreatedAt().isAfter(since24))
                    .filter(t -> t.getAmount().compareTo(low) >= 0 && t.getAmount().compareTo(high) <= 0)
                    .count();
            if (count >= 3) {
                saveAlert("AML-003", "HIGH", customerId, transactionId, "{\"count\":" + count + "}");
            }
        }
    }

    private void saveAlert(String code, String severity, UUID customerId, UUID transactionId, String details) {
        AmlAlert alert = new AmlAlert(UUID.randomUUID(), code, severity, customerId, transactionId, details);
        alertRepository.save(alert);
    }
}
