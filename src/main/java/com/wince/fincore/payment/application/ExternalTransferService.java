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
import com.wince.fincore.ledger.domain.TransactionStatus;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.notification.domain.OutboxEvent;
import com.wince.fincore.notification.repository.OutboxEventRepository;
import com.wince.fincore.payment.api.DepositCommand;
import com.wince.fincore.payment.api.TransferCommand;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExternalTransferService {
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final LedgerService ledgerService;
    private final FinancialTransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ExternalPaymentProvider provider;
    private final FeeService feeService;
    private final LimitService limitService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ExternalTransferService(AccountRepository accountRepository, CustomerRepository customerRepository,
                                  LedgerService ledgerService, FinancialTransactionRepository transactionRepository,
                                  OutboxEventRepository outboxEventRepository, ExternalPaymentProvider provider,
                                  FeeService feeService, LimitService limitService, EntityManager entityManager,
                                  Clock clock, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.ledgerService = ledgerService;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.provider = provider;
        this.feeService = feeService;
        this.limitService = limitService;
        this.entityManager = entityManager;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FinancialTransaction create(TransferCommand command, UUID initiatedBy) {
        com.wince.fincore.customer.domain.Customer customer = customerRepository.findByUserId(initiatedBy).orElseThrow(
                () -> new FinCoreException(ErrorCode.ACCESS_DENIED, "Client introuvable."));
        Account source = accountRepository.findById(command.sourceAccountId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable."));
        if (source.getCustomerId() == null || !source.getCustomerId().equals(customer.getId())) {
            throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte source introuvable.");
        }
        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new FinCoreException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Compte non actif.");
        }
        BigDecimal amount = command.amount();
        if (amount.signum() <= 0) throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Montant invalide.");
        limitService.checkWithdrawal(source, amount);

        BigDecimal fee = feeService.calculate("EXTERNAL_TRANSFER", source.getCurrency(), amount);
        BigDecimal totalDebited = amount.add(fee);

        Account suspense = accountRepository.findBySystemCodeAndCurrency(SystemCode.SUSPENSE, source.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
        Account feeRevenue = accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, source.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));

        ledgerService.lockAccounts(List.of(source.getId(), suspense.getId(), feeRevenue.getId()));
        source = accountRepository.findById(source.getId()).orElseThrow(() -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        entityManager.refresh(source);
        suspense = accountRepository.findBySystemCodeAndCurrency(SystemCode.SUSPENSE, source.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
        entityManager.refresh(suspense);
        feeRevenue = accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, source.getCurrency()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
        entityManager.refresh(feeRevenue);

        if (source.getBalance().compareTo(totalDebited) < 0) {
            throw new FinCoreException(ErrorCode.INSUFFICIENT_FUNDS, "Solde insuffisant.");
        }

        FinancialTransaction transaction = new FinancialTransaction(
                UUID.randomUUID(), TransactionReferenceGenerator.generate(), TransactionType.EXTERNAL_TRANSFER,
                source.getId(), null, initiatedBy, amount, source.getCurrency(), fee, command.description());
        transaction.setStatus(TransactionStatus.PENDING);

        List<LedgerMovement> movements = List.of(
                new LedgerMovement(source.getId(), Direction.DEBIT, totalDebited, source.getCurrency()),
                new LedgerMovement(suspense.getId(), Direction.CREDIT, amount, source.getCurrency()),
                new LedgerMovement(feeRevenue.getId(), Direction.CREDIT, fee, source.getCurrency())
        );
        // remove zero fee movement
        List<LedgerMovement> filtered = movements.stream().filter(m -> m.amount().signum() > 0).toList();
        ledgerService.apply(transaction, filtered);

        // outbox
        String payload = "{\"transactionId\":\"" + transaction.getId() + "\",\"amount\":" + amount + ",\"currency\":\"" + source.getCurrency() + "\"}";
        outboxEventRepository.save(new OutboxEvent(UUID.randomUUID(), "financial_transaction", transaction.getId(),
                "EXTERNAL_TRANSFER_PENDING", payload));

        return transaction;
    }

    @Scheduled(fixedDelayString = "${fincore.external.scheduler-ms:5000}")
    public void processPending() {
        List<FinancialTransaction> pending = transactionRepository.findByTypeAndStatus(TransactionType.EXTERNAL_TRANSFER, TransactionStatus.PENDING);
        for (FinancialTransaction txn : pending) {
            try {
                processOne(txn.getId());
            } catch (Exception e) {
                // ignore, retry next cycle
            }
        }
    }

    @Transactional
    public void processOne(UUID transactionId) {
        FinancialTransaction txn = transactionRepository.findById(transactionId).orElseThrow();
        if (txn.getStatus() != TransactionStatus.PENDING) return;
        if (txn.getCreatedAt().plusSeconds(900).isBefore(clock.instant())) {
            failAndRefund(txn, "Timeout 15 minutes");
            return;
        }
        ExternalPaymentProvider.Result result = provider.process(txn.getId());
        if (result == ExternalPaymentProvider.Result.SUCCESS) {
            complete(txn);
        } else {
            failAndRefund(txn, result == ExternalPaymentProvider.Result.TIMEOUT ? "Timeout fournisseur" : "Echec fournisseur");
        }
    }

    private void complete(FinancialTransaction txn) {
        Account suspense = accountRepository.findBySystemCodeAndCurrency(SystemCode.SUSPENSE, txn.getCurrency()).orElseThrow();
        Account settlement = accountRepository.findBySystemCodeAndCurrency(SystemCode.EXTERNAL_SETTLEMENT, txn.getCurrency()).orElseThrow();
        ledgerService.lockAccounts(List.of(suspense.getId(), settlement.getId()));
        entityManager.refresh(suspense);
        entityManager.refresh(settlement);
        List<LedgerMovement> movements = List.of(
                new LedgerMovement(suspense.getId(), Direction.DEBIT, txn.getAmount(), txn.getCurrency()),
                new LedgerMovement(settlement.getId(), Direction.CREDIT, txn.getAmount(), txn.getCurrency())
        );
        FinancialTransaction update = txn;
        update.setStatus(TransactionStatus.COMPLETED);
        ledgerService.apply(update, movements);
        update.setCompletedAt(clock.instant());
        transactionRepository.save(update);
        outboxEventRepository.save(new OutboxEvent(java.util.UUID.randomUUID(), "financial_transaction", update.getId(),
                "EXTERNAL_TRANSFER_COMPLETED", "{\"transactionId\":\"" + update.getId() + "\"}"));
    }

    private void failAndRefund(FinancialTransaction txn, String reason) {
        Account source = accountRepository.findById(txn.getSourceAccountId()).orElseThrow();
        Account suspense = accountRepository.findBySystemCodeAndCurrency(SystemCode.SUSPENSE, txn.getCurrency()).orElseThrow();
        Account feeRevenue = accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, txn.getCurrency()).orElseThrow();
        ledgerService.lockAccounts(List.of(source.getId(), suspense.getId(), feeRevenue.getId()));
        entityManager.refresh(source);
        entityManager.refresh(suspense);
        entityManager.refresh(feeRevenue);
        BigDecimal total = txn.getAmount().add(txn.getFeeAmount());
        List<LedgerMovement> movements = new java.util.ArrayList<>();
        movements.add(new LedgerMovement(suspense.getId(), Direction.DEBIT, txn.getAmount(), txn.getCurrency()));
        if (txn.getFeeAmount().signum() > 0) movements.add(new LedgerMovement(feeRevenue.getId(), Direction.DEBIT, txn.getFeeAmount(), txn.getCurrency()));
        movements.add(new LedgerMovement(source.getId(), Direction.CREDIT, total, txn.getCurrency()));
        txn.setStatus(TransactionStatus.FAILED);
        txn.setFailureReason(reason);
        ledgerService.apply(txn, movements);
        transactionRepository.save(txn);
        outboxEventRepository.save(new OutboxEvent(java.util.UUID.randomUUID(), "financial_transaction", txn.getId(),
                "EXTERNAL_TRANSFER_FAILED", "{\"transactionId\":\"" + txn.getId() + "\",\"reason\":\"" + reason + "\"}"));
    }
}
