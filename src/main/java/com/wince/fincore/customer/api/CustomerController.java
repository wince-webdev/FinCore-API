package com.wince.fincore.customer.api;

import com.wince.fincore.customer.application.CustomerService;
import com.wince.fincore.customer.domain.KycLevel;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
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
            description = "Monter de niveau (RG-KYC-02) — matrice §5 (TELLER, ADMIN), "
                    + "exception : LEVEL_3 — ADMIN seul.")
    public CustomerResponse updateKyc(
            @PathVariable UUID id,
            @Valid @RequestBody KycUpdateRequest request) {
        // RG-KYC-02 : « LEVEL_3 (justificatif de domicile + source de revenus
        // déclarée) est validé par un ADMIN » — le TELLER est validé à
        // LEVEL_2 seul, l'escalade à LEVEL_3 demande le rôle ADMIN.
        if (request.kycLevel() == KycLevel.LEVEL_3 && !hasAuthority(Role.ADMIN)) {
            throw new FinCoreException(ErrorCode.ACCESS_DENIED,
                    "Seul un ADMIN peut atteindre le LEVEL_3 (RG-KYC-02).");
        }
        return CustomerResponse.from(customerService.updateKyc(id, request));
    }

    /** Identifiant du client authentifié — claim {@code sub} du JWT (RG-AUTH-06). */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }

    /** Autorité courante du client authentifié (préfixe `ROLE_` — claim `role` du JWT, voir `IdentitySecurityConfig`). */
    private boolean hasAuthority(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }
}
