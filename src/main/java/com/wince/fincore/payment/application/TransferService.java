package com.wince.fincore.payment.application;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.ledger.application.LedgerMovement;
import com.wince.fincore.ledger.application.LedgerService;
import com.wince.fincore.ledger.application.TransactionReferenceGenerator;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.payment.api.TransferCommand;
import com.wince.fincore.payment.api.TransferResponse;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final LedgerService ledgerService;
    private final FinancialTransactionRepository transactionRepository;
    private final FeeService feeService;
    private final LimitService limitService;
    private final EntityManager entityManager;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public TransferService(AccountRepository accountRepository, CustomerRepository customerRepository,
                          LedgerService ledgerService, FinancialTransactionRepository transactionRepository,
                          FeeService feeService, LimitService limitService, EntityManager entityManager,
                          io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.ledgerService = ledgerService;
        this.transactionRepository = transactionRepository;
        this.feeService = feeService;
        this.limitService = limitService;
        this.entityManager = entityManager;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public TransferResult transfer(TransferCommand command, UUID initiatedBy) {
        Customer customer = customerRepository.findByUserId(initiatedBy).orElseThrow(
                () -> new FinCoreException(ErrorCode.ACCESS_DENIED, "Client introuvable."));
        Account source = accountRepository.findById(command.sourceAccountId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable."));
        if (source.getCustomerId() == null || !source.getCustomerId().equals(customer.getId())) {
            throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable.");
        }
        Account destination;
        if (command.destinationAccountId() != null) {
            destination = accountRepository.findById(command.destinationAccountId()).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte destination introuvable."));
        } else {
            destination = accountRepository.findByAccountNumber(command.destinationAccountNumber()).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte destination introuvable."));
        }
        if (source.getId().equals(destination.getId())) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Source et destination doivent etre differents.");
        }
        if (source.getStatus() != AccountStatus.ACTIVE || destination.getStatus() != AccountStatus.ACTIVE) {
            throw new FinCoreException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Compte non actif.");
        }
        if (source.getCurrency() != destination.getCurrency()) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Les comptes doivent etre dans la meme devise.");
        }
        Currency currency = source.getCurrency();
        if (command.currency() != null && command.currency() != currency) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Devise incoherente.");
        }
        if (command.amount().signum() <= 0) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Le montant doit etre strictement positif.");
        }
        BigDecimal amount = command.amount();
        MoneyCheck(amount, currency);

        // limits
        limitService.checkTransfer(source, destination, amount);

        BigDecimal fee = feeService.calculate("TRANSFER", currency, amount);
        BigDecimal totalDebited = amount.add(fee);

        Account feeRevenue = accountRepository.findBySystemCodeAndCurrency(
                com.wince.fincore.account.domain.SystemCode.FEE_REVENUE, currency).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));

        // lock
        ledgerService.lockAccounts(List.of(source.getId(), destination.getId(), feeRevenue.getId()));
        source = accountRepository.findById(source.getId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        entityManager.refresh(source);
        destination = accountRepository.findById(destination.getId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        entityManager.refresh(destination);
        feeRevenue = accountRepository.findBySystemCodeAndCurrency(
                com.wince.fincore.account.domain.SystemCode.FEE_REVENUE, currency).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
        entityManager.refresh(feeRevenue);

        if (source.getBalance().compareTo(totalDebited) < 0) {
            throw new FinCoreException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Solde insuffisant : disponible " + source.getBalance() + ", requis " + totalDebited + ".");
        }

        FinancialTransaction transaction = new FinancialTransaction(
                UUID.randomUUID(), TransactionReferenceGenerator.generate(), TransactionType.TRANSFER,
                source.getId(), destination.getId(), initiatedBy, amount, currency, fee, command.description());

        List<LedgerMovement> movements = new ArrayList<>();
        movements.add(new LedgerMovement(source.getId(), Direction.DEBIT, totalDebited, currency));
        movements.add(new LedgerMovement(destination.getId(), Direction.CREDIT, amount, currency));
        if (fee.signum() > 0) {
            movements.add(new LedgerMovement(feeRevenue.getId(), Direction.CREDIT, fee, currency));
        }
        ledgerService.apply(transaction, movements);

        BigDecimal sourceBalanceAfter = source.getBalance().subtract(totalDebited);
        BigDecimal destinationBalanceAfter = destination.getBalance().add(amount);
        meterRegistry.counter("fincore.transactions.count", "type", "TRANSFER", "status", "COMPLETED").increment();
        return new TransferResult(transaction, sourceBalanceAfter, destinationBalanceAfter, fee, totalDebited, null);
    }

    public TransferResult quote(TransferCommand command, UUID initiatedBy) {
        Customer customer = customerRepository.findByUserId(initiatedBy).orElseThrow(
                () -> new FinCoreException(ErrorCode.ACCESS_DENIED, "Client introuvable."));
        Account source = accountRepository.findById(command.sourceAccountId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable."));
        if (source.getCustomerId() == null || !source.getCustomerId().equals(customer.getId())) {
            throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable.");
        }
        Account destination;
        if (command.destinationAccountId() != null) {
            destination = accountRepository.findById(command.destinationAccountId()).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte destination introuvable."));
        } else {
            destination = accountRepository.findByAccountNumber(command.destinationAccountNumber()).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte destination introuvable."));
        }
        if (source.getStatus() != AccountStatus.ACTIVE || destination.getStatus() != AccountStatus.ACTIVE) {
            throw new FinCoreException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Compte non actif.");
        }
        if (source.getCurrency() != destination.getCurrency()) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Les comptes doivent etre dans la meme devise.");
        }
        Currency currency = source.getCurrency();
        BigDecimal amount = command.amount();
        if (amount.signum() <= 0) throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Montant invalide.");
        limitService.checkTransfer(source, destination, amount);
        BigDecimal fee = feeService.calculate("TRANSFER", currency, amount);
        BigDecimal totalDebited = amount.add(fee);
        BigDecimal remaining = limitService.remainingDailyLimit(source);
        BigDecimal projectedSourceBalance = source.getBalance().subtract(totalDebited);
        return new TransferResult(null, projectedSourceBalance, null, fee, totalDebited, remaining);
    }

    private void MoneyCheck(BigDecimal amount, Currency currency) {
        // validation echelle deja faite cote DTO? ensure
    }

    public record TransferResult(FinancialTransaction transaction, BigDecimal sourceBalanceAfter,
                                 BigDecimal destinationBalanceAfter, BigDecimal fee, BigDecimal totalDebited,
                                 BigDecimal remainingDailyLimit) {
    }
}
