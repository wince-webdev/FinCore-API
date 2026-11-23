package com.wince.fincore.account.api;

import com.wince.fincore.account.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PATCH /api/v1/accounts/{id}/status} — F-03 (§8).
 *
 * <p>{@link #status()} obligatoire (Bean Validation).
 *
 * <p>{@code motif} obligatoire pour {@code FROZEN} et {@code CLOSED}
 * (F-13) — {@code AccountService.changeStatus} valide la clause de
 * motivation — pour le {@code ACTIVE} (dégel) — facultatif.
 *
 * <p>Motif dans la réponse ? — sans colonne en base (§6.1) — le motif est
 * un <em>input</em> de validation (historique RG-ACC-05) — F-13 (cahier)
 * le fige comme « motif obligatoire » — équivalent du motif dans
 * {@code ILLEGAL_STATE_TRANSITION} de F-09 — on ne stocke pas de deuxième
 * variété de motif en base — clarification pédagogique en entretien.
 */
public record AccountStatusUpdateRequest(
        @NotNull(message = "le statut est obligatoire") AccountStatus status,
        String motif) {
}
