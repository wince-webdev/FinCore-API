package com.wince.fincore.payment.api;

import com.wince.fincore.payment.application.WithdrawalService;
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
 * Contrôleur — retraits — catalogue §8 (F-05 — Phase 5 — TELLER).
 *
 * <p>{@code POST /api/v1/withdrawals} — {@code RG-DEP-02} — débit compte client
 * (montant + frais) + crédit {@code CASH_VAULT} + {@code FEE_REVENUE} — {@code CA-DEP-02}.
 */
@RestController
@RequestMapping("/api/v1/withdrawals")
@Tag(name = "Retraits", description = "F-05 — §8 — retrait par TELLER — RG-DEP-02.")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    /** {@code POST /api/v1/withdrawals} — F-05 — §8 — TELLER — RG-DEP-02. */
    @PostMapping
    @PreAuthorize("hasRole('TELLER')")
    @Operation(summary = "Retirer un montant",
            description = "§8 — F-05 — retrait par TELLER — débit compte client + frais — crédit CASH_VAULT — FEE_REVENUE — INSUFFICIENT_FUNDS — CA-DEP-02.")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawalCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TransactionResponse.from(withdrawalService.withdraw(command, currentUserId())));
    }

    /** Identifiant de l'initiateur — claim {@code sub} du JWT — {@code initiated_by} (§6.1). */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }
}
