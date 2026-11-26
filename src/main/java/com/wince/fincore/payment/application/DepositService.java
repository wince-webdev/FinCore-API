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
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.payment.api.DepositCommand;
import com.wince.fincore.payment.application.LimitService;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage F-05 — dépôts (§7 F-05 — TELLER — RG-DEP-01 à 05).
 *
 * <p>{@link #deposit(DepositCommand, UUID)} — {@code @Transactional} (RG-LED-01).
 *
 * <p>{@link ErrorCode#ACCOUNT_NOT_ACTIVE} — compte {@code FROZEN|CLOSED} — 409.
 * {@link ErrorCode#INVALID_AMOUNT_SCALE} — échelle de devise — 400 — {@code Money.parse} — RG-DEP-03.
 * {@link ErrorCode#RESOURCE_NOT_FOUND} — compte ou CASH_VAULT inexistant.
 */
@Service
public class DepositService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final LimitService limitService;

    public DepositService(AccountRepository accountRepository, LedgerService ledgerService,
                          FinancialTransactionRepository financialTransactionRepository,
                          LimitService limitService) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.limitService = limitService;
    }

    /**
     * Dépôt — F-05 — RG-DEP-01 à 04 — RG-LED-01.
     *
     * <p>2 écritures — débit {@code CASH_VAULT} — crédit compte client — sans frais —
     * {@code Fee_amount = 0} — {@code Plafond de solde du niveau KYC (RG-DEP-01)} — Phase 6 — F-06 — {@code LIMIT_EXCEEDED}.
     *
     * @param initiatedBy — l'id de l'{@code AppUser} TELLER — {@code initiated_by} (§6.1).
     */
    @Transactional
    public FinancialTransaction deposit(DepositCommand command, UUID initiatedBy) {
        Account account = accountRepository.findById(command.accountId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        // RG-DEP-03 — montant strictement positif + échelle de la devise (Money.parse — 400 INVALID_AMOUNT_SCALE).
        Money amount = Money.parse(command.amount().toPlainString(), account.getCurrency());
        if (amount.amount().signum() <= 0) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Le montant doit être strictement positif (RG-DEP-03).");
        }
        // RG-DEP-04 — compte FROZEN ou CLOSED → ACCOUNT_NOT_ACTIVE.
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new FinCoreException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Le compte est " + account.getStatus() + " (RG-DEP-04) - depot impossible.");
        }
        if (limitService != null) {
            limitService.checkDeposit(account, amount.amount());
        }
        // Compte système — seed en migration — RG-ACC-06 — Phase 4.
        Account cashVault = accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, account.getCurrency())
                .orElseThrow(() -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte système introuvable."));

        FinancialTransaction transaction = new FinancialTransaction(
                UUID.randomUUID(), TransactionReferenceGenerator.generate(), TransactionType.DEPOSIT,
                cashVault.getId(), account.getId(), initiatedBy,
                amount.amount(), account.getCurrency(), BigDecimal.ZERO, command.description());
        return ledgerService.apply(transaction, List.of(
                new LedgerMovement(cashVault.getId(), Direction.DEBIT, amount.amount(), account.getCurrency()),
                new LedgerMovement(account.getId(), Direction.CREDIT, amount.amount(), account.getCurrency())));
    }
}
