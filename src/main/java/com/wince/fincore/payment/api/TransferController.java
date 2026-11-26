package com.wince.fincore.payment.api;

import com.wince.fincore.payment.application.IdempotencyService;
import com.wince.fincore.payment.application.TransferService;
import tools.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Virements", description = "F-06 - virements internes.")
public class TransferController {
    private final TransferService transferService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final com.wince.fincore.admin.application.AuditService auditService;

    public TransferController(TransferService transferService, IdempotencyService idempotencyService, ObjectMapper objectMapper,
                             com.wince.fincore.admin.application.AuditService auditService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @PostMapping("/quote")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Devis de virement")
    public ResponseEntity<QuoteResponse> quote(@Valid @RequestBody TransferCommand command) {
        UUID userId = currentUserId();
        TransferService.TransferResult result = transferService.quote(command, userId);
        return ResponseEntity.ok(new QuoteResponse(
                command.amount(), result.fee(), result.totalDebited(), command.currency(),
                result.remainingDailyLimit(), result.remainingDailyLimit(), result.sourceBalanceAfter()));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Virement interne")
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferCommand command,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String keyHeader) {
        if (keyHeader == null || keyHeader.isBlank()) {
            throw new com.wince.fincore.shared.domain.FinCoreException(
                    com.wince.fincore.shared.domain.ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "L'en-tete Idempotency-Key est obligatoire.");
        }
        UUID key;
        try { key = UUID.fromString(keyHeader); } catch (IllegalArgumentException e) {
            throw new com.wince.fincore.shared.domain.FinCoreException(
                    com.wince.fincore.shared.domain.ErrorCode.VALIDATION_FAILED, "Idempotency-Key invalide.");
        }
        UUID userId = currentUserId();
        String requestHash = idempotencyService.hashRequest(command);
        IdempotencyService.BeginResult begin = idempotencyService.begin(userId, "POST /api/v1/transfers", key.toString(), requestHash);
        if (begin.reused()) {
            throw new com.wince.fincore.shared.domain.FinCoreException(
                    com.wince.fincore.shared.domain.ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "La cle d'idempotence est reutilisee avec un corps different.");
        }
        if (begin.conflict()) {
            throw new com.wince.fincore.shared.domain.FinCoreException(
                    com.wince.fincore.shared.domain.ErrorCode.REQUEST_IN_PROGRESS,
                    "Requete en cours de traitement.");
        }
        if (begin.replay()) {
            return ResponseEntity.status(begin.replayStatus())
                    .header("Idempotent-Replayed", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(begin.replayBody());
        }
        try {
            TransferService.TransferResult result = transferService.transfer(command, userId);
            TransferResponse response = TransferResponse.from(result.transaction(),
                    result.sourceBalanceAfter(), result.destinationBalanceAfter(), result.fee(), result.totalDebited());
            String body = objectMapper.writeValueAsString(response);
            idempotencyService.complete(begin.record(), HttpStatus.CREATED.value(), body, result.transaction().getId());
            auditService.log(userId, "CUSTOMER", "TRANSFER", "financial_transaction", result.transaction().getId().toString(), null, null);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);
        } catch (RuntimeException e) {
            idempotencyService.fail(begin.record());
            throw e;
        } catch (Exception e) {
            idempotencyService.fail(begin.record());
            throw new RuntimeException(e);
        }
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }
}
