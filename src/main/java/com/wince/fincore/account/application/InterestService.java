package com.wince.fincore.account.application;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.InterestRun;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.account.repository.InterestRunRepository;
import com.wince.fincore.ledger.application.LedgerMovement;
import com.wince.fincore.ledger.application.LedgerService;
import com.wince.fincore.ledger.application.TransactionReferenceGenerator;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.LedgerEntryRepository;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import jakarta.persistence.EntityManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class InterestService {
    private static final BigDecimal RATE = new BigDecimal("0.035");
    private final AccountRepository accountRepository;
    private final InterestRunRepository interestRunRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final EntityManager entityManager;
    private final Clock clock;

    public InterestService(AccountRepository accountRepository, InterestRunRepository interestRunRepository,
                          LedgerEntryRepository ledgerEntryRepository, LedgerService ledgerService,
                          EntityManager entityManager, Clock clock) {
        this.accountRepository = accountRepository;
        this.interestRunRepository = interestRunRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerService = ledgerService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 1 * *", zone = "Africa/Porto-Novo")
    public void runMonthly() {
        LocalDate now = LocalDate.now(ClockConfig.BANKING_ZONE);
        int year = now.getYear();
        int month = now.getMonthValue();
        // compute for previous month
        YearMonth prev = YearMonth.from(now).minusMonths(1);
        if (interestRunRepository.findByRunYearAndRunMonth(prev.getYear(), prev.getMonthValue()).isPresent()) {
            return; // already run
        }
        execute(prev.getYear(), prev.getMonthValue());
    }

    @Transactional
    public void execute(int year, int month) {
        if (interestRunRepository.findByRunYearAndRunMonth(year, month).isPresent()) return;
        interestRunRepository.save(new InterestRun(UUID.randomUUID(), year, month));
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        int days = ym.lengthOfMonth();
        for (Account account : accountRepository.findAll()) {
            if (account.getKind() != AccountKind.SAVINGS) continue;
            if (account.getStatus() != com.wince.fincore.account.domain.AccountStatus.ACTIVE) continue;
            // average daily balance: use entries in month
            var entries = ledgerEntryRepository.findByAccountIdOrderById(account.getId()).stream()
                    .filter(e -> !e.getCreatedAt().isBefore(start.atStartOfDay(ClockConfig.BANKING_ZONE).toInstant())
                            && !e.getCreatedAt().isAfter(end.atTime(23,59,59).atZone(ClockConfig.BANKING_ZONE).toInstant()))
                    .toList();
            if (entries.isEmpty()) continue;
            BigDecimal sum = entries.stream().map(com.wince.fincore.ledger.domain.LedgerEntry::getBalanceAfter)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(new BigDecimal(entries.size()), 4, RoundingMode.HALF_EVEN);
            BigDecimal interest = avg.multiply(RATE).multiply(new BigDecimal(days))
                    .divide(new BigDecimal(365), account.getCurrency().scale(), RoundingMode.HALF_EVEN);
            if (interest.compareTo(BigDecimal.ZERO) <= 0) continue;
            interest = interest.setScale(account.getCurrency().scale(), RoundingMode.HALF_EVEN);
            com.wince.fincore.account.domain.SystemCode interestExpense = com.wince.fincore.account.domain.SystemCode.INTEREST_EXPENSE;
            Account expense = accountRepository.findBySystemCodeAndCurrency(interestExpense, account.getCurrency()).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
            FinancialTransaction txn = new FinancialTransaction(
                    UUID.randomUUID(), TransactionReferenceGenerator.generate(), TransactionType.INTEREST,
                    expense.getId(), account.getId(), null, interest, account.getCurrency(), BigDecimal.ZERO,
                    "Interets " + year + "-" + month);
            ledgerService.apply(txn, List.of(
                    new LedgerMovement(expense.getId(), Direction.DEBIT, interest, account.getCurrency()),
                    new LedgerMovement(account.getId(), Direction.CREDIT, interest, account.getCurrency())
            ));
        }
    }
}
