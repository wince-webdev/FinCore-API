package com.wince.fincore.admin.api;

import com.wince.fincore.payment.domain.FeeRule;
import com.wince.fincore.payment.repository.FeeRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/fee-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeeRuleController {
    private final FeeRuleRepository repository;
    public AdminFeeRuleController(FeeRuleRepository repository) { this.repository = repository; }

    @GetMapping
    public List<FeeRule> list() { return repository.findAll(); }

    @PostMapping
    public ResponseEntity<FeeRule> create(@RequestBody FeeRule rule) {
        if (rule.getId() == null) rule.setId(UUID.randomUUID());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rule));
    }

    @PutMapping("/{id}")
    public FeeRule update(@PathVariable UUID id, @RequestBody FeeRule rule) {
        rule.setId(id);
        return repository.save(rule);
    }
}
