package com.wince.fincore.payment.api;

import com.wince.fincore.payment.application.ExternalTransferService;
import com.wince.fincore.payment.application.IdempotencyService;
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
@Tag(name = "Virements externes", description = "F-08 - asynchrone.")
public class ExternalTransferController {
    private final ExternalTransferService service;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public ExternalTransferController(ExternalTransferService service, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.service = service;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/external")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Virement externe asynchrone")
    public ResponseEntity<?> external(@Valid @RequestBody TransferCommand command,
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
        IdempotencyService.BeginResult begin = idempotencyService.begin(userId, "POST /api/v1/transfers/external", key.toString(), requestHash);
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
            com.wince.fincore.ledger.domain.FinancialTransaction txn = service.create(command, userId);
            TransactionResponse response = TransactionResponse.from(txn);
            String body = objectMapper.writeValueAsString(response);
            idempotencyService.complete(begin.record(), HttpStatus.CREATED.value(), body, txn.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
