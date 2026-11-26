package com.wince.fincore.admin.api;

import com.wince.fincore.admin.domain.AuditLog;
import com.wince.fincore.admin.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Audit", description = "F-12 - journal d'audit.")
public class AuditController {
    private final AuditLogRepository repository;
    public AuditController(AuditLogRepository repository) { this.repository = repository; }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    @Operation(summary = "Consulter le journal d'audit")
    public ResponseEntity<Page<AuditLog>> list(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        java.util.UUID actor = (actorId == null || actorId.isBlank()) ? null : java.util.UUID.fromString(actorId);
        Instant f = (from == null || from.isBlank()) ? null : Instant.parse(from);
        Instant t = (to == null || to.isBlank()) ? null : Instant.parse(to);
        return ResponseEntity.ok(repository.search(actor, action, f, t, PageRequest.of(page, size)));
    }
}
