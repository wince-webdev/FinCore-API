package com.wince.fincore.account.api;

import com.wince.fincore.account.application.AccountService;
import com.wince.fincore.account.domain.Account;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleurs « comptes » — catalogue §8 (F-03, Phase 4).
 *
 * <p>Délage : le contrôleur valide le format du corps (Bean Validation) et
 * la matrice de droits — les règles métier (RG-ACC-01 à 07) vivent dans
 * {@link AccountService} — équivalent {@code AccountController} (Phase 3,
 * F-02) / {@code AuthController} (Phase 2).
 *
 * <p>Matrice de droits (§5) :
 * <ul>
 *   <li>{@code POST /accounts} — TELLER, ADMIN — ouvrir un compte ;</li>
 *   <li>{@code GET /accounts} — CUSTOMER — « mes comptes » ;</li>
 *   <li>{@code GET /accounts/{id}} — CUSTOMER (ses comptes), ADMIN —
 *       404 pour un autre client — CA-ACC-02 ;</li>
 *   <li>{@code GET /accounts/lookup?number=} — CUSTOMER — pré-contrôle
 *       bénéficiaire (F-06) ;</li>
 *   <li>{@code PATCH /accounts/{id}/status} — ADMIN — gel/dégel/clôture —
 *       RG-ACC-04 (motif obligatoire — F-13).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Comptes", description = "F-03 — ouverture, lecture et statut des comptes.")
public class AccountController {

    private final AccountService accountService;
    private final CustomerRepository customerRepository;

    public AccountController(AccountService accountService, CustomerRepository customerRepository) {
        this.accountService = accountService;
        this.customerRepository = customerRepository;
    }

    /** {@code POST /api/v1/accounts} — ouvrir un compte — §5 (TELLER, ADMIN). */
    @PostMapping
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN')")
    @Operation(summary = "Ouvrir un compte",
            description = "§8 — F-03 — ouvrir un compte pour un client — §5 : TELLER, ADMIN — RG-ACC-01 à 07.")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        // Pattern AuthController (Phase 2) : POST crée une ressource → 201 Created.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AccountResponse.from(accountService.open(request)));
    }

    /** {@code GET /api/v1/accounts} — mes comptes — §5 (CUSTOMER). */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Mes comptes", description = "§8 — CUSTOMER — tous les comptes du client.")
    public List<AccountResponse> getMyAccounts() {
        return accountService.findByCustomerId(currentCustomerId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    /** {@code GET /api/v1/accounts/{id}} — détail — §5 (CUSTOMER, ADMIN). */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Détail d'un compte",
            description = "§8 — CUSTOMER (ses comptes) / ADMIN — 404 pour les autres — CA-ACC-02.")
    public AccountResponse getAccount(@PathVariable UUID id) {
        return AccountResponse.from(accountService.findById(id, currentRole(), currentCustomerId()));
    }

    /** {@code GET /api/v1/accounts/lookup?number=} — pré-contrôle bénéficiaire — §8 (CUSTOMER). */
    @GetMapping("/lookup")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Pré-contrôle bénéficiaire",
            description = "§8 / F-06 — renvoie le nom masqué du détenteur — RG-ACC-03 (CA-ACC-01).")
    public AccountLookupResponse lookupBeneficiary(@RequestParam(required = false) String number) {
        if (number == null || number.isBlank()) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
                    "Le paramètre « number » est obligatoire — RG-ACC-03.");
        }
        return accountService.lookupBeneficiary(number);
    }

    /** {@code PATCH /api/v1/accounts/{id}/status} — geler/dégeler/clôturer — §5 (ADMIN). */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Geler / dégeler / clôturer",
            description = "§8 — ADMIN — RG-ACC-04 (motif obligatoire — F-13) / RG-ACC-05.")
    public AccountResponse changeStatus(@PathVariable UUID id,
            @Valid @RequestBody AccountStatusUpdateRequest request) {
        return AccountResponse.from(accountService.changeStatus(id, request));
    }

    // ── Passerelles ──────────────────────────────────────────────────────

    /** Identifiant du client authentifié — claim {@code sub} du JWT (RG-AUTH-06). */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }

    /**
     * Rôle courant — claim {@code role} du JWT (RG-AUTH-06) — également la
     * source de vérité pour {@code hasRole(...)} via
     * {@code IdentitySecurityConfig} — simple {@code Role.valueOf(claim)}.
     */
    private Role currentRole() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Role.valueOf(jwt.getClaimAsString("role"));
    }

    /**
     * Id du {@code Customer} lié au client authentifié — {@code account.customer_id}
     * pointe vers {@code customer.id} (§6.1) — alors que {@code currentUserId()}
     * est l'id de {@code app_user} — la vérification §5/CA-ACC-02 compare
     * avec {@code customer.id} — le resolveur via {@code findByUserId}.
     *
     * <p>{@code null} si l'utilisateur authentifié n'a pas de {@code Customer}
     * (typiquement : un ADMIN — l'ADMIN peut lire n'importe quel compte —
     * §5 — la vérification ne passe pas par le customer).
     */
    private UUID currentCustomerId() {
        return customerRepository.findByUserId(currentUserId())
                .map(Customer::getId)
                .orElse(null);
    }
}
