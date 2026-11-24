package com.wince.fincore.ledger.api;

import com.wince.fincore.ledger.application.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur — vérification du grand livre — catalogue §8 (F-04 — Phase 5).
 *
 * <p>{@code GET /api/v1/admin/ledger/verify?accountId=} — §8 — ADMIN + AUDITOR —
 * {@code ok = false + errors} si altération — {@code CA-LED-03} — vérifications du
 * chaînage de hachages (RG-LED-05) + solde dénormalisé (RG-LED-07).
 */
@RestController
@RequestMapping("/api/v1/admin/ledger/verify")
@Tag(name = "Grand livre — vérification", description = "F-04 — §8 — ADMIN/AUDITOR — CA-LED-03.")
public class LedgerVerificationController {

    private final LedgerService ledgerService;

    public LedgerVerificationController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** {@code GET /api/v1/admin/ledger/verify?accountId=} — F-04 — §8 — CA-LED-03. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    @Operation(summary = "Vérification du chaînage de hachages",
            description = "§8 — F-04 — recompte la chaîne de hachages + le solde — ok=false + errors si altération.")
    public LedgerService.VerificationResult verify(@RequestParam UUID accountId) {
        return ledgerService.verify(accountId);
    }
}
