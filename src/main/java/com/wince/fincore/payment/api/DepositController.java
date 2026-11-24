package com.wince.fincore.payment.api;

import com.wince.fincore.payment.application.DepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur — dépôts — catalogue §8 (F-05 — Phase 5 — TELLER).
 *
 * <p>{@code POST /api/v1/deposits} — {@code RG-DEP-01 à 05} — débit {@code CASH_VAULT} +
 * crédit compte client — 2 écritures — {@code CA-DEP-01}.
 */
@RestController
@RequestMapping("/api/v1/deposits")
@Tag(name = "Dépôts", description = "F-05 — §8 — dépôt par TELLER — RG-DEP-01 à 05.")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    /** {@code POST /api/v1/deposits} — F-05 — §8 — TELLER — RG-DEP-01 à 05. */
    @PostMapping
    @PreAuthorize("hasRole('TELLER')")
    @Operation(summary = "Déposer un montant",
            description = "§8 — F-05 — dépôt par TELLER — débit CASH_VAULT + crédit compte client — sans frais — RG-DEP-01 à 05.")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TransactionResponse.from(depositService.deposit(command, currentUserId())));
    }

    /** Identifiant de l'initiateur — claim {@code sub} du JWT — {@code initiated_by} (§6.1). */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }
}
