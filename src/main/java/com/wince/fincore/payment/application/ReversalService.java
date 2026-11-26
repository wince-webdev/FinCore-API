package com.wince.fincore.payment.application;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.ledger.application.LedgerMovement;
import com.wince.fincore.ledger.application.LedgerService;
import com.wince.fincore.ledger.application.TransactionReferenceGenerator;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionStatus;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
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
public class ReversalService {
    private final FinancialTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final EntityManager entityManager;

    public ReversalService(FinancialTransactionRepository transactionRepository, AccountRepository accountRepository,
                          LedgerService ledgerService, EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.entityManager = entityManager;
    }

    @Transactional
    public FinancialTransaction reverse(UUID transactionId, String motif, UUID initiatedBy) {
        FinancialTransaction original = transactionRepository.findById(transactionId).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Transaction introuvable."));
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new FinCoreException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Seule une transaction COMPLETED peut etre contre-passee.");
        }
        if (transactionRepository.findByReversalOf(original.getId()).stream().findAny().isPresent()) {
            throw new FinCoreException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Transaction deja contre-passee.");
        }
        TransactionType type = original.getType();
        if (type != TransactionType.DEPOSIT && type != TransactionType.WITHDRAWAL
                && type != TransactionType.TRANSFER && type != TransactionType.FX_TRANSFER) {
            throw new FinCoreException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Type de transaction non reversible.");
        }

        Currency currency = original.getCurrency();
        BigDecimal amount = original.getAmount();
        BigDecimal fee = original.getFeeAmount();

        // determine accounts
        List<UUID> toLock = new ArrayList<>();
        // original source/dest
        if (original.getSourceAccountId() != null) toLock.add(original.getSourceAccountId());
        if (original.getDestinationAccountId() != null) toLock.add(original.getDestinationAccountId());
        // system accounts
        if (type == TransactionType.DEPOSIT) {
            Account cashVault = accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, currency).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
            toLock.add(cashVault.getId());
        } else if (type == TransactionType.WITHDRAWAL) {
            Account cashVault = accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, currency).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
            Account feeRevenue = accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, currency).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
            toLock.add(cashVault.getId());
            toLock.add(feeRevenue.getId());
        } else if (type == TransactionType.TRANSFER) {
            Account feeRevenue = accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, currency).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
            toLock.add(feeRevenue.getId());
        }
        ledgerService.lockAccounts(toLock);

        // reload accounts
        Account originalSource = original.getSourceAccountId() == null ? null :
                accountRepository.findById(original.getSourceAccountId()).orElseThrow(
                        () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        if (originalSource != null) entityManager.refresh(originalSource);
        Account originalDest = original.getDestinationAccountId() == null ? null :
                accountRepository.findById(original.getDestinationAccountId()).orElseThrow(
                        () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        if (originalDest != null) entityManager.refresh(originalDest);

        // check sufficient funds for accounts being debited in reversal
        // DEPOSIT reversal: debit client (destination) amount
        // WITHDRAWAL reversal: debit cashVault amount + feeRevenue fee (system allowed negative? skip check for system)
        // TRANSFER reversal: debit destination amount + feeRevenue fee
        if (type == TransactionType.DEPOSIT) {
            if (originalDest != null && originalDest.getCustomerId() != null
                    && originalDest.getBalance().compareTo(amount) < 0) {
                throw new FinCoreException(ErrorCode.REVERSAL_IMPOSSIBLE_INSUFFICIENT_FUNDS == null ? ErrorCode.INSUFFICIENT_FUNDS : ErrorCode.REVERSAL_IMPOSSIBLE_INSUFFICIENT_FUNDS,
                        "Reversal impossible : fonds insuffisants.");
            }
        } else if (type == TransactionType.TRANSFER) {
            if (originalDest != null && originalDest.getCustomerId() != null
                    && originalDest.getBalance().compareTo(amount) < 0) {
                throw new FinCoreException(ErrorCode.REVERSAL_IMPOSSIBLE_INSUFFICIENT_FUNDS == null ? ErrorCode.INSUFFICIENT_FUNDS : ErrorCode.REVERSAL_IMPOSSIBLE_INSUFFICIENT_FUNDS,
                        "Reversal impossible : fonds insuffisants.");
            }
        }

        // build movements (inverse)
        List<LedgerMovement> movements = new ArrayList<>();
        Account feeRevenue = null;
        Account cashVault = null;
        if (type == TransactionType.DEPOSIT || type == TransactionType.WITHDRAWAL) {
            cashVault = accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, currency).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
            entityManager.refresh(cashVault);
        }
        if (type == TransactionType.WITHDRAWAL || type == TransactionType.TRANSFER) {
            feeRevenue = accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, currency).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte systeme introuvable."));
            entityManager.refresh(feeRevenue);
        }

        if (type == TransactionType.DEPOSIT) {
            // original: debit cashVault, credit client. inverse: debit client, credit cashVault
            movements.add(new LedgerMovement(originalDest.getId(), Direction.DEBIT, amount, currency));
            movements.add(new LedgerMovement(cashVault.getId(), Direction.CREDIT, amount, currency));
        } else if (type == TransactionType.WITHDRAWAL) {
            // original: debit client (amount+fee), credit cashVault (amount), credit feeRevenue (fee)
            // inverse: debit cashVault (amount), debit feeRevenue (fee), credit client (amount+fee)
            movements.add(new LedgerMovement(cashVault.getId(), Direction.DEBIT, amount, currency));
            if (fee.signum() > 0) movements.add(new LedgerMovement(feeRevenue.getId(), Direction.DEBIT, fee, currency));
            movements.add(new LedgerMovement(originalSource.getId(), Direction.CREDIT, amount.add(fee), currency));
        } else if (type == TransactionType.TRANSFER) {
            // original: debit source (amount+fee), credit dest (amount), credit feeRevenue (fee)
            // inverse: debit dest (amount), debit feeRevenue (fee), credit source (amount+fee)
            movements.add(new LedgerMovement(originalDest.getId(), Direction.DEBIT, amount, currency));
            if (fee.signum() > 0) movements.add(new LedgerMovement(feeRevenue.getId(), Direction.DEBIT, fee, currency));
            movements.add(new LedgerMovement(originalSource.getId(), Direction.CREDIT, amount.add(fee), currency));
        }

        FinancialTransaction reversal = new FinancialTransaction(
                UUID.randomUUID(), TransactionReferenceGenerator.generate(), TransactionType.REVERSAL,
                // source = account being debited first? set original destination as source
                (type == TransactionType.DEPOSIT ? originalDest.getId() : (type == TransactionType.WITHDRAWAL ? cashVault.getId() : originalDest.getId())),
                (type == TransactionType.DEPOSIT ? cashVault.getId() : (type == TransactionType.WITHDRAWAL ? originalSource.getId() : originalSource.getId())),
                initiatedBy, amount, currency, fee, "Reversal: " + motif);
        reversal.setReversalOf(original.getId());

        ledgerService.apply(reversal, movements);

        // mark original reversed
        original.setStatus(TransactionStatus.REVERSED);
        // FinancialTransaction has setStatus; but JPA entity mutable. However no setter for reversal? setStatus exists. Good.
        transactionRepository.save(original);

        return reversal;
    }
}
