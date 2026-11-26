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
import com.wince.fincore.payment.api.WithdrawalCommand;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.domain.Money;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage F-05 — retraits (§7 F-05 — TELLER — RG-DEP-02).
 *
 * <p>Écritures — débit compte client (montant + frais), crédit {@code CASH_VAULT} (montant)
 * + crédit {@code FEE_REVENUE} (frais) — Σ DEBIT = Σ CREDIT — {@code RG-LED-02}.
 *
 * <p>Frais — barème <strong>annexe A</strong> (§17.1 — ligne « Retrait ») : 0,3 % + 0
 * — plancher 100 XOF — plafond 3 000 XOF — {@code pédagogique (Phase 6 — barème paramétrique
 * dans {@code fee_rule} — F-06 — février)} — le {@code ErrorCode#LIMIT_EXCEEDED} d'entrée
 * apparaît en Phase 6.
 *
 * <p>{@link #withdraw(WithdrawalCommand, UUID)} — {@code @Transactional} — le refus
 * {@link ErrorCode#INSUFFICIENT_FUNDS} (RG-DEP-02) est vérifié <strong>sous le
 * verrou</strong> {@code RG-LED-06} — 0 écriture — {@code CA-DEP-02}.
 */
@Service
public class WithdrawalService {

    /** 0,3 % — annexe A — ligne « Retrait » (§17.1). */
    private static final BigDecimal WITHDRAWAL_RATE = new BigDecimal("0.003");

    /** Minimum — annexe A — ligne « Retrait » — 100 XOF. */
    private static final BigDecimal WITHDRAWAL_MIN_FEE = new BigDecimal("100");

    /** Maximum — annexe A — ligne « Retrait » — 3 000 XOF. */
    private static final BigDecimal WITHDRAWAL_MAX_FEE = new BigDecimal("3000");

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final EntityManager entityManager;

    public WithdrawalService(AccountRepository accountRepository, LedgerService ledgerService, EntityManager entityManager) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.entityManager = entityManager;
    }

    /**
     * Retrait — F-05 — RG-DEP-02.
     *
     * <ul>
     *   <li>RG-DEP-03 — montant strictement positif — échelle de devise — {@code Money.parse} → 400 {@code INVALID_AMOUNT_SCALE};</li>
     *   <li>RG-DEP-04 — {@code ACCOUNT_NOT_ACTIVE} — 409;</li>
     *   <li>RG-DEP-02 — insuffisant — {@code INSUFFICIENT_FUNDS} — vérifié sous verrou — 0 écriture — CA-DEP-02;</li>
     *   <li>RG-DEP-05 — plafond solde (Phase 6 — {@code LIMIT_EXCEEDED}).</li>
     * </ul>
     *
     * @param initiatedBy — l'id de l'{@code AppUser} TELLER — {@code initiated_by} (§6.1).
     */
    @Transactional
    public FinancialTransaction withdraw(WithdrawalCommand command, UUID initiatedBy) {
        Account account = accountRepository.findById(command.accountId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        // RG-DEP-03 — échelle de devise (Money.parse — 400 INVALID_AMOUNT_SCALE).
        Money amount = Money.parse(command.amount().toPlainString(), account.getCurrency());
        if (amount.amount().signum() <= 0) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "Le montant doit être strictement positif (RG-DEP-03).");
        }
        // RG-DEP-04 — compte FROZEN ou CLOSED → ACCOUNT_NOT_ACTIVE.
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new FinCoreException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Le compte est " + account.getStatus() + " (RG-DEP-04) — le retrait est impossible.");
        }
        BigDecimal fee = withdrawalFee(amount, account.getCurrency());
        // Comptes systèmes — seed — RG-ACC-06 — Phase 4.
        Account cashVault = accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, account.getCurrency())
                .orElseThrow(() -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte système introuvable."));
        Account feeRevenue = accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, account.getCurrency())
                .orElseThrow(() -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte système introuvable."));
        // RG-LED-06 — verrouillage — tri par identifiant croissant — vérification sous-verrou.
        ledgerService.lockAccounts(Set.of(account.getId(), cashVault.getId(), feeRevenue.getId()));
        account = accountRepository.findById(account.getId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        entityManager.refresh(account);
        // RG-DEP-02 — insuffisant — vérifié sous verrou — 0 écriture — CA-DEP-02.
        if (account.getBalance().compareTo(amount.amount().add(fee)) < 0) {
            throw new FinCoreException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Solde insuffisant pour le retrait + frais (RG-DEP-02) — solde="
                            + account.getBalance().toPlainString() + " — exigé="
                            + amount.amount().add(fee).toPlainString());
        }
        FinancialTransaction transaction = new FinancialTransaction(
                UUID.randomUUID(), TransactionReferenceGenerator.generate(), TransactionType.WITHDRAWAL,
                account.getId(), cashVault.getId(), initiatedBy,
                amount.amount(), account.getCurrency(), fee, command.description());
        return ledgerService.apply(transaction, List.of(
                // RG-LED-02 — Σ DEBIT = Σ CREDIT — débit client + frais — crédit CASH_VAULT — crédit FEE_REVENUE.
                new LedgerMovement(account.getId(), Direction.DEBIT, amount.amount().add(fee), account.getCurrency()),
                new LedgerMovement(cashVault.getId(), Direction.CREDIT, amount.amount(), account.getCurrency()),
                new LedgerMovement(feeRevenue.getId(), Direction.CREDIT, fee, account.getCurrency())));
    }

    /** Frais de retrait — barème annexe A — ligne « Retrait » — Phase 6 : {@code fee_rule} (F-06). */
    private static BigDecimal withdrawalFee(Money amount, Currency currency) {
        BigDecimal fee = amount.amount().multiply(WITHDRAWAL_RATE);
        return new Money(fee.max(WITHDRAWAL_MIN_FEE).min(WITHDRAWAL_MAX_FEE).setScale(0, RoundingMode.HALF_EVEN), currency).amount();
    }
}
