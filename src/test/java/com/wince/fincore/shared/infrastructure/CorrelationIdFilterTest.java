package com.wince.fincore.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests unitaires du {@link CorrelationIdFilter} : génération, reprise de
 * l'en-tête entrant, propagation en réponse, nettoyage du MDC.
 *
 * <p>On utilise les mocks <strong>Spring</strong>
 * ({@code MockHttpServletRequest/Response}) et non des mocks Mockito :
 * un mock généré automatiquement n'a pas de « vrai » stockage d'en-têtes —
 * {@code setHeader} n'alimente pas {@code getHeader}. Le mock Spring
 * reproduit le comportement réel du conteneur servlet.</p>
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    /** Chaîne qui capture le MDC au moment exact de l'exécution. */
    private static FilterChain capturingChain(String[] capture) {
        return (request, response) -> capture[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    @DisplayName("Sans en-tête entrant : UUID généré, en MDC pendant la requête, en réponse")
    void generatesUuidWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenByChain = new String[1];

        filter.doFilter(request, response, capturingChain(seenByChain));

        assertThat(seenByChain[0]).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seenByChain[0]);
        // Nettoyage après la requête : pas de fuite vers le thread suivant.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("En-tête entrant conservé à l'identique (bout en bout)")
    void reusesIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        request.addHeader(CorrelationIdFilter.HEADER, "  trace-from-client  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenByChain = new String[1];

        filter.doFilter(request, response, capturingChain(seenByChain));

        assertThat(seenByChain[0]).isEqualTo("trace-from-client");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("trace-from-client");
    }

    @Test
    @DisplayName("En-tête vide ou blanc → nouvel UUID généré")
    void blankHeaderGeneratesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        request.addHeader(CorrelationIdFilter.HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain(new String[1]));

        String echoed = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(echoed).isNotBlank();
        assertThat(echoed).doesNotContain(" ");
    }

    @Test
    @DisplayName("MDC nettoyé même si la chaîne lève une exception")
    void mdcIsCleanedWhenChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (req, res) -> {
            throw new RuntimeException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing))
                .hasMessage("boom");

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
