package com.wince.fincore.customer.api;

import com.wince.fincore.customer.domain.IdDocType;
import com.wince.fincore.customer.domain.KycLevel;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PATCH /api/v1/customers/{id}/kyc} — F-02 / RG-KYC-02.
 *
 * <p>{@code kycLevel} est obligatoire (ce que le client est monté en).
 * {@code idDocType}/{@code idDocNumber} n'ont de sens qu'à {@code LEVEL_2}
 * et {@code LEVEL_3} (le service les requiert — CA-KYC-01 : le numéro
 * n'apparaît nulle part dans aucune réponse). {@code sourceRevenus} est
 * requis à {@code LEVEL_3} (« justificatif de domicile + source de revenus
 * déclarée », validée par un ADMIN — RG-KYC-02) — déclarée dans le DTO, la
 * règle de dépendance s'applique dans le service.</p>
 *
 * <p>Pourquoi pas d'annotation Bean Validation « LEVEL_2 ⇒ document ? » —
 * les « conditionnelles entre champs » appartiennent au service (la règle
 * fait intervenir l'âge du client — RG-KYC-04 — un être du domaine) et pas
 * au schéma du DTO. C'est là que se joue la différence architecture — le
 * DTO valide la <em>forme</em>, le service valide les <em>règles métier</em>.</p>
 */
public record KycUpdateRequest(
        @NotNull(message = "le niveau KYC est obligatoire") KycLevel kycLevel,
        IdDocType idDocType,
        String idDocNumber,
        String sourceRevenus) {
}
