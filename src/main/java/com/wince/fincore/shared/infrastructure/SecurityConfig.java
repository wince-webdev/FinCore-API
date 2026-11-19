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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Chaîne de sécurité transverse — <strong>phase 1</strong> : uniquement le
 * nécessaire pour servir Swagger UI et la santé Docker.
 *
 * <p><strong>Règle d'or : refuser par défaut.</strong> Spring Security
 * bloque déjà tout (api-docs répondait 401 avant cette classe) ; ici on
 * ouvre <em>ponctuellement</em> les ressources publiques du §12.3 :</p>
 * <ul>
 *   <li>{@code /actuator/health*} — indispensable au HEALTHCHECK Docker,
 *       sans authentification (un moniteur ne possède pas de jeton) ;</li>
 *   <li>{@code /api-docs/**} + {@code /swagger-ui/**} — contrat OpenAPI,
 *       ouvert en dev uniquement ;</li>
 *   <li>tout le reste → {@code authenticated()} : sans jeton = 401, jamais
 *       de page « 403 silencieuse » (CA-AUTH-04).</li>
 * </ul>
 *
 * <p><strong>Phase 2</strong> : cette classe sera étendue (JWT resource
 * server, contrôle d'accès par rôle CUSTOMER/AGENT/OPS/AUDITOR, jeton
 * révoqué…). Elle vit déjà dans {@code shared} car elle ne dépend d'aucun
 * métier ; le module {@code identity} y ajoutera son authenticateur.</p>
 *
 * <p>Équivalent Symfony : la classe {@code SecurityBundle} + les
 * {@code access_control} du {@code security.yaml}.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Ordre explicite : cette chaîne « basique » passe avant toute autre. */
    public static final int CHAIN_ORDER = 0;

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
                        // Tout le reste exige un jeton (phase 2 : JWT).
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        // CA-AUTH-04 : sans jeton → 401 (et pas 403 « silencieux »),
                        // avec le même corps RFC 9457 que GlobalExceptionHandler.
                        .authenticationEntryPoint(unauthenticatedEntryPoint())
                        // Jeton valide mais rôle insuffisant → 403.
                        .accessDeniedHandler(accessDeniedHandler()));
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
