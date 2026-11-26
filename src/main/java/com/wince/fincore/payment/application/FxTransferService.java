package com.wince.fincore.payment.application;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.ledger.application.LedgerMovement;
import com.wince.fincore.ledger.application.LedgerService;
import com.wince.fincore.ledger.application.TransactionReferenceGenerator;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.payment.api.TransferCommand;
import com.wince.fincore.payment.domain.FxRate;
import com.wince.fincore.payment.repository.FxRateRepository;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.domain.Money;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FxTransferService {
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final LedgerService ledgerService;
    private final FinancialTransactionRepository transactionRepository;
    private final FxRateRepository fxRateRepository;
    private final LimitService limitService;
    private final EntityManager entityManager;
    private final Clock clock;

    public FxTransferService(AccountRepository accountRepository, CustomerRepository customerRepository,
                            LedgerService ledgerService, FinancialTransactionRepository transactionRepository,
                            FxRateRepository fxRateRepository, LimitService limitService, EntityManager entityManager,
                            Clock clock) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.ledgerService = ledgerService;
        this.transactionRepository = transactionRepository;
        this.fxRateRepository = fxRateRepository;
        this.limitService = limitService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public record FxResult(FinancialTransaction transaction, BigDecimal sourceBalanceAfter, BigDecimal destinationBalanceAfter,
                           BigDecimal convertedAmount, BigDecimal rate, BigDecimal spreadPercent, BigDecimal fee,
                           Currency destinationCurrency) {}

    public FxResult quote(TransferCommand command, UUID initiatedBy) {
        // resolve accounts
        Account source = resolveSource(command, initiatedBy);
        Account destination = resolveDestination(command);
        if (source.getCurrency() == destination.getCurrency()) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Les devises doivent etre differentes pour un change.");
        }
        if (source.getStatus() != AccountStatus.ACTIVE || destination.getStatus() != AccountStatus.ACTIVE) {
            throw new FinCoreException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Compte non actif.");
        }
        BigDecimal amount = command.amount();
        if (amount.signum() <= 0) throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Montant invalide.");
        FxRate fx = rate(source.getCurrency(), destination.getCurrency());
        BigDecimal rawConverted = amount.multiply(fx.getRate());
        BigDecimal spread = fx.getSpreadPercent();
        BigDecimal converted = rawConverted.multiply(BigDecimal.ONE.subtract(spread.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_EVEN)));
        converted = converted.setScale(destination.getCurrency().scale(), RoundingMode.HALF_EVEN);
        BigDecimal fee = BigDecimal.ZERO;
        return new FxResult(null, source.getBalance().subtract(amount), destination.getBalance().add(converted),
                converted, fx.getRate(), spread, fee, destination.getCurrency());
    }

    @Transactional
    public FxResult transfer(TransferCommand command, UUID initiatedBy) {
        Account source = resolveSource(command, initiatedBy);
        Account destination = resolveDestination(command);
        if (source.getCurrency() == destination.getCurrency()) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Les devises doivent etre differentes pour un change.");
        }
        if (source.getStatus() != AccountStatus.ACTIVE || destination.getStatus() != AccountStatus.ACTIVE) {
            throw new FinCoreException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Compte non actif.");
        }
        if (source.getId().equals(destination.getId())) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Source et destination doivent etre differents.");
        }
        BigDecimal amount = command.amount();
        if (amount.signum() <= 0) throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Montant invalide.");

        limitService.checkTransfer(source, destination, amount);

        FxRate fx = rate(source.getCurrency(), destination.getCurrency());
        BigDecimal rawConverted = amount.multiply(fx.getRate());
        BigDecimal spread = fx.getSpreadPercent();
        BigDecimal converted = rawConverted.multiply(BigDecimal.ONE.subtract(spread.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_EVEN)));
        converted = converted.setScale(destination.getCurrency().scale(), RoundingMode.HALF_EVEN);

        BigDecimal fee = BigDecimal.ZERO;

        Account fxPosSource = accountRepository.findBySystemCodeAndCurrency(SystemCode.FX_POSITION, source.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
        Account fxPosDest = accountRepository.findBySystemCodeAndCurrency(SystemCode.FX_POSITION, destination.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));

        List<UUID> toLock = List.of(source.getId(), destination.getId(), fxPosSource.getId(), fxPosDest.getId());
        ledgerService.lockAccounts(toLock);
        source = accountRepository.findById(source.getId()).orElseThrow(() -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        entityManager.refresh(source);
        destination = accountRepository.findById(destination.getId()).orElseThrow(() -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        entityManager.refresh(destination);
        fxPosSource = accountRepository.findBySystemCodeAndCurrency(SystemCode.FX_POSITION, source.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
        entityManager.refresh(fxPosSource);
        fxPosDest = accountRepository.findBySystemCodeAndCurrency(SystemCode.FX_POSITION, destination.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
        entityManager.refresh(fxPosDest);

        if (source.getBalance().compareTo(amount) < 0) {
            throw new FinCoreException(ErrorCode.INSUFFICIENT_FUNDS, "Solde insuffisant.");
        }

        FinancialTransaction transaction = new FinancialTransaction(
                UUID.randomUUID(), TransactionReferenceGenerator.generate(), TransactionType.FX_TRANSFER,
                source.getId(), destination.getId(), initiatedBy, amount, source.getCurrency(), fee, command.description());
        transaction.setDestinationAmount(converted);
        transaction.setExchangeRate(fx.getRate());
        transaction.setSpreadPercent(spread);

        List<LedgerMovement> movements = new ArrayList<>();
        movements.add(new LedgerMovement(source.getId(), Direction.DEBIT, amount, source.getCurrency()));
        movements.add(new LedgerMovement(fxPosSource.getId(), Direction.CREDIT, amount, source.getCurrency()));
        movements.add(new LedgerMovement(fxPosDest.getId(), Direction.DEBIT, converted, destination.getCurrency()));
        movements.add(new LedgerMovement(destination.getId(), Direction.CREDIT, converted, destination.getCurrency()));
        ledgerService.apply(transaction, movements);

        BigDecimal sourceBalanceAfter = source.getBalance().subtract(amount);
        BigDecimal destinationBalanceAfter = destination.getBalance().add(converted);
        return new FxResult(transaction, sourceBalanceAfter, destinationBalanceAfter, converted, fx.getRate(), spread, fee, destination.getCurrency());
    }

    private Account resolveSource(TransferCommand command, UUID initiatedBy) {
        com.wince.fincore.customer.domain.Customer customer = customerRepository.findByUserId(initiatedBy).orElseThrow(
                () -> new FinCoreException(ErrorCode.ACCESS_DENIED, "Client introuvable."));
        Account source = accountRepository.findById(command.sourceAccountId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable."));
        if (source.getCustomerId() == null || !source.getCustomerId().equals(customer.getId())) {
            throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable.");
        }
        return source;
    }

    private Account resolveDestination(TransferCommand command) {
        if (command.destinationAccountId() != null) {
            return accountRepository.findById(command.destinationAccountId()).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte destination introuvable."));
        }
        return accountRepository.findByAccountNumber(command.destinationAccountNumber()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte destination introuvable."));
    }

    private FxRate rate(Currency base, Currency quote) {
        return fxRateRepository.findTopByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(
                base, quote, clock.instant()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Taux de change introuvable."));
    }
}
