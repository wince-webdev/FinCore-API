package com.wince.fincore.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.LedgerEntry;
import com.wince.fincore.ledger.domain.TransactionStatus;
import com.wince.fincore.ledger.domain.TransactionType;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.ledger.repository.LedgerEntryRepository;
import com.wince.fincore.shared.domain.Currency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.domain.Money;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitaires de {@link LedgerService} — Phase 5, F-04 — RG-LED-01 à 07.
 *
 * <p>Horloge figée à 2026-11-23 (§2.2 — jamais {@code Instant.now()} dans les tests métier).
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final UUID TELLER_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-11-23T10:00:00Z");

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;

    private LedgerService service;

    @BeforeEach
    void setUp() {
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.getSingleResult()).thenReturn(1);
        service = new LedgerService(ledgerEntryRepository, accountRepository, financialTransactionRepository,
                Clock.fixed(CREATED_AT, ClockConfig.BANKING_ZONE), entityManager, null);
    }

    private static Account account(UUID id, BigDecimal balance) {
        return new Account(id, "BJ123456789012", UUID.randomUUID(), AccountKind.CURRENT, null, Currency.XOF, balance);
    }

    // ── apply — RG-LED-01/02/04/06/07 — postEntries — 2 écritures ──────

    @Test
    @DisplayName("RG-LED-01/02/04/07 : apply — Σ DEBIT = Σ CREDIT — balance_after — MAJ comptes — RG-LED-06")
    void apply_posts_entries_and_updates_balances() {
        Account account = account(UUID.randomUUID(), BigDecimal.ZERO);
        Account cashVault = account(UUID.randomUUID(), BigDecimal.ZERO);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.findById(cashVault.getId())).thenReturn(Optional.of(cashVault));
        when(ledgerEntryRepository.findTop1ByAccountIdOrderByIdDesc(account.getId())).thenReturn(Optional.empty());
        when(ledgerEntryRepository.findTop1ByAccountIdOrderByIdDesc(cashVault.getId())).thenReturn(Optional.empty());
        FinancialTransaction transaction = new FinancialTransaction(UUID.randomUUID(), "TXN-20261130-000001",
                TransactionType.DEPOSIT, cashVault.getId(), account.getId(), TELLER_ID,
                Money.of(BigDecimal.valueOf(50000), Currency.XOF).amount(), Currency.XOF, BigDecimal.ZERO, null);

        service.apply(transaction, List.of(
                new LedgerMovement(cashVault.getId(), Direction.DEBIT, BigDecimal.valueOf(50000), Currency.XOF),
                new LedgerMovement(account.getId(), Direction.CREDIT, BigDecimal.valueOf(50000), Currency.XOF)));

        // RG-LED-07 — dénormalisation — MAJ dans la transaction — solde = Σ crédits − Σ débits.
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(cashVault.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(-50000));
        // RG-LED-01 — écritures sauvegardées — 2 — via List.of — snapshot.
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(captor.capture());
        List<LedgerEntry> entries = captor.getAllValues();
        assertThat(entries).hasSize(2);
        // RG-LED-04 — balance_after — solde juste après l'écriture.
        assertThat(entries.stream().map(LedgerEntry::getBalanceAfter))
                .containsExactlyInAnyOrder(BigDecimal.valueOf(50000), BigDecimal.valueOf(-50000));
        // RG-LED-05 — chaîne — prev_hash = ZERO_HASH pour les premières entrées.
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.getEntryHash()).hasSize(64).matches("[0-9a-f]{64}");
            assertThat(entry.getPrevHash()).isEqualTo(LedgerService.ZERO_HASH);
        });
        // RG-LED-04/07 — et la transaction est enregistrée — status — completed_at — (vérifié dans VerifyEndpointTest avec la base réelle).
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("RG-LED-02 : Σ DEBIT ≠ Σ CREDIT → LEDGER_UNBALANCED — avant tout INSERT")
    void apply_rejects_unbalanced_movements() {
        Account account = account(UUID.randomUUID(), BigDecimal.ZERO);

        assertThatThrownBy(() -> service.apply(new FinancialTransaction(UUID.randomUUID(), "TXN-20261130-000002",
                TransactionType.DEPOSIT, null, account.getId(), TELLER_ID, BigDecimal.TEN, Currency.XOF, BigDecimal.ZERO, null),
                List.of(
                        new LedgerMovement(account.getId(), Direction.DEBIT, BigDecimal.TEN, Currency.XOF),
                        new LedgerMovement(account.getId(), Direction.CREDIT, BigDecimal.valueOf(9), Currency.XOF))))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.LEDGER_UNBALANCED));
        verifyNoInteractions(ledgerEntryRepository);
    }

    // ── verify — CA-LED-03 — préservation du chaînage ──────────────────

    @Test
    @DisplayName("CA-LED-03 : verify() — ok=true sur une chaîne cohérente —RG-LED-05/07")
    void verify_ok_on_consistent_chain() {
        Account account = account(UUID.randomUUID(), BigDecimal.valueOf(50000));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        UUID transactionId = UUID.randomUUID();
        String hash1 = LedgerService.computeHash(LedgerService.ZERO_HASH, transactionId, account.getId(),
                Direction.CREDIT, BigDecimal.valueOf(50000), Currency.XOF, CREATED_AT);
        LedgerEntry entry = new LedgerEntry(transactionId, account.getId(), Direction.CREDIT,
                BigDecimal.valueOf(50000), Currency.XOF, BigDecimal.valueOf(50000), LedgerService.ZERO_HASH, hash1);
        entry.setCreatedAt(CREATED_AT);
        when(ledgerEntryRepository.findByAccountIdOrderById(account.getId())).thenReturn(List.of(entry));

        LedgerService.VerificationResult result = service.verify(account.getId());

        assertThat(result.ok()).isTrue();
        assertThat(result.errors()).isEmpty();
        // RG-LED-07 — solde dénormalisé — cohérent — ok.
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("CA-LED-03 : verify() — ok=false + erreur — altération du hash — simulation (INSERT)")
    void verify_detects_tampered_entry_hash() {
        Account account = account(UUID.randomUUID(), BigDecimal.ZERO);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        UUID transactionId = UUID.randomUUID();
        LedgerEntry tampered = new LedgerEntry(transactionId, account.getId(), Direction.CREDIT,
                BigDecimal.valueOf(50000), Currency.XOF, BigDecimal.valueOf(50000), LedgerService.ZERO_HASH,
                "0".repeat(64));  // hash ≠ hash calculé
        tampered.setCreatedAt(CREATED_AT);
        when(ledgerEntryRepository.findByAccountIdOrderById(account.getId())).thenReturn(List.of(tampered));

        LedgerService.VerificationResult result = service.verify(account.getId());

        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("chaîne de hachages cassée"));
    }

    @Test
    @DisplayName("CA-LED-03 : verify() — ok=false — solde dénormalisé (RG-LED-07)")
    void verify_detects_unbalanced_solde() {
        Account account = account(UUID.randomUUID(), BigDecimal.valueOf(100));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        UUID transactionId = UUID.randomUUID();
        String hash1 = LedgerService.computeHash(LedgerService.ZERO_HASH, transactionId, account.getId(),
                Direction.DEBIT, BigDecimal.valueOf(10), Currency.XOF, CREATED_AT);
        LedgerEntry entry = new LedgerEntry(transactionId, account.getId(), Direction.DEBIT,
                BigDecimal.valueOf(10), Currency.XOF, BigDecimal.valueOf(-10), LedgerService.ZERO_HASH, hash1);
        entry.setCreatedAt(CREATED_AT);
        when(ledgerEntryRepository.findByAccountIdOrderById(account.getId())).thenReturn(List.of(entry));

        LedgerService.VerificationResult result = service.verify(account.getId());

        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("Solde dénormalisé"));
    }
}
