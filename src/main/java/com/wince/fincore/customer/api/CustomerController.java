package com.wince.fincore.customer.api;

import com.wince.fincore.customer.application.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleurs « client » — catalogue §8 (Phase 3, F-02).
 *
 * <ul>
 *   <li>{@code GET /api/v1/customers/me} — profil du client authentifié,
 *       matrice §5 (CUSTOMER) ;</li>
 *   <li>{@code PATCH /api/v1/customers/{id}/kyc} — carte KYC, matrice §5
 *       (TELLER, ADMIN).</li>
 * </ul>
 *
 * <p>Comme pour {@code AuthController} : le contrôleur valide le corps et
 * délage — la règle métier vit dans {@link CustomerService}. La projection en
 * {@link CustomerResponse} garantit que le numéro de pièce ne sort jamais en
 * clair (CA-KYC-01).</p>
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Clients", description = "F-02 — profil client et carte KYC.")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** {@code GET /api/v1/customers/me} — matrice §5 (CUSTOMER). */
    @GetMapping("/me")
    @Operation(summary = "Mon profil client",
            description = "Profil du client authentifié — matrice §5 (CUSTOMER).")
    public CustomerResponse getMyProfile() {
        return CustomerResponse.from(customerService.getProfile(currentUserId()));
    }

    /** {@code PATCH /api/v1/customers/{id}/kyc} — matrice §5 (TELLER, ADMIN). */
    @PatchMapping("/{id}/kyc")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN')")
    @Operation(summary = "Carte KYC",
            description = "Monter de niveau (RG-KYC-02) — matrice §5 (TELLER, ADMIN).")
    public CustomerResponse updateKyc(
            @PathVariable UUID id,
            @Valid @RequestBody KycUpdateRequest request) {
        return CustomerResponse.from(customerService.updateKyc(id, request));
    }

    /** Identifiant du client authentifié — claim {@code sub} du JWT (RG-AUTH-06). */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }
}
