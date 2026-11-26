package com.wince.fincore.payment.api;

import com.wince.fincore.admin.application.AuditService;
import com.wince.fincore.payment.application.ReversalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "F-09 - contre-passation.")
public class ReversalController {
    private final ReversalService reversalService;
    private final AuditService auditService;

    public ReversalController(ReversalService reversalService, AuditService auditService) {
        this.reversalService = reversalService;
        this.auditService = auditService;
    }

    @PostMapping("/{id}/reversal")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Contre-passer une transaction")
    public ResponseEntity<TransactionResponse> reverse(@PathVariable UUID id,
                                                      @Valid @RequestBody ReversalCommand command,
                                                      HttpServletRequest request) {
        UUID userId = currentUserId();
        com.wince.fincore.ledger.domain.FinancialTransaction txn = reversalService.reverse(id, command.motif(), userId);
        auditService.log(userId, "ADMIN", "REVERSAL", "financial_transaction", id.toString(), request,
                "{\"motif\":\"" + command.motif() + "\"}");
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(txn));
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }
}
