package com.wince.fincore.payment.api;

import com.wince.fincore.payment.application.FxTransferService;
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
@Tag(name = "Change", description = "F-07 - virements avec change.")
public class FxTransferController {
    private final FxTransferService fxTransferService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public FxTransferController(FxTransferService fxTransferService, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.fxTransferService = fxTransferService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/fx/quote")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Devis de change")
    public ResponseEntity<FxQuoteResponse> quote(@Valid @RequestBody TransferCommand command) {
        UUID userId = currentUserId();
        FxTransferService.FxResult result = fxTransferService.quote(command, userId);
        return ResponseEntity.ok(new FxQuoteResponse(
                command.amount(), command.currency(), result.convertedAmount(),
                result.destinationCurrency(), result.rate(), result.spreadPercent(), result.fee(), command.amount()));
    }

    @PostMapping("/fx")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Virement avec change")
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
        IdempotencyService.BeginResult begin = idempotencyService.begin(userId, "POST /api/v1/transfers/fx", key.toString(), requestHash);
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
            FxTransferService.FxResult result = fxTransferService.transfer(command, userId);
            FxTransferResponse response = new FxTransferResponse(
                    result.transaction().getId(), result.transaction().getReference(),
                    result.transaction().getAmount(), result.transaction().getCurrency(),
                    result.convertedAmount(), result.destinationCurrency(),
                    result.rate(), result.spreadPercent(), result.fee(),
                    result.sourceBalanceAfter(), result.destinationBalanceAfter(), result.transaction().getCreatedAt());
            // fix destination currency
            // We need destination currency from account; but not in result. Use command? command has single currency (source). 
            String body = objectMapper.writeValueAsString(response);
            idempotencyService.complete(begin.record(), HttpStatus.CREATED.value(), body, result.transaction().getId());
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
