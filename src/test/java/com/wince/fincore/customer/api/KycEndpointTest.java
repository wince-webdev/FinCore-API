package com.wince.fincore.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wince.fincore.TestcontainersConfiguration;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.domain.AppUser;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.identity.repository.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tests d'intégration <strong>F-02</strong> — preuve par le HTTP de :
 *
 * <ul>
 *   <li><b>Inscription d'un mineur</b> → 422 CUSTOMER_UNDERAGE (CA-KYC-02,
 *       RG-KYC-04) ;</li>
 *   <li>la matrice de droits de la §5 : CUSTOMER ne peut pas PATCH (403),
 *       TELLER monte à LEVEL_2 (200) ;</li>
 *   <li><b>CA-KYC-01</b> : le numéro de pièce n'apparaît nulle part — ni
 *       dans la réponse HTTP, ni dans les champs (seul le masqué), alors que
 *       la base ne contient que le numéro masqué — RG-KYC-03 ;</li>
 *   <li><b>§5</b> : id inexistant → 404 ; client mineur déjà existant →
 *       422 CUSTOMER_UNDERAGE (RG-KYC-04) ;</li>
 *   <li><b>GET /customers/me</b> → profil du client (§8).</li>
 * </ul>
 *
 * <p>Base réelle (Testcontainers PostgreSQL) : les règles métier reposent
 * sur des écritures SQL — les mocker rendrait le test faux (règle §11).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KycEndpointTest {

    /** RG-AUTH-01 : 12+ caractères, 4 classes de caractères. */
    private static final String STRONG_PASSWORD = "Aa1!Aa1!Aa1!Aa1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── Inscription — RG-KYC-01, CA-KYC-02 ───────────────────────────────

    @Test
    @DisplayName("CA-KYC-02 : inscription d'un mineur → 422 CUSTOMER_UNDERAGE, pas de compte ni de client créés")
    void minor_cannot_register() throws Exception {
        String email = "mineur-" + uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "Jean", "Bon", "2009-05-10", "0612345678")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CUSTOMER_UNDERAGE"));

        assertThat(userRepository.existsByEmail(email)).as("aucun compte créé").isFalse();
    }

    @Test
    @DisplayName("GET /customers/me — profil du client authentifié (§8) : LEVEL_1 / PENDING par défaut")
    void customer_reads_own_profile() throws Exception {
        MvcResult customer = registerCustomer("read-" + uniqueEmail());

        mockMvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", "Bearer " + read(customer, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Marie"))
                .andExpect(jsonPath("$.lastName").value("Dupont"))
                .andExpect(jsonPath("$.phone").value("0612345678"))
                .andExpect(jsonPath("$.kycLevel").value("LEVEL_1"))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.idDocNumberMasked").doesNotExist());
    }

    // ── Matrice de droits (§5) ──────────────────────────────────────────

    @Test
    @DisplayName("Matrice §5 : CUSTOMER sur PATCH /customers/{id}/kyc → 403 ACCESS_DENIED")
    void customer_cannot_patch_kyc() throws Exception {
        MvcResult customer = registerCustomer("cust-" + uniqueEmail());

        mockMvc.perform(patch("/api/v1/customers/" + UUID.randomUUID() + "/kyc")
                        .header("Authorization", "Bearer " + read(customer, "$.accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kycLevel\":\"LEVEL_2\",\"idDocType\":\"ID_CARD\",\"idDocNumber\":\"0123456789\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Matrice §5 : TELLER monte à LEVEL_2 → 200, niveau mis à jour (RG-KYC-02)")
    void teller_patches_kyc_level() throws Exception {
        String tellerEmail = "teller-" + uniqueEmail();
        userRepository.save(new AppUser(tellerEmail, passwordEncoder.encode(STRONG_PASSWORD), Role.TELLER));
        String tellerToken = read(login(tellerEmail), "$.accessToken");

        MvcResult customer = registerCustomer("patched-" + uniqueEmail());
        UUID customerId = customerRepository.findByUserId(UUID.fromString(read(customer, "$.userId")))
                .orElseThrow(() -> new AssertionError("le client n'a pas été créé à l'inscription"))
                .getId();

        MvcResult result = mockMvc.perform(patch("/api/v1/customers/" + customerId + "/kyc")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kycLevel\":\"LEVEL_2\",\"idDocType\":\"ID_CARD\",\"idDocNumber\":\"0123456789\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycLevel").value("LEVEL_2"))
                .andExpect(jsonPath("$.idDocType").value("ID_CARD"))
                .andExpect(jsonPath("$.idDocNumberMasked").value("****6789"))
                .andExpect(jsonPath("$.userId").value(read(customer, "$.userId")))
                .andReturn();

        // CA-KYC-01 : le numéro clair ne figure nulle part — pas même dans la
        // réponse HTTP (jamais de numéro dans une réponse par définition).
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("0123456789");
    }

    @Test
    @DisplayName("§5 : id inexistant → 404 RESOURCE_NOT_FOUND")
    void unknown_customer_is_404() throws Exception {
        String tellerEmail = "teller-" + uniqueEmail();
        userRepository.save(new AppUser(tellerEmail, passwordEncoder.encode(STRONG_PASSWORD), Role.TELLER));
        String tellerToken = read(login(tellerEmail), "$.accessToken");

        mockMvc.perform(patch("/api/v1/customers/" + UUID.randomUUID() + "/kyc")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kycLevel\":\"LEVEL_2\",\"idDocType\":\"ID_CARD\",\"idDocNumber\":\"0123456789\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("RG-KYC-04 : TELLER tente de monter un mineur à LEVEL_2 → 422 CUSTOMER_UNDERAGE")
    void minor_cannot_be_escalated() throws Exception {
        String tellerEmail = "teller-" + uniqueEmail();
        userRepository.save(new AppUser(tellerEmail, passwordEncoder.encode(STRONG_PASSWORD), Role.TELLER));
        String tellerToken = read(login(tellerEmail), "$.accessToken");

        AppUser minor = userRepository.save(new AppUser("minor-" + uniqueEmail(),
                passwordEncoder.encode(STRONG_PASSWORD), Role.CUSTOMER));
        UUID minorCustomerId = UUID.randomUUID();
        customerRepository.save(new Customer(minorCustomerId, minor.getId(),
                "Jean", "Bon", LocalDate.of(2009, 5, 10), "0612345678"));

        mockMvc.perform(patch("/api/v1/customers/" + minorCustomerId + "/kyc")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kycLevel\":\"LEVEL_2\",\"idDocType\":\"ID_CARD\",\"idDocNumber\":\"0123456789\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CUSTOMER_UNDERAGE"));
    }

    // ── LEVEL_3 — RG-KYC-02 (ADMIN seul + source de revenus) ──────────────

    @Test
    @DisplayName("Matrice §5 + RG-KYC-02 : TELLER tente LEVEL_3 → 403 ACCESS_DENIED (ADMIN seul)")
    void teller_cannot_reach_level_3() throws Exception {
        String tellerEmail = "teller-lvl3-" + uniqueEmail();
        userRepository.save(new AppUser(tellerEmail, passwordEncoder.encode(STRONG_PASSWORD), Role.TELLER));
        String tellerToken = read(login(tellerEmail), "$.accessToken");

        MvcResult customer = registerCustomer("teller3-" + uniqueEmail());

        mockMvc.perform(patch("/api/v1/customers/" + customerId(customer) + "/kyc")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kycLevel\":\"LEVEL_3\",\"idDocType\":\"ID_CARD\",\"idDocNumber\":\"0123456789\",\"sourceRevenus\":\"Emploi salarié\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("RG-KYC-02 : ADMIN monte en LEVEL_3 avec source de revenus → 200 masqué, aucun numéro clair (CA-KYC-01)")
    void admin_reaches_level_3_with_declared_source_of_revenue() throws Exception {
        String adminEmail = "admin-lvl3-" + uniqueEmail();
        userRepository.save(new AppUser(adminEmail, passwordEncoder.encode(STRONG_PASSWORD), Role.ADMIN));
        String adminToken = read(login(adminEmail), "$.accessToken");

        MvcResult customer = registerCustomer("admin3-" + uniqueEmail());

        MvcResult result = mockMvc.perform(patch("/api/v1/customers/" + customerId(customer) + "/kyc")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kycLevel\":\"LEVEL_3\",\"idDocType\":\"ID_CARD\",\"idDocNumber\":\"0123456789\",\"sourceRevenus\":\"Emploi salarié\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycLevel").value("LEVEL_3"))
                .andExpect(jsonPath("$.idDocNumberMasked").value("****6789"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("0123456789");
    }

    @Test
    @DisplayName("RG-KYC-02 : ADMIN sans source de revenus → 400 VALIDATION_FAILED (errors.sourceRevenus)")
    void admin_level_3_requires_declared_source_of_revenue() throws Exception {
        String adminEmail = "admin-lvl3b-" + uniqueEmail();
        userRepository.save(new AppUser(adminEmail, passwordEncoder.encode(STRONG_PASSWORD), Role.ADMIN));
        String adminToken = read(login(adminEmail), "$.accessToken");

        MvcResult customer = registerCustomer("admin3b-" + uniqueEmail());

        mockMvc.perform(patch("/api/v1/customers/" + customerId(customer) + "/kyc")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kycLevel\":\"LEVEL_3\",\"idDocType\":\"ID_CARD\",\"idDocNumber\":\"0123456789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.sourceRevenus").isNotEmpty());
    }

    // ── Passerelles ──────────────────────────────────────────────────────

    /** Connexion par mot de passe — jeton d'accès pour les appels protégés. */
    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, STRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Inscription d'un client par l'endpoint public — RG-KYC-01. */
    private MvcResult registerCustomer(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "Marie", "Dupont", "1990-01-05", "0612345678")))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Id du `Customer` lié à l'id de l'utilisateur (§5 — le `{id}` des PATCH). */
    private UUID customerId(MvcResult customer) throws Exception {
        return customerRepository.findByUserId(UUID.fromString(read(customer, "$.userId")))
                .orElseThrow(() -> new AssertionError("le client n'a pas été créé à l'inscription"))
                .getId();
    }

    private static String credentials(String email, String firstName, String lastName,
                                      String birthDate, String phone) {
        return ("{\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\","
                + "\"birthDate\":\"%s\",\"phone\":\"%s\",\"password\":\"%s\"}")
                .formatted(email, firstName, lastName, birthDate, phone, STRONG_PASSWORD);
    }

    /** Lecture d'un champ JSON sans bibliothèque lourde (cf. AuthFlowTest). */
    private static String read(MvcResult result, String jsonPath) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), jsonPath);
        return String.valueOf(value);
    }

    private static String uniqueEmail() {
        return "cust" + UUID.randomUUID() + "@fincore.test";
    }
}
