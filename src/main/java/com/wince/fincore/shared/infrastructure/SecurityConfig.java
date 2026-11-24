package com.wince.fincore.shared.infrastructure;

import com.wince.fincore.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Chaîne de sécurité transverse — <strong>phase 2</strong> : la seule
 * chaîne de l'application, stateless, qui valide les JWT et arbitre les rôles.
 *
 * <p><strong>Règle d'or : refuser par défaut.</strong> On ouvre
 * <em>ponctuellement</em> les ressources publiques du §12.3 :</p>
 * <ul>
 *   <li>{@code /actuator/health*} — indispensable au HEALTHCHECK Docker,
 *       sans authentification (un moniteur ne possède pas de jeton) ;</li>
 *   <li>{@code /api-docs/**} + {@code /swagger-ui/**} — contrat OpenAPI,
 *       ouvert en dev uniquement ;</li>
 *   <li>{@code POST /auth/register}, {@code /login}, {@code /refresh} —
 *       c'est précisément l'endroit où l'on obtient un jeton, le protéger
 *       serait une impasse ; {@code /logout}, lui, exige un jeton ;</li>
 *   <li>tout le reste → {@code authenticated()} : sans jeton = 401, jamais
 *       de page « 403 silencieuse » (CA-AUTH-04).</li>
 * </ul>
 *
 * <p><strong>Phase 2</strong> : le <em>resource server</em> JWT est branché
 * ici ({@code oauth2ResourceServer}), mais les beans qu'il faut — le
 * {@code JwtDecoder} et le convertisseur du claim {@code role} — sont
 * publiés par le module {@code identity}
 * ({@code IdentitySecurityConfig}), seul à connaître le secret et le
 * contrat de jetons.</p>
 *
 * <p>Équivalent Symfony : la classe {@code SecurityBundle} + les
 * {@code access_control} du {@code security.yaml}.</p>
 */
@Configuration
@EnableWebSecurity
// @PreAuthorize("hasRole(...)") : indispensable pour la matrice des droits
// (§5) — testée dès la phase 2 et réutilisée sur les endpoints /admin/**.
@EnableMethodSecurity
public class SecurityConfig {

    /** Ordre explicite : cette chaîne « basique » passe avant toute autre. */
    public static final int CHAIN_ORDER = 0;

    /**
     * Hachage des mots de passe — RG-AUTH-01 : BCrypt coût 12.
     *
     * <p>Le coût n'est pas un détail : chaque vérification coûte ~250 ms,
     * ce qui rend une attaque par force brute <em>patiente</em> (≈ 4 essais
     * par seconde et par cœur de CPU). Baisser le coût à 4 pour « gagner
     * du temps » en test reviendrait à tester autre chose que la prod.</p>
     *
     * <p>Équivalent Symfony : {@code security.password_hashers} avec
     * {@code cost: 12}.</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Order(CHAIN_ORDER)
    public SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless : jamais de cookie de session, jamais de CSRF
                // (le CSRF protège les formulaires HTML, pas les appels JWT).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Santé Docker + monitoring (toujours public).
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info")
                        .permitAll()
                        // Contrat OpenAPI + Swagger UI (public en dev ;
                        // désactivés en prod via springdoc.* = false).
                        .requestMatchers(
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        // F-01 : les trois endpoints qui DÉLIVRENT un jeton
                        // sont publics (RG-AUTH-02/03). /auth/logout n'est
                        // pas dans la liste : il exige un jeton valide,
                        // comme les 4 autres rôles du catalogue §8.
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh")
                        .permitAll()
                        // Tout le reste exige un jeton valide (phase 2 : JWT).
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        // CA-AUTH-04 : sans jeton → 401 (et pas 403 « silencieux »),
                        // avec le même corps RFC 9457 que GlobalExceptionHandler.
                        .authenticationEntryPoint(unauthenticatedEntryPoint())
                        // Jeton valide mais droits insuffisants → 403.
                        .accessDeniedHandler(accessDeniedHandler()))
                // Resource server JWT : le JwtDecoder et le convertisseur du
                // claim « role » sont découverts dans le contexte (beans
                // publiés par identity/IdentitySecurityConfig).
                // Piège Spring Security 7 : la simple présence d'un bean
                // JwtDecoder ne suffit plus — il faut déclarer explicitement
                // le format des jetons, sinon le build échoue avec
                // « Jwt and Opaque Token are the only supported formats ».
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthenticatedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** 401 UNAUTHENTICATED — corps problem+json, traceId inclus. */
    private static AuthenticationEntryPoint unauthenticatedEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.AuthenticationException authException)
                -> writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorCode.UNAUTHENTICATED,
                        "Authentification requise : jeton absent, expiré ou invalide.",
                        request);
    }

    /** 403 ACCESS_DENIED — jeton présent mais droits insuffisants. */
    private static AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException accessDeniedException)
                -> writeProblem(response, HttpServletResponse.SC_FORBIDDEN,
                        ErrorCode.ACCESS_DENIED,
                        "Vous n'avez pas les droits nécessaires pour cette ressource.",
                        request);
    }

    /**
     * Écrit un corps {@code application/problem+json} minimal — dupliqué
     * (volontairement) de {@code GlobalExceptionHandler} car Security s'exécute
     * <em>avant</em> les contrôleurs : le {@code @RestControllerAdvice} n'est
     * jamais atteint sur ce chemin.
     */
    private static void writeProblem(HttpServletResponse response, int status,
                                     ErrorCode code, String detail,
                                     HttpServletRequest request)
            throws IOException {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String body = "{\"type\":\"" + code.type()
                + "\",\"title\":\"" + code.title()
                + "\",\"status\":" + status
                + ",\"detail\":\"" + detail
                + "\",\"instance\":\"" + request.getRequestURI()
                + "\",\"code\":\"" + code.name()
                + "\",\"traceId\":\"" + (traceId != null ? traceId : "unknown")
                + "\"}";
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
