package com.wince.fincore.statement.application;

import com.wince.fincore.account.application.AccountService;
import com.wince.fincore.account.domain.Account;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.LedgerEntry;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.ledger.repository.LedgerEntryRepository;
import com.wince.fincore.shared.domain.Currency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StatementService {
    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FinancialTransactionRepository transactionRepository;

    public StatementService(AccountService accountService, LedgerEntryRepository ledgerEntryRepository,
                           FinancialTransactionRepository transactionRepository) {
        this.accountService = accountService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
    }

    public record StatementLine(Instant date, String reference, String description,
                               BigDecimal debit, BigDecimal credit, BigDecimal balanceAfter) {}

    public record Statement(UUID accountId, String accountNumber, Currency currency, Instant from, Instant to,
                           BigDecimal openingBalance, List<StatementLine> movements, BigDecimal closingBalance) {}

    public Statement statement(UUID accountId, Role role, UUID currentCustomerId,
                              Instant from, Instant to, int page, int size) {
        Account account = accountService.findById(accountId, role, currentCustomerId);
        Instant f = from == null ? Instant.EPOCH : from;
        Instant t = to == null ? Instant.now() : to;
        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
                accountId, f, t, pageable);
        BigDecimal opening = ledgerEntryRepository.sumNetBefore(accountId, f);
        Map<UUID, FinancialTransaction> txns = transactionRepository.findAllById(
                entries.getContent().stream().map(LedgerEntry::getTransactionId).distinct().toList())
                .stream().collect(Collectors.toMap(FinancialTransaction::getId, x -> x));
        List<StatementLine> lines = entries.getContent().stream().map(e -> {
            FinancialTransaction txn = txns.get(e.getTransactionId());
            String ref = txn == null ? "" : txn.getReference();
            String desc = txn == null ? "" : (txn.getDescription() == null ? "" : txn.getDescription());
            BigDecimal debit = e.getDirection().name().equals("DEBIT") ? e.getAmount() : BigDecimal.ZERO;
            BigDecimal credit = e.getDirection().name().equals("CREDIT") ? e.getAmount() : BigDecimal.ZERO;
            return new StatementLine(e.getCreatedAt(), ref, desc, debit, credit, e.getBalanceAfter());
        }).toList();
        BigDecimal closing = lines.isEmpty() ? opening : lines.get(lines.size() - 1).balanceAfter();
        return new Statement(accountId, account.getAccountNumber(), account.getCurrency(), from, to, opening, lines, closing);
    }

    public List<LedgerEntry> allEntries(UUID accountId, Instant from, Instant to) {
        Instant f = from == null ? Instant.EPOCH : from;
        Instant t = to == null ? Instant.now() : to;
        return ledgerEntryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(accountId, f, t, Pageable.unpaged()).getContent();
    }
}
