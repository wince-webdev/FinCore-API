package com.wince.fincore.admin.api;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.ledger.repository.LedgerEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
public class AdminReportController {
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FinancialTransactionRepository transactionRepository;

    public AdminReportController(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
                                FinancialTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/reconciliation")
    public ResponseEntity<List<Map<String, Object>>> reconcile() {
        List<Map<String, Object>> discrepancies = new ArrayList<>();
        for (Account acc : accountRepository.findAll()) {
            BigDecimal net = ledgerEntryRepository.sumNetBefore(acc.getId(), Instant.now().plusSeconds(86400 * 365 * 10));
            // sumNetBefore sums entries before future => all entries. But if no entries, 0.
            if (net.compareTo(acc.getBalance()) != 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("accountId", acc.getId());
                m.put("expected", net);
                m.put("actual", acc.getBalance());
                discrepancies.add(m);
            }
        }
        return ResponseEntity.ok(discrepancies);
    }

    @GetMapping("/reports/trial-balance")
    public ResponseEntity<Map<String, Object>> trialBalance(@RequestParam(required = false) String date) {
        Map<String, BigDecimal> totalsByCurrency = new LinkedHashMap<>();
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (Account acc : accountRepository.findAll()) {
            accounts.add(Map.of("accountId", acc.getId(), "accountNumber", acc.getAccountNumber(),
                    "currency", acc.getCurrency(), "balance", acc.getBalance()));
            totalsByCurrency.merge(acc.getCurrency().name(), acc.getBalance(), BigDecimal::add);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", accounts);
        result.put("totalsByCurrency", totalsByCurrency);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/reports/daily-summary")
    public ResponseEntity<Map<String, Object>> dailySummary() {
        Instant from = LocalDate.now(java.time.ZoneId.of("Africa/Porto-Novo")).atStartOfDay(java.time.ZoneId.of("Africa/Porto-Novo")).toInstant();
        List<FinancialTransaction> today = transactionRepository.findAll().stream()
                .filter(t -> t.getCreatedAt().isAfter(from)).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> volumes = new LinkedHashMap<>();
        BigDecimal fees = BigDecimal.ZERO;
        for (FinancialTransaction t : today) {
            counts.merge(t.getType().name(), 1L, Long::sum);
            volumes.merge(t.getType().name(), t.getAmount(), BigDecimal::add);
            fees = fees.add(t.getFeeAmount());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("countsByType", counts);
        result.put("volumesByType", volumes);
        result.put("feesCollected", fees);
        result.put("totalTransactions", today.size());
        return ResponseEntity.ok(result);
    }
}
