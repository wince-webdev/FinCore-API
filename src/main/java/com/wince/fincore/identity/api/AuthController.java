package com.wince.fincore.identity.api;

import com.wince.fincore.identity.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publics de session (catalogue §8) :
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/register} — public ;</li>
 *   <li>{@code POST /api/v1/auth/login} — public ;</li>
 *   <li>{@code POST /api/v1/auth/refresh} — public (le refresh token <em>est</em>
 *       la preuve) ;</li>
 *   <li>{@code POST /api/v1/auth/logout} — tout rôle authentifié.</li>
 * </ul>
 *
 * <p>Le contrôleur ne fait que valider le corps et déléguer : toute la
 * règle métier vit dans {@code AuthService}, testable sans servlet ni
 * conteneur HTTP.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification", description = "F-01 — inscription, connexion, rafraîchissement, déconnexion.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Inscription d'un client",
            description = "Crée un utilisateur CUSTOMER et retourne immédiatement les deux jetons.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Connexion",
            description = "Vérifie les identifiants et retourne l'access token (15 min) "
                    + "et le refresh token (7 jours).")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotation du refresh token",
            description = "Consomme le refresh token présenté, en émet un nouveau "
                    + "et renouvelle l'access token.")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion",
            description = "Révoque le refresh token présenté. Réponse vide et "
                    + "silencieuse, que le jeton soit connu ou non.")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
