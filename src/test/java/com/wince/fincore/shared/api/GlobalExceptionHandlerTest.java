package com.wince.fincore.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.domain.Money;
import com.wince.fincore.shared.infrastructure.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Tests du {@link GlobalExceptionHandler} : codes HTTP, codes métier,
 * champs RFC 9457 (type/title/detail/instance/traceId/timestamp).
 *
 * <p>Approche par <strong>appel direct des méthodes</strong> (pas de
 * MockMvc) : on teste la <em>logique</em> de construction du ProblemDetail,
 * sans dépendre du convertisseur JSON de Spring — plus rapide, plus stable.</p>
 */
class GlobalExceptionHandlerTest {

    /** Horloge figée : le test doit être déterministe. */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-15T10:30:00Z");

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(FIXED_INSTANT, ZoneId.of("Africa/Porto-Novo"));
        handler = new GlobalExceptionHandler(fixed);
        request = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/v1/accounts/42");
    }

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    // ── FinCoreException → statut + code métier ──────────────────────────

    @Test
    @DisplayName("Solde insuffisant → 422 + INSUFFICIENT_FUNDS")
    void insufficientFundsMapsTo422() {
        FinCoreException ex = new FinCoreException(ErrorCode.INSUFFICIENT_FUNDS,
                "Solde insuffisant : disponible 100 XOF, requis 500 XOF.");

        ProblemDetail pd = handler.onFinCore(ex, request);

        assertThat(pd.getStatus()).isEqualTo(422);
        assertThat(pd.getProperties().get("code")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(pd.getDetail()).contains("disponible 100 XOF");
    }

    @Test
    @DisplayName("Échelle de montant invalide → 400 + INVALID_AMOUNT_SCALE")
    void invalidAmountScaleMapsTo400() {
        FinCoreException ex = null;
        try {
            Money.parse("100.50", Currency.XOF);
        } catch (FinCoreException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();

        ProblemDetail pd = handler.onFinCore(ex, request);

        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getProperties().get("code")).isEqualTo("INVALID_AMOUNT_SCALE");
    }

    @Test
    @DisplayName("Champs RFC 9457 complets : type, title, instance, traceId, timestamp")
    void problemDetailCarriesRfc9457Fields() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-123");
        FinCoreException ex = new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "absent");

        ProblemDetail pd = handler.onFinCore(ex, request);

        assertThat(pd.getType()).hasToString("https://fincore.example/errors/resource-not-found");
        assertThat(pd.getTitle()).isEqualTo("Ressource introuvable");
        assertThat(pd.getDetail()).isEqualTo("absent");
        assertThat(pd.getInstance()).hasToString("/api/v1/accounts/42");
        assertThat(pd.getProperties().get("traceId")).isEqualTo("trace-123");
        assertThat(pd.getProperties().get("timestamp")).isEqualTo(FIXED_INSTANT.toString());
        assertThat(pd.getProperties().get("code")).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("traceId absent du MDC → « unknown », jamais null")
    void traceIdFallsBackToUnknown() {
        ProblemDetail pd = handler.onFinCore(
                new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "absent"), request);
        assertThat(pd.getProperties().get("traceId")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("Détails structurés (plafond, solde…) ajoutés au corps")
    void structuredDetailsArePropagated() {
        FinCoreException ex = new FinCoreException(ErrorCode.LIMIT_EXCEEDED,
                "Plafond quotidien dépassé.",
                Map.of("plafondRestant", "5000.00", "devise", "XOF"));

        ProblemDetail pd = handler.onFinCore(ex, request);

        assertThat(pd.getProperties().get("plafondRestant")).isEqualTo("5000.00");
        assertThat(pd.getProperties().get("devise")).isEqualTo("XOF");
    }

    // ── Erreur inattendue → 500 générique, sans fuite ────────────────────

    @Test
    @DisplayName("Exception inconnue → 500 INTERNAL_ERROR avec détail générique")
    void unexpectedErrorIsMasked() {
        ProblemDetail pd = handler.onUnexpected(
                new IllegalStateException("chemin /var/lib/secrets exposé"), request);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getProperties().get("code")).isEqualTo("INTERNAL_ERROR");
        // Le message brut de l'exception ne doit JAMAIS fuiter au client.
        assertThat(pd.getDetail()).doesNotContain("/var/lib/secrets");
        assertThat(pd.getDetail()).contains("erreur interne");
    }

    // ── 404 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ressource absente → 404 RESOURCE_NOT_FOUND")
    void notFoundIs404() {
        ProblemDetail pd = handler.onNotFound(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/nope", "/nope"), request);

        assertThat(pd.getStatus()).isEqualTo(404);
        assertThat(pd.getProperties().get("code")).isEqualTo("RESOURCE_NOT_FOUND");
    }
}
