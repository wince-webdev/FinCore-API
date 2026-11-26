package com.wince.fincore.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.ledger.application.LedgerService;
import com.wince.fincore.ledger.domain.Direction;
import jakarta.persistence.EntityManager;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.payment.api.WithdrawalCommand;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitaires de {@link WithdrawalService} — Phase 5 — F-05 — RG-DEP-02.
 *
 * <p>Frais — barème annexe A (ligne « Retrait ») : 0,3 % + 0 — min 100 XOF — max 3 000 XOF.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    private static final UUID TELLER_ID = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private EntityManager entityManager;

    private WithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawalService(accountRepository, ledgerService, entityManager);
    }

    private static Account account(UUID id, AccountStatus status, BigDecimal balance) {
        Account account = new Account(id, "BJ123456789012", UUID.randomUUID(), AccountKind.CURRENT, null, Currency.XOF, balance);
        account.setStatus(status);
        return account;
    }

    @Test
    @DisplayName("RG-DEP-02 : 3 écritures — client −(amount+fee), CASH_VAULT +amount, FEE_REVENUE +fee")
    void withdraw_posts_3_movements_with_fee() {
        Account account = account(UUID.randomUUID(), AccountStatus.ACTIVE, BigDecimal.valueOf(1000));
        Account cashVault = account(UUID.randomUUID(), AccountStatus.ACTIVE, BigDecimal.ZERO);
        Account feeRevenue = account(UUID.randomUUID(), AccountStatus.ACTIVE, BigDecimal.ZERO);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, Currency.XOF)).thenReturn(Optional.of(cashVault));
        when(accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, Currency.XOF)).thenReturn(Optional.of(feeRevenue));
        when(ledgerService.apply(any(FinancialTransaction.class), any(List.class))).thenAnswer(inv -> inv.getArgument(0));

        // XOF — 100 → frais = clamp(100 × 0,003 + 0, min 100, max 3000) = 100 XOF — RG-DEP-02.
        FinancialTransaction transaction = service.withdraw(new WithdrawalCommand(account.getId(), BigDecimal.valueOf(100), null), TELLER_ID);

        assertThat(transaction.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(transaction.getFeeAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(transaction.getSourceAccountId()).isEqualTo(account.getId());
        verify(ledgerService).apply(any(FinancialTransaction.class),
                argThat(movements -> movements.size() == 3
                        && movements.get(0).accountId().equals(account.getId())
                        && movements.get(0).direction() == Direction.DEBIT
                        && movements.get(0).amount().compareTo(BigDecimal.valueOf(200)) == 0
                        && movements.get(1).accountId().equals(cashVault.getId())
                        && movements.get(1).direction() == Direction.CREDIT
                        && movements.get(1).amount().compareTo(BigDecimal.valueOf(100)) == 0
                        && movements.get(2).accountId().equals(feeRevenue.getId())
                        && movements.get(2).direction() == Direction.CREDIT
                        && movements.get(2).amount().compareTo(BigDecimal.valueOf(100)) == 0));
    }

    @Test
    @DisplayName("CA-DEP-02 : solde insuffisant → INSUFFICIENT_FUNDS — 0 écriture (RG-DEP-02)")
    void withdraw_insufficient_funds_rejected_without_entries() {
        Account account = account(UUID.randomUUID(), AccountStatus.ACTIVE, BigDecimal.valueOf(100));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, Currency.XOF))
                .thenReturn(Optional.of(account(UUID.randomUUID(), AccountStatus.ACTIVE, BigDecimal.ZERO)));
        when(accountRepository.findBySystemCodeAndCurrency(SystemCode.FEE_REVENUE, Currency.XOF))
                .thenReturn(Optional.of(account(UUID.randomUUID(), AccountStatus.ACTIVE, BigDecimal.ZERO)));

        assertThatThrownBy(() -> service.withdraw(new WithdrawalCommand(account.getId(), BigDecimal.valueOf(500), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        // 0 écriture — RG-LED-01 — rien envoyé au grand livre.
        verify(ledgerService, never()).apply(any(FinancialTransaction.class), any(List.class));
    }

    @Test
    @DisplayName("RG-DEP-04 : compte CLOSED → ACCOUNT_NOT_ACTIVE")
    void withdraw_on_closed_account_is_rejected() {
        Account account = account(UUID.randomUUID(), AccountStatus.CLOSED, BigDecimal.valueOf(1000));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdraw(new WithdrawalCommand(account.getId(), BigDecimal.valueOf(10), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE));
    }

    @Test
    @DisplayName("RG-DEP-03 : échelle XOF — 1,50 → INVALID_AMOUNT_SCALE — 400")
    void withdraw_with_wrong_scale_is_rejected() {
        Account account = account(UUID.randomUUID(), AccountStatus.ACTIVE, BigDecimal.valueOf(1000));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdraw(new WithdrawalCommand(account.getId(), new BigDecimal("1.50"), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.INVALID_AMOUNT_SCALE));
    }

    @Test
    @DisplayName("§5 : compte introuvable → RESOURCE_NOT_FOUND")
    void withdraw_on_unknown_account_is_404() {
        UUID unknownAccountId = UUID.randomUUID();
        when(accountRepository.findById(unknownAccountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(new WithdrawalCommand(unknownAccountId, BigDecimal.valueOf(10), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

}
