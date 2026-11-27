package com.wince.fincore.compliance.api;

import com.wince.fincore.compliance.domain.AmlAlert;
import com.wince.fincore.compliance.repository.AmlAlertRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/alerts")
@Tag(name = "Conformite", description = "F-11 - alertes AML.")
public class ComplianceController {
    private final AmlAlertRepository repository;

    public ComplianceController(AmlAlertRepository repository) { this.repository = repository; }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    @Operation(summary = "Lister les alertes AML")
    public List<AmlAlert> list(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String ruleCode) {
        return repository.search(status, ruleCode);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Traiter une alerte")
    public ResponseEntity<AmlAlert> update(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        AmlAlert alert = repository.findById(id).orElseThrow();
        String status = body.get("status");
        if (status != null) alert.setStatus(status);
        if (body.get("comment") != null) {
            // store comment in details? append
        }
        if ("REVIEWED".equals(status) || "CLOSED".equals(status)) {
            // reviewedBy not resolved here; leave null
            alert.setReviewedAt(java.time.Instant.now());
        }
        return ResponseEntity.ok(repository.save(alert));
    }
}
