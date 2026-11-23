package com.wince.fincore.customer.api;

import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.domain.IdDocType;
import com.wince.fincore.customer.domain.KycLevel;
import com.wince.fincore.customer.domain.KycStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de réponse — {@code GET /customers/me} et {@code PATCH /customers/{id}/kyc}.
 *
 * <p><strong>Règle d'or (CA-KYC-01, cahier §6.1)</strong> : le numéro de pièce
 * existe <em>uniquement</em> sous sa forme masquée ({@code ****6789}). Le
 * numéro clair ne figure nulle part dans la réponse — par construction : le
 * {@code Customer} ne le stocke jamais (RG-KYC-03) et le projete ici, dans
 * {@link #from(Customer)}, sous {@code idDocNumberMasked}.</p>
 *
 * <p>Record = classe valeur immuable sans boilerplate — {@code equals}/
 * {@code hashCode}/{@code toString} générés par Java.</p>
 */
public record CustomerResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phone,
        IdDocType idDocType,
        String idDocNumberMasked,
        KycLevel kycLevel,
        KycStatus kycStatus,
        Instant createdAt) {

    /** Projet du client en sécurité — jamais de numéro de pièce en clair. */
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getUserId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getBirthDate(),
                customer.getPhone(),
                customer.getIdDocType(),
                customer.getIdDocNumberMasked(),
                customer.getKycLevel(),
                customer.getKycStatus(),
                customer.getCreatedAt());
    }
}
