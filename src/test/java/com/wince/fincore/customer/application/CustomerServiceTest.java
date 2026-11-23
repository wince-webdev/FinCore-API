package com.wince.fincore.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wince.fincore.customer.api.KycUpdateRequest;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.domain.IdDocType;
import com.wince.fincore.customer.domain.KycLevel;
import com.wince.fincore.customer.domain.KycStatus;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitaires de {@link CustomerService} — Phase 3, F-02.
 *
 * <p>Horloge figée à 2026-11-20 (§2.2 : jamais {@code Instant.now()} dans
 * les tests métier — on injecte {@code Clock.fixed}).</p>
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    /** « Ce jour » des assertions — défini à l'occasion du test suite. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ClockConfig.BANKING_ZONE);

    @Mock
    private CustomerRepository customerRepository;

    /** Créé dans {@code @BeforeEach} — après l'injection des mocks (NPE sinon). */
    private CustomerService service;

    /** Injection des mocks termine avant les {@code @BeforeEach} — le service voit les mocks. */
    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, FIXED_CLOCK);
    }

    // ── isAdult — RG-KYC-04 ──────────────────────────────────────────────

    @Test
    @DisplayName("isAdult : 18 ans le jour de son anniversaire → majeur (born 2008-11-20)")
    void adult_on_the_18th_birthday() {
        Customer adult = new Customer(UUID.randomUUID(), UUID.randomUUID(),
                "A", "B", LocalDate.of(2008, 11, 20), "0612345678");
        assertThat(adult.isAdult(TODAY)).isTrue();
    }

    @Test
    @DisplayName("isAdult : 17 ans (born 2009-05-10) → mineur ; 19 ans (born 2007-11-20) → majeur")
    void minor_at_17_and_adult_at_19() {
        Customer minor = new Customer(UUID.randomUUID(), UUID.randomUUID(),
                "A", "B", LocalDate.of(2009, 5, 10), "0612345678");
        Customer adult = new Customer(UUID.randomUUID(), UUID.randomUUID(),
                "A", "B", LocalDate.of(2007, 11, 20), "0612345678");
        assertThat(minor.isAdult(TODAY)).isFalse();
        assertThat(adult.isAdult(TODAY)).isTrue();
    }

    // ── Création — RG-KYC-01 ─────────────────────────────────────────────

    @Test
    @DisplayName("Création : client réservé en LEVEL_1 / PENDING (RG-KYC-01)")
    void creation_defaults_to_level_1_pending() {
        Customer customer = new Customer(UUID.randomUUID(), UUID.randomUUID(),
                "A", "B", LocalDate.of(2000, 1, 1), "0612345678");
        assertThat(customer.getKycLevel()).isEqualTo(KycLevel.LEVEL_1);
        assertThat(customer.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(customer.isAdult(TODAY)).isTrue();
    }

    // ── Montée de niveau — RG-KYC-04 (mineur) ────────────────────────────

    @Test
    @DisplayName("RG-KYC-04 : monter à LEVEL_2 en étant mineur → 422 CUSTOMER_UNDERAGE")
    void minor_cannot_be_escalated() {
        UUID customerId = UUID.randomUUID();
        Customer minor = new Customer(customerId, UUID.randomUUID(),
                "Jean", "Bon", LocalDate.of(2009, 5, 10), "0612345678");
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(minor));

        assertThatThrownBy(() -> service.updateKyc(customerId,
                        new KycUpdateRequest(KycLevel.LEVEL_2, IdDocType.ID_CARD, "0123456789")))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.CUSTOMER_UNDERAGE));
    }

    // ── Document obligatoire — RG-KYC-02 ─────────────────────────────────

    @Test
    @DisplayName("RG-KYC-02 : LEVEL_2 sans document → 400 VALIDATION_FAILED (errors.idDoc*)")
    void level_2_without_document_is_rejected() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, UUID.randomUUID(),
                "Jean", "Bon", LocalDate.of(2008, 5, 10), "0612345678");
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.updateKyc(customerId, new KycUpdateRequest(KycLevel.LEVEL_2, null, null)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ── Stockage masqué — RG-KYC-03 (CA-KYC-01) ──────────────────────────

    @Test
    @DisplayName("RG-KYC-03 : numéro haché, masqué ****6789 — jamais le numéro clair (CA-KYC-01)")
    void doc_is_masked_and_never_clear() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, UUID.randomUUID(),
                "Jean", "Bon", LocalDate.of(2008, 5, 10), "0612345678");
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Customer updated = service.updateKyc(customerId,
                new KycUpdateRequest(KycLevel.LEVEL_2, IdDocType.ID_CARD, "0123456789"));

        assertThat(updated.getKycLevel()).isEqualTo(KycLevel.LEVEL_2);
        assertThat(updated.getIdDocNumberMasked()).isEqualTo("****6789");
        assertThat(updated.getIdDocNumber()).isNotEqualTo("0123456789");
    }

    // ── 404 — §5 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("§5 : client inexistant → 404 RESOURCE_NOT_FOUND")
    void unknown_customer_is_404() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateKyc(customerId, new KycUpdateRequest(KycLevel.LEVEL_1, null, null)))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> assertThat(((FinCoreException) ex).errorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
