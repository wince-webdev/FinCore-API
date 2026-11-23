package com.wince.fincore.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wince.fincore.TestcontainersConfiguration;
import com.wince.fincore.identity.domain.AppUser;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.identity.repository.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests d'intégration <strong>F-01</strong> — la preuve par le HTTP des
 * critères d'acceptation de l'authentification :
 *
 * <ul>
 *   <li><b>CA-AUTH-01</b> — mot de passe faible → 400 + règles violées ;</li>
 *   <li><b>CA-AUTH-02</b> — 5 échecs → le 6<sup>e</sup> essai (même correct)
 *       répond 423 jusqu'à l'expiration du verrou ;</li>
 *   <li><b>CA-AUTH-03</b> — rejeu d'un refresh token → 401 <em>et</em>
 *       révocation de toute la famille ;</li>
 *   <li><b>CA-AUTH-04</b> — sans jeton 401, rôle insuffisant 403 ;</li>
 *   <li>RG-AUTH-06 — claims du JWT (sub, role, iat, exp, jti).</li>
 * </ul>
 *
 * <p>Base réelle (Testcontainers PostgreSQL) : les règles de verrouillage
 * et de rotation reposent sur des écritures SQL — les mocker rendrait le
 * test faux (règle §11 du cahier des charges).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    /** RG-AUTH-01 : 12+ caractères, 4 classes de caractères. */
    private static final String STRONG_PASSWORD = "Aa1!Aa1!Aa1!Aa1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    // ── CA-AUTH-01 · Politique de mot de passe ──────────────────────────

    @Test
    @DisplayName("CA-AUTH-01 : mot de passe faible → 400 VALIDATION_FAILED + liste des règles")
    void ca_auth_01_rejet_d_un_mot_de_passe_faible() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.password").isArray())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());

        assertThat(userRepository.existsByEmail(email)).as("aucun compte créé").isFalse();
    }

    @Test
    @DisplayName("CA-AUTH-01 : inscription valide → 201, les deux jetons, Bearer, 900 s")
    void ca_auth_01_inscription_valide() throws Exception {
        String email = uniqueEmail();

        MvcResult result = register(email, STRONG_PASSWORD);

        assertThat(read(result, "$.tokenType")).isEqualTo("Bearer");
        assertThat(read(result, "$.role")).isEqualTo("CUSTOMER");
        assertThat(read(result, "$.userId")).isNotBlank();
        // RG-AUTH-03 : access token 15 min = 900 secondes.
        assertThat(Long.parseLong(read(result, "$.expiresIn"))).isEqualTo(900);
        assertThat(read(result, "$.accessToken")).isNotBlank();
        assertThat(read(result, "$.refreshToken")).isNotBlank();
    }

    @Test
    @DisplayName("RG-AUTH-06 : le JWT contient sub, role, iat, exp, jti et l'issuer fincore")
    void rg_auth_06_claims_du_jwt() throws Exception {
        MvcResult result = register(uniqueEmail(), STRONG_PASSWORD);
        String accessToken = read(result, "$.accessToken");
        String userId = read(result, "$.userId");

        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toMinutes())
                .as("fenêtre de validité de l'access token").isEqualTo(15);
        assertThat(jwt.getId()).as("jti").isNotBlank();
        // Lu comme une simple chaîne : Jwt.getIssuer() exige une URL valide
        // or notre issuer est le simple mot « fincore ».
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("fincore");
    }

    // ── CA-AUTH-02 / RG-AUTH-05 · Verrouillage et non-énumération ──────

    @Test
    @DisplayName("CA-AUTH-02 : 5 échecs verrouillent le compte → 423 y compris avec le bon mot de passe")
    void ca_auth_02_verrouillage_apres_cinq_echecs() throws Exception {
        String email = uniqueEmail();
        register(email, STRONG_PASSWORD);

        // 4 premiers échecs : 401, message non informatif.
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(email, "MauvaisMotDePasse" + attempt + "!")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        // 5ᵉ échec : le verrou est posé, on le signale immédiatement.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "MauvaisMotDePasse5!")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());

        // 6ᵉ essai AVEC LE BON MOT DE Passe : toujours 423 (CA-AUTH-02).
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, STRONG_PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("RG-AUTH-05 : « utilisateur inconnu » et « mauvais mot de passe » → réponse identique")
    void rg_auth_05_pas_d_Enumeration_des_utilisateurs() throws Exception {
        String knownEmail = uniqueEmail();
        register(knownEmail, STRONG_PASSWORD);

        MvcResult unknown = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), "Nimportequoi1!")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(knownEmail, "Nimportequoi1!")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String unknownDetail = read(unknown, "$.detail");
        String wrongDetail = read(wrongPassword, "$.detail");
        assertThat(wrongDetail)
                .as("le message ne doit pas révéler l'existence du compte")
                .isEqualTo(unknownDetail);
        assertThat(unknownDetail).isEqualTo("Identifiants invalides.");
    }

    // ── CA-AUTH-03 / RG-AUTH-04 · Rotation et révocation de famille ────

    @Test
    @DisplayName("CA-AUTH-03 : le rejeu d'un refresh token renvoie 401 et révoque toute la famille")
    void ca_auth_03_rejeu_du_refresh_token() throws Exception {
        MvcResult login = register(uniqueEmail(), STRONG_PASSWORD);
        String firstRefresh = read(login, "$.refreshToken");

        // Rotation : on échange le jeton contre une paire neuve.
        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        String secondRefresh = read(rotated, "$.refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        // Le nouvel access token est réellement vérifiable.
        assertThat(jwtDecoder.decode(read(rotated, "$.accessToken")).getSubject())
                .isEqualTo(read(login, "$.userId"));

        // Rejeu du jeton déjà consommé → 401.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // ... et la famille entière est morte : le successeur aussi (RG-AUTH-04).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("RG-AUTH-04 : logout révoque le refresh token présenté (plus de rotation possible)")
    void rg_auth_04_logout_revoque_le_refresh_token() throws Exception {
        MvcResult login = register(uniqueEmail(), STRONG_PASSWORD);
        String accessToken = read(login, "$.accessToken");
        String refreshToken = read(login, "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ── CA-AUTH-04 · Matrice des droits ─────────────────────────────────

    @Test
    @DisplayName("CA-AUTH-04 : endpoint protégé sans jeton → 401 + corps RFC 9457")
    void ca_auth_04_sans_jeton_renvoie_401() throws Exception {
        mockMvc.perform(get("/api/v1/__probe/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("CA-AUTH-04 : bearer illisible ou falsifié → 401, jamais un 500")
    void ca_auth_04_jeton_invalide_renvoie_401() throws Exception {
        mockMvc.perform(get("/api/v1/__probe/admin-only")
                        .header("Authorization", "Bearer " + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("CA-AUTH-04 : CUSTOMER sur une route ADMIN → 403 ACCESS_DENIED")
    void ca_auth_04_role_insuffisant_renvoie_403() throws Exception {
        MvcResult login = register(uniqueEmail(), STRONG_PASSWORD);

        mockMvc.perform(get("/api/v1/__probe/admin-only")
                        .header("Authorization", "Bearer " + read(login, "$.accessToken")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Matrice des droits : ADMIN est accepté là où CUSTOMER reçoit 403")
    void matrice_des_droits_admin_autorise() throws Exception {
        String adminEmail = uniqueEmail();
        userRepository.save(new AppUser(adminEmail, passwordEncoder.encode(STRONG_PASSWORD), Role.ADMIN));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(adminEmail, STRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        mockMvc.perform(get("/api/v1/__probe/admin-only")
                        .header("Authorization", "Bearer " + read(login, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(content().string("autorise"));
    }

    // ── Passerelles ─────────────────────────────────────────────────────

    private MvcResult register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /**
     * Corps de {@code POST /auth/register} — extension Phase 3 : F-02
     * {@code RegisterRequest} porte désormais le profil minimal du client
     * (prénom, nom, naissance, téléphone) — valeurs utilisées pour les tests.
     */
    private static String credentials(String email, String password) {
        return ("{\"email\":\"%s\",\"firstName\":\"Marie\",\"lastName\":\"Dupont\","
                + "\"birthDate\":\"1990-01-05\",\"phone\":\"0612345678\",\"password\":\"%s\"}")
                .formatted(email, password);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    /**
     * Lecture d'un champ de la réponse sans bibliothèque JSON lourde.
     *
     * <p>{@code String.valueOf(...)} est indispensable : JsonPath renvoie un
     * {@code Integer} pour {@code expiresIn} et un {@code String} pour les
     * autres champs — un cast direct exploserait sur les nombres.</p>
     */
    private static String read(MvcResult result, String jsonPath) throws Exception {
        Object value = JsonPath.read(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), jsonPath);
        return String.valueOf(value);
    }

    private static String uniqueEmail() {
        return "flow-" + UUID.randomUUID() + "@fincore.test";
    }

    /**
     * Endpoint factice <strong>réservé aux tests</strong> : en phase 2, aucun
     * endpoint métier n'existe encore avec une règle de rôle, or CA-AUTH-04
     * exige déjà de prouver le 403. Ce contrôleur ne vit que dans ce contexte
     * de test — il n'est jamais déployé avec l'application.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeEndpoint {

        @RestController
        static class ProbeController {

            @GetMapping("/api/v1/__probe/admin-only")
            @PreAuthorize("hasRole('ADMIN')")
            public String adminOnly() {
                return "autorise";
            }
        }
    }
}
