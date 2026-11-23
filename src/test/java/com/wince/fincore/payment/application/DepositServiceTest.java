package com.wince.fincore.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.ledger.application.LedgerService;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.payment.api.DepositCommand;
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
 * Tests unitaires de {@link DepositService} — Phase 5 — F-05 — RG-DEP-01 à 04.
 */
@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    private static final UUID TELLER_ID = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    private DepositService service;

    @BeforeEach
    void setUp() {
        service = new DepositService(accountRepository, ledgerService, financialTransactionRepository);
    }

    private static Account account(UUID id, AccountStatus status) {
        Account account = new Account(id, "BJ123456789012", UUID.randomUUID(), AccountKind.CURRENT, null, Currency.XOF, BigDecimal.ZERO);
        account.setStatus(status);
        return account;
    }

    @Test
    @DisplayName("RG-DEP-01/RG-LED-02 : dépôt — 2 écritures — CASH_VAULT −amount + compte +amount — sans frais")
    void deposit_posts_2_movements_without_fee() {
        Account account = account(UUID.randomUUID(), AccountStatus.ACTIVE);
        Account cashVault = account(UUID.randomUUID(), AccountStatus.ACTIVE);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, Currency.XOF)).thenReturn(Optional.of(cashVault));
        when(ledgerService.apply(any(FinancialTransaction.class), any(List.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancialTransaction transaction = service.deposit(new DepositCommand(account.getId(), BigDecimal.valueOf(50000), null), TELLER_ID);

        assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(transaction.getFeeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transaction.getSourceAccountId()).isEqualTo(cashVault.getId());
        assertThat(transaction.getDestinationAccountId()).isEqualTo(account.getId());
        verify(ledgerService).apply(any(FinancialTransaction.class),
                argThat(movements -> movements.size() == 2
                        && movements.get(0).accountId().equals(cashVault.getId())
                        && movements.get(0).direction().name().equals("DEBIT")
                        && movements.get(0).amount().compareTo(BigDecimal.valueOf(50000)) == 0
                        && movements.get(1).accountId().equals(account.getId())
                        && movements.get(1).direction().name().equals("CREDIT")
                        && movements.get(1).amount().compareTo(BigDecimal.valueOf(50000)) == 0));
    }

    @Test
    @DisplayName("RG-DEP-04 : compte FROZEN → ACCOUNT_NOT_ACTIVE — pas d'écriture")
    void deposit_on_frozen_account_is_rejected() {
        Account account = account(UUID.randomUUID(), AccountStatus.FROZEN);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.deposit(new DepositCommand(account.getId(), BigDecimal.valueOf(100), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE));
    }

    @Test
    @DisplayName("RG-DEP-03 : échelle XOF — 1,50 → INVALID_AMOUNT_SCALE — 400")
    void deposit_with_wrong_scale_is_rejected() {
        Account account = account(UUID.randomUUID(), AccountStatus.ACTIVE);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.deposit(new DepositCommand(account.getId(), new BigDecimal("1.50"), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.INVALID_AMOUNT_SCALE));
    }

    @Test
    @DisplayName("RG-DEP-03 : montant négatif/nul → VALIDATION_FAILED")
    void deposit_negative_or_zero_amount_is_rejected() {
        Account account = account(UUID.randomUUID(), AccountStatus.ACTIVE);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.deposit(new DepositCommand(account.getId(), BigDecimal.valueOf(0), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("§5 : compte introuvable → RESOURCE_NOT_FOUND")
    void deposit_on_unknown_account_is_404() {
        UUID unknownAccountId = UUID.randomUUID();
        when(accountRepository.findById(unknownAccountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit(new DepositCommand(unknownAccountId, BigDecimal.valueOf(100), null), TELLER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
