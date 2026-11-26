package com.wince.fincore.admin.api;

import com.wince.fincore.payment.domain.LimitRule;
import com.wince.fincore.payment.repository.LimitRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/limit-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLimitRuleController {
    private final LimitRuleRepository repository;
    public AdminLimitRuleController(LimitRuleRepository repository) { this.repository = repository; }

    @GetMapping
    public List<LimitRule> list() { return repository.findAll(); }

    @PostMapping
    public ResponseEntity<LimitRule> create(@RequestBody LimitRule rule) {
        if (rule.getId() == null) rule.setId(UUID.randomUUID());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rule));
    }

    @PutMapping("/{id}")
    public LimitRule update(@PathVariable UUID id, @RequestBody LimitRule rule) {
        rule.setId(id);
        return repository.save(rule);
    }
}
