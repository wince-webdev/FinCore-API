package com.wince.fincore.customer.application;

import com.wince.fincore.customer.api.KycUpdateRequest;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.domain.KycLevel;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage F-02 — clients et KYC (RG-KYC-01 à 05).
 *
 * <p><strong>Responsabilités :</strong>
 * <ul>
 *   <li>résoudre le client à partir de l'id de l'utilisateur (déjà lié à
 *       {@code customer.user_id}) — {@link #getProfile(UUID)} — {@code GET /me};</li>
 *   <li>appliquer la montée de niveau KYC — {@link #updateKyc(UUID, KycUpdateRequest)} —
 *       {@code PATCH /customers/{id}/kyc} (RG-KYC-02 à 05).</li>
 * </ul>
 *
 * <p><strong>Pourquoi ça vit ici et non dans le contrôleur ?</strong> Les
 * règles métier (majeure, document obligatoire au LEVEL_2, masquage) doivent
 * pouvoir s'exécuter sans servlet ni container — {@code @Transactional} sur
 * la couche application selon le cahier (§2.2), testable avec
 * {@code CustomerServiceTest} (Phase 3, unitaire).</p>
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final Clock clock;

    public CustomerService(CustomerRepository customerRepository, Clock clock) {
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    /** {@code GET /customers/me} — 404 pour un client introuvable (§5). */
    public Customer getProfile(UUID userId) {
        return customerRepository.findByUserId(userId).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Client introuvable."));
    }

    /**
     * {@code PATCH /customers/{id}/kyc} (RG-KYC-02 à 05) — orchestration
     * métier : majeure → document → stockage masqué.
     *
     * @return le client mis à jour — {@link CustomerResponse} construit par le
     *         contrôleur via {@link com.wince.fincore.customer.api.CustomerResponse#from(Customer)}
     */
    @Transactional
    public Customer updateKyc(UUID customerId, KycUpdateRequest request) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Client introuvable."));

        if (request.kycLevel() != null && request.kycLevel() != customer.getKycLevel()) {
            LocalDate today = LocalDate.ofInstant(clock.instant(), ClockConfig.BANKING_ZONE);
            if (!customer.isAdult(today)) {
                throw new FinCoreException(ErrorCode.CUSTOMER_UNDERAGE,
                        "Le client a moins de 18 ans à l'instant T et ne peut pas être monté au niveau "
                                + request.kycLevel() + ".");
            }
            if (request.kycLevel() != KycLevel.LEVEL_1
                    && (request.idDocType() == null || request.idDocNumber() == null)) {
                // RG-KYC-02 : passage en LEVEL_2 (doc) ou LEVEL_3 (doc + ADMIN).
                throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
                        "Le passage au niveau " + request.kycLevel()
                                + " requiert un type et un numéro d'identification renseignés.",
                        Map.of("errors", Map.of(
                                "idDocType", "obligatoire",
                                "idDocNumber", "obligatoire")));
            }
        }
        if (request.idDocType() != null) {
            customer.setIdDocType(request.idDocType());
        }
        if (request.idDocNumber() != null) {
            customer.maskIdDoc(request.idDocNumber());
        }
        if (request.kycLevel() != null) {
            customer.setKycLevel(request.kycLevel());
        }
        return customer;
    }
}
