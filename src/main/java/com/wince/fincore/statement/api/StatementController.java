package com.wince.fincore.statement.api;

import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.statement.application.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Releves", description = "F-10 - releves.")
public class StatementController {
    private final StatementService statementService;
    private final CustomerRepository customerRepository;

    public StatementController(StatementService statementService, CustomerRepository customerRepository) {
        this.statementService = statementService;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/{id}/statement")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Releve JSON")
    public ResponseEntity<StatementService.Statement> statement(@PathVariable UUID id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Instant f = parse(from); Instant t = parse(to);
        return ResponseEntity.ok(statementService.statement(id, currentRole(), currentCustomerId(), f, t, page, size));
    }

    @GetMapping("/{id}/statement.csv")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Releve CSV")
    public ResponseEntity<byte[]> csv(@PathVariable UUID id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant f = parse(from); Instant t = parse(to);
        StatementService.Statement st = statementService.statement(id, currentRole(), currentCustomerId(), f, t, 0, 100000);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM
        sb.append("Date;Reference;Libelle;Debit;Credit;Solde\n");
        for (StatementService.StatementLine line : st.movements()) {
            sb.append(line.date()).append(';')
              .append(line.reference()).append(';')
              .append(line.description()).append(';')
              .append(line.debit()).append(';')
              .append(line.credit()).append(';')
              .append(line.balanceAfter()).append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/{id}/statement.pdf")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Releve PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant f = parse(from); Instant t = parse(to);
        StatementService.Statement st = statementService.statement(id, currentRole(), currentCustomerId(), f, t, 0, 100000);
        byte[] bytes = PdfStatementGenerator.generate(st);
        String hash = sha256(bytes);
        // append hash? We'll embed in PDF already. 
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private Instant parse(String s) {
        if (s == null || s.isBlank()) return null;
        return Instant.parse(s);
    }

    private Role currentRole() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Role.valueOf(jwt.getClaimAsString("role"));
    }

    private UUID currentCustomerId() {
        return customerRepository.findByUserId(UUID.fromString(((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getSubject()))
                .map(com.wince.fincore.customer.domain.Customer::getId).orElse(null);
    }

    private String sha256(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) { return ""; }
    }
}
