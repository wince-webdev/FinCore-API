package com.wince.fincore.admin.api;

import com.wince.fincore.payment.domain.FxRate;
import com.wince.fincore.payment.repository.FxRateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/fx-rates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFxRateController {
    private final FxRateRepository repository;
    public AdminFxRateController(FxRateRepository repository) { this.repository = repository; }

    @GetMapping
    public List<FxRate> list() { return repository.findAll(); }

    @PostMapping
    public ResponseEntity<FxRate> create(@RequestBody FxRate rate) {
        if (rate.getId() == null) rate.setId(UUID.randomUUID());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rate));
    }
}
