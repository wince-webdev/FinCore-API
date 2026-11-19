package com.wince.fincore.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre transverse de corrélation : garantit qu'une requête possède un
 * identifiant unique, présent à la fois dans la réponse, dans les logs et
 * dans les erreurs (§2.4 du cahier des charges).
 *
 * <p><strong>Comportement :</strong></p>
 * <ul>
 *   <li>lit l'en-tête entrant {@code X-Correlation-Id} ; s'il est absent ou
 *       vide, en génère un ({@code UUID.randomUUID()}) ;</li>
 *   <li>le place dans le MDC SLF4J sous la clé {@link #MDC_KEY} — tous les
 *       logs émis pendant la requête le portent automatiquement ;</li>
 *   <li>le renvoie dans l'en-tête de réponse {@code X-Correlation-Id} ;</li>
 *   <li>nettoie le MDC en {@code finally} : un filtre exécuté dans un thread
 *       du pool ne doit jamais « fuiter » d'un identifiant vers la requête
 *       suivante du même thread.</li>
 * </ul>
 *
 * <p>Équivalent Symfony : un listener {@code kernel.request} qui pose un
 * identifiant dans le logger context.</p>
 *
 * <p>Le nom de la clé MDC est exposé publiquement car
 * {@link com.wince.fincore.shared.api.GlobalExceptionHandler} la lit pour
 * remplir le {@code traceId} des corps d'erreur.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Clé MDC partagée entre ce filtre et le handler d'erreurs. */
    public static final String MDC_KEY = "correlationId";

    /** En-tête HTTP utilisé à l'entrée et à la sortie. */
    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming.trim();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Toujours en finally : le MDC est propre même si la requête échoue.
            MDC.remove(MDC_KEY);
        }
    }
}
