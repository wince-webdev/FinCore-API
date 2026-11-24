package com.wince.fincore.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wince.fincore.account.api.AccountLookupResponse;
import com.wince.fincore.account.api.AccountStatusUpdateRequest;
import com.wince.fincore.account.api.OpenAccountRequest;
import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.domain.KycStatus;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * Tests unitaires de {@link AccountService} — Phase 4, F-03.
 *
 * <p>Horloge figée à 2026-11-23 (§2.2 : jamais {@code Instant.now()} dans les
 * tests métier — on injecte {@code Clock.fixed(...)}).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, customerRepository,
                Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ClockConfig.BANKING_ZONE));
    }

    private Customer customer(KycStatus kycStatus) {
        Customer customer = new Customer(CUSTOMER_ID, UUID.randomUUID(),
                "Marie", "Dupont", LocalDate.of(1990, 1, 5), "0612345678");
        customer.setKycStatus(kycStatus);
        return customer;
    }

    /** Compte client « ouvert » — ACTIVE, solde zéro — RG-ACC-01. */
    private static Account clientAccount() {
        return new Account(UUID.randomUUID(), AccountNumberGenerator.generate(),
                CUSTOMER_ID, AccountKind.CURRENT, null, Currency.XOF, BigDecimal.ZERO);
    }

    /** Compte système (caisse) — RG-ACC-06. */
    private static Account systemAccount() {
        return new Account(UUID.randomUUID(), AccountNumberGenerator.generate(),
                null, AccountKind.SYSTEM, SystemCode.CASH_VAULT, Currency.XOF, BigDecimal.ZERO);
    }

    // ── open — RG-ACC-01 à 07 ────────────────────────────────────────────

    @Test
    @DisplayName("RG-ACC-01/02 : ouverture — ACTIVE, solde 0, devise demandée, numéro avec clé mod 97")
    void open_creates_client_account_active_zero_balance() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer(KycStatus.VERIFIED)));
        when(accountRepository.findByCustomerIdAndKindAndCurrency(CUSTOMER_ID, AccountKind.CURRENT, Currency.EUR))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account account = service.open(new OpenAccountRequest(CUSTOMER_ID, AccountKind.CURRENT, Currency.EUR));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(account.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(account.isSystem()).isFalse();
        assertThat(AccountNumberGenerator.isValid(account.getAccountNumber())).isTrue();
    }

    @Test
    @DisplayName("RG-ACC-01 : XOF est la devise par défaut quand le DTO ne le fournit pas")
    void open_defaults_to_XOF_when_currency_absent() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer(KycStatus.VERIFIED)));
        when(accountRepository.findByCustomerIdAndKindAndCurrency(CUSTOMER_ID, AccountKind.WALLET, Currency.XOF))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account account = service.open(new OpenAccountRequest(CUSTOMER_ID, AccountKind.WALLET, null));

        assertThat(account.getCurrency()).isEqualTo(Currency.XOF);
    }

    @Test
    @DisplayName("RG-ACC-02 : client rejeté par le KYC → VALIDATION_FAILED")
    void open_rejects_customer_rejected_by_kyc() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer(KycStatus.REJECTED)));

        assertThatThrownBy(() -> service.open(new OpenAccountRequest(CUSTOMER_ID, AccountKind.CURRENT, Currency.XOF)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("RG-ACC-02 : un seul compte par couple (type, devise) → VALIDATION_FAILED")
    void open_rejects_duplicate_type_and_currency() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer(KycStatus.VERIFIED)));
        when(accountRepository.findByCustomerIdAndKindAndCurrency(CUSTOMER_ID, AccountKind.CURRENT, Currency.XOF))
                .thenReturn(Optional.of(clientAccount()));

        assertThatThrownBy(() -> service.open(new OpenAccountRequest(CUSTOMER_ID, AccountKind.CURRENT, Currency.XOF)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("RG-ACC-06 : SYSTEM via l'API → VALIDATION_FAILED (créé au démarrage)")
    void open_rejects_system_kind() {
        assertThatThrownBy(() -> service.open(new OpenAccountRequest(CUSTOMER_ID, AccountKind.SYSTEM, Currency.XOF)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("§5 : client introuvable → RESOURCE_NOT_FOUND")
    void open_unknown_customer_is_404() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(new OpenAccountRequest(CUSTOMER_ID, AccountKind.CURRENT, Currency.XOF)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("§8 : findByCustomerId — « mes comptes » (GET /accounts)")
    void find_by_customer_id_returns_own_accounts() {
        Account own = clientAccount();
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(own));

        assertThat(service.findByCustomerId(CUSTOMER_ID)).containsExactly(own);
    }

    // ── findById — §5 — CA-ACC-02 (404 pour les autres) ───────────────────

    @Test
    @DisplayName("CA-ACC-02 : CUSTOMER ne lit que ses comptes — autre client → 404")
    void customer_reads_only_own_account() {
        Account foreign = new Account(UUID.randomUUID(), AccountNumberGenerator.generate(),
                UUID.randomUUID(), AccountKind.CURRENT, null, Currency.XOF, BigDecimal.ZERO);
        when(accountRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.findById(foreign.getId(), Role.CUSTOMER, CUSTOMER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("§5 : CUSTOMER ne lit pas un compte système → 404")
    void customer_cannot_read_system_account() {
        Account system = systemAccount();
        when(accountRepository.findById(system.getId())).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.findById(system.getId(), Role.CUSTOMER, CUSTOMER_ID))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("§5 / §8 : ADMIN peut lire n'importe quel compte — détail — GET /accounts/{id}")
    void admin_reads_any_account() {
        Account foreign = new Account(UUID.randomUUID(), AccountNumberGenerator.generate(),
                UUID.randomUUID(), AccountKind.CURRENT, null, Currency.XOF, BigDecimal.ZERO);
        when(accountRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThat(service.findById(foreign.getId(), Role.ADMIN, CUSTOMER_ID)).isEqualTo(foreign);
    }

    // ── changeStatus — RG-ACC-04/05, F-13 ────────────────────────────────

    @Test
    @DisplayName("§8/RG-ACC-04 : gèle un compte — statut FROZEN + écrit en base")
    void admin_freezes_account() {
        Account account = clientAccount();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.changeStatus(account.getId(),
                new AccountStatusUpdateRequest(AccountStatus.FROZEN, "Solde investigé"));

        assertThat(result.getStatus()).isEqualTo(AccountStatus.FROZEN);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("§5/§8 : ADMIN gèle un compte — motif obligatoire (F-13)")
    void admin_freeze_without_motif_is_400() {
        Account account = clientAccount();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changeStatus(account.getId(),
                        new AccountStatusUpdateRequest(AccountStatus.FROZEN, null)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("§5/§8 : ADMIN clôture un compte — solde à zéro obligatoire (RG-ACC-05)")
    void admin_close_requires_zero_balance() {
        Account account = clientAccount();
        account.setBalance(BigDecimal.valueOf(10));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changeStatus(account.getId(),
                        new AccountStatusUpdateRequest(AccountStatus.CLOSED, "Liquidation")))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    @Test
    @DisplayName("§5/§8 : ADMIN clôture — closed_at positionné, statut CLOSED")
    void admin_close_sets_closed_at() {
        Account account = clientAccount();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.changeStatus(account.getId(),
                new AccountStatusUpdateRequest(AccountStatus.CLOSED, "Liquidation"));

        assertThat(result.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(result.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("§5/§8 : ADMIN dégel — FROZEN → ACTIVE")
    void admin_unfreeze_to_active() {
        Account account = clientAccount();
        account.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.changeStatus(account.getId(),
                new AccountStatusUpdateRequest(AccountStatus.ACTIVE, null));

        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("§5/§8 : depuis CLOSED — plus aucune transition (RG-ACC-04)")
    void admin_cannot_transition_from_closed() {
        Account account = clientAccount();
        account.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changeStatus(account.getId(),
                        new AccountStatusUpdateRequest(AccountStatus.ACTIVE, null)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    @Test
    @DisplayName("RG-ACC-06 : statut des comptes systèmes — ILLEGAL_STATE_TRANSITION")
    void admin_cannot_change_system_account() {
        Account system = systemAccount();
        when(accountRepository.findById(system.getId())).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.changeStatus(system.getId(),
                        new AccountStatusUpdateRequest(AccountStatus.FROZEN, "motif")))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    // ── Pré-contrôle bénéficiaire — §8 / F-02 / F-06 ─────────────────────

    @Test
    @DisplayName("CA-ACC-01 : numéro invalide → INVALID_ACCOUNT_NUMBER (RG-ACC-03)")
    void lookup_rejects_invalid_number() {
        String number = AccountNumberGenerator.generate();
        String corrupted = number.substring(0, number.length() - 1)
                + (number.charAt(number.length() - 1) == '0' ? '9' : '0');

        assertThatThrownBy(() -> service.lookupBeneficiary(corrupted))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.INVALID_ACCOUNT_NUMBER));
    }

    @Test
    @DisplayName("§8 / F-06 : pré-contrôle — nom masqué « J*** D*** » (CA-KYC-01)")
    void lookup_returns_masked_name() {
        Account account = clientAccount();
        when(accountRepository.findByAccountNumber(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
        when(customerRepository.findById(account.getCustomerId()))
                .thenReturn(Optional.of(customer(KycStatus.VERIFIED)));

        AccountLookupResponse response = service.lookupBeneficiary(account.getAccountNumber());

        assertThat(response.maskedName()).isEqualTo("M*** D***");
    }
}
