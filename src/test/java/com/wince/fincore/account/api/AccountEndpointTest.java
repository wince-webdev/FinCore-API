package com.wince.fincore.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wince.fincore.TestcontainersConfiguration;
import com.wince.fincore.account.application.AccountNumberGenerator;
import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.domain.KycStatus;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.domain.AppUser;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.identity.repository.AppUserRepository;
import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;
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
 * Tests d'intégration F-03 — preuve par le HTTP des critères
 * d'acceptation et des règles (§8, §5) :
 *
 * <ul>
 *   <li><b>RG-ACC-06</b> : comptes systèmes seedés (6 codes × 3 devises) ;</li>
 *   <li>{@code POST /accounts} : TELLER 201 / CUSTOMER 403 — RG-ACC-01 à 07 ;</li>
 *   <li>{@code GET /accounts} — CUSTOMER — « mes comptes » ;</li>
 *   <li>{@code GET /accounts/{id}} : CA-ACC-02 — 404 pour un autre client —
 *       404 système — CUSTOMER, 200 ADMIN ;</li>
 *   <li>{@code GET /accounts/lookup?number=} : CA-ACC-01 — 400 — nom masqué — §8 ;</li>
 *   <li>{@code PATCH /accounts/{id}/status} : TELLER 403, ADMIN 200 — RG-ACC-04/05, F-13 —
 *       statut vérifié via {@code accountRepository}.</li>
 * </ul>
 *
 * <p>Base réelle (Testcontainers PostgreSQL) : les règles métier reposent
 * sur des écritures SQL — les mocker rendrait le test faux (rôle §11).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountEndpointTest {

    /** RG-AUTH-01 : 12+ caractères, 4 classes de caractères. */
    private static final String STRONG_PASSWORD = "Aa1!Aa1!Aa1!Aa1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Connexion par mot de passe — jeton d'accès — §2 (Phase 2). */
    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, STRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Inscription d'un client par l'endpoint public — RG-AUTH-02 / F-03 — §8. */
    private MvcResult registerCustomer(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String credentials(String email) {
        return ("{\"email\":\"%s\",\"firstName\":\"Marie\",\"lastName\":\"Dupont\","
                + "\"birthDate\":\"1990-01-05\",\"phone\":\"0612345678\",\"password\":\"%s\"}")
                .formatted(email, STRONG_PASSWORD);
    }

    /** Id du `Customer` lié à l'id de l'utilisateur — §5. */
    private String customerId(MvcResult customer) throws Exception {
        return customerRepository.findByUserId(UUID.fromString(read(customer, "$.userId")))
                .orElseThrow(() -> new AssertionError("le client n'a pas été créé à l'inscription"))
                .getId().toString();
    }

    /** Crée un TELLER et renvoie son jeton d'accès (§5 — ouvrir un compte). */
    private String tellerToken() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new AppUser(email, passwordEncoder.encode(STRONG_PASSWORD), Role.TELLER));
        return read(login(email), "$.accessToken");
    }

    /** Crée un ADMIN et renvoie son jeton d'accès (§5 — statut). */
    private String adminToken() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new AppUser(email, passwordEncoder.encode(STRONG_PASSWORD), Role.ADMIN));
        return read(login(email), "$.accessToken");
    }

    /** {@code POST /api/v1/accounts} — ouverture — renvoie le résultat (201). */
    private MvcResult openAccount(String bearerToken, String customerId, String kind, String currency)
            throws Exception {
        return mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId + "\",\"kind\":\"" + kind
                                + "\",\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Lecture d'un champ de la réponse sans bibliothèque JSON lourde. */
    private static String read(MvcResult result, String jsonPath) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), jsonPath);
        return String.valueOf(value);
    }

    private static String uniqueEmail() {
        return "acct" + UUID.randomUUID() + "@fincore.test";
    }

    // ── Tests ────────────────────────────────────────────────────────────

    // ── RG-ACC-06 — seed des comptes systèmes (migration + seed — V4) ────

    @Test
    @DisplayName("RG-ACC-06 : comptes systèmes seedés — 6 codes × 3 devises (XOF/EUR/USD)")
    void system_accounts_are_seeded_at_startup() {
        assertThat(accountRepository.findAllBySystemCode(SystemCode.CASH_VAULT))
                .hasSize(3)
                .extracting(Account::getCurrency)
                .containsExactlyInAnyOrder(Currency.XOF, Currency.EUR, Currency.USD);
        assertThat(accountRepository.findAllBySystemCode(SystemCode.CASH_VAULT))
                .allMatch(Account::isSystem)
                .allMatch(a -> a.getCustomerId() == null)
                .allMatch(a -> a.getStatus() == AccountStatus.ACTIVE);
        assertThat(accountRepository.findAllBySystemCode(SystemCode.CASH_VAULT))
                .allSatisfy(a -> assertThat(AccountNumberGenerator.isValid(a.getAccountNumber()))
                        .as("numéro du compte système valide — clé mod 97 — RG-ACC-03").isTrue());
        assertThat(accountRepository.findAllBySystemCode(SystemCode.FEE_REVENUE)).hasSize(3);
        assertThat(accountRepository.findAllBySystemCode(SystemCode.INTEREST_EXPENSE)).hasSize(3);
    }

    // ── POST /accounts — §5, TELLER/ADMIN, RG-ACC-01 à 07 ────────────────

    @Test
    @DisplayName("§5/§8/RG-ACC-01 : TELLER ouvre un compte → 201 — champs de la réponse")
    void teller_opens_account_201() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String customerId = customerId(customer);

        MvcResult result = openAccount(tellerToken, customerId, "CURRENT", "XOF");

        assertThat(read(result, "$.status")).isEqualTo("ACTIVE");
        assertThat(read(result, "$.kind")).isEqualTo("CURRENT");
        assertThat(read(result, "$.currency")).isEqualTo("XOF");
        assertThat(read(result, "$.customerId")).isEqualTo(customerId);
        assertThat(AccountNumberGenerator.isValid(read(result, "$.accountNumber"))).isTrue();
        assertThat(accountRepository.findByAccountNumber(read(result, "$.accountNumber"))).isPresent();
    }

    @Test
    @DisplayName("§5 : CUSTOMER ouvre un compte → 403 ACCESS_DENIED")
    void customer_cannot_open_account_403() throws Exception {
        MvcResult customer = registerCustomer(uniqueEmail());

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + read(customer, "$.accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId(customer)
                                + "\",\"kind\":\"CURRENT\",\"currency\":\"XOF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("RG-ACC-02 : client rejeté par le KYC → 400 VALIDATION_FAILED")
    void open_account_for_rejected_customer_400() throws Exception {
        String tellerToken = tellerToken();
        AppUser customerUser = userRepository.save(new AppUser(uniqueEmail(),
                passwordEncoder.encode(STRONG_PASSWORD), Role.CUSTOMER));
        Customer rejected = new Customer(UUID.randomUUID(), customerUser.getId(),
                "Rej", "ecté", LocalDate.of(1990, 1, 5), "0612345678");
        rejected.setKycStatus(KycStatus.REJECTED);
        customerRepository.save(rejected);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + rejected.getId()
                                + "\",\"kind\":\"CURRENT\",\"currency\":\"XOF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("RG-ACC-02 : un seul compte par couple (type, devise) → 400")
    void duplicate_account_for_same_type_and_currency_400() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String customerId = customerId(customer);

        openAccount(tellerToken, customerId, "CURRENT", "XOF");

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId
                                + "\",\"kind\":\"CURRENT\",\"currency\":\"XOF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── GET /accounts — §5/§8 — CUSTOMER — « mes comptes » ───────────────

    @Test
    @DisplayName("§8/§5 : CUSTOMER lit ses comptes (GET /accounts)")
    void customer_reads_own_accounts() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String customerId = customerId(customer);
        MvcResult account = openAccount(tellerToken, customerId, "CURRENT", "XOF");
        String accountNumber = read(account, "$.accountNumber");

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + read(customer, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value(accountNumber));
    }

    // ── GET /accounts/{id} — §5 — CA-ACC-02 (404 pour les autres) ────────

    @Test
    @DisplayName("CA-ACC-02 : CUSTOMER lit le compte d'un autre client → 404")
    void customer_cannot_read_others_account_404() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customerA = registerCustomer(uniqueEmail());
        MvcResult customerB = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customerA), "CURRENT", "XOF"), "$.id");

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + read(customerB, "$.accessToken")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("§5 : CUSTOMER ne lit pas un compte système → 404")
    void customer_cannot_read_system_account_404() throws Exception {
        MvcResult customer = registerCustomer(uniqueEmail());
        String systemId = accountRepository.findAllBySystemCode(SystemCode.CASH_VAULT).get(0).getId().toString();

        mockMvc.perform(get("/api/v1/accounts/" + systemId)
                        .header("Authorization", "Bearer " + read(customer, "$.accessToken")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("§5 : TELLER ne lit pas le détail — 403 (matrice §5 — TELLER/ADMIN uniquement)")
    void teller_cannot_read_account_detail_403() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + tellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("§5 : ADMIN lit le détail d'un compte → 200")
    void admin_reads_account_detail_200() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");
        String adminToken = adminToken();

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").isNotEmpty());
    }

    // ── GET /accounts/lookup — §8, CUSTOMER — CA-ACC-01 / RG-ACC-03 ──────

    @Test
    @DisplayName("CA-ACC-01 : pré-contrôle avec clé invalide → 400 INVALID_ACCOUNT_NUMBER")
    void lookup_rejects_invalid_number_400() throws Exception {
        MvcResult customer = registerCustomer(uniqueEmail());
        String validNumber = AccountNumberGenerator.generate();
        String corrupted = validNumber.substring(0, validNumber.length() - 1)
                + (validNumber.charAt(validNumber.length() - 1) == '0' ? '9' : '0');

        mockMvc.perform(get("/api/v1/accounts/lookup")
                        .header("Authorization", "Bearer " + read(customer, "$.accessToken"))
                        .param("number", corrupted))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_NUMBER"));
    }

    @Test
    @DisplayName("§8 : pré-contrôle d'un numéro valide → 200 + nom masqué « M*** D*** » (CA-KYC-01)")
    void lookup_returns_masked_name_for_valid_number() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        MvcResult account = openAccount(tellerToken, customerId(customer), "CURRENT", "XOF");
        String accountNumber = read(account, "$.accountNumber");

        mockMvc.perform(get("/api/v1/accounts/lookup")
                        .header("Authorization", "Bearer " + read(customer, "$.accessToken"))
                        .param("number", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedName").value("M*** D***"));
    }

    // ── PATCH /accounts/{id}/status — §5 (ADMIN) — RG-ACC-04/05, F-13 ────

    @Test
    @DisplayName("§5 : TELLER ne peut pas modifier le statut — 403 (matrice §5)")
    void teller_cannot_change_status_403() throws Exception {
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\",\"motif\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("§8/RG-ACC-04 : ADMIN gèle un compte → 200 + statut FROZEN")
    void admin_freezes_account_200() throws Exception {
        String adminToken = adminToken();
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\",\"motif\":\"Solde investigué\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        Account account = accountRepository.findById(UUID.fromString(accountId)).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    @DisplayName("§8/RG-ACC-04 : ADMIN dégèle — FROZEN → ACTIVE")
    void admin_unfreeze_returns_to_active() throws Exception {
        String adminToken = adminToken();
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\",\"motif\":\"Solde investigué\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("§8/RG-ACC-04/05 : ADMIN clôture — closed_at positionné")
    void admin_close_account_sets_closed_at() throws Exception {
        String adminToken = adminToken();
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\",\"motif\":\"Liquidation du compte\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        Account account = accountRepository.findById(UUID.fromString(accountId)).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(account.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("§8 : ADMIN sans motif → 400 VALIDATION_FAILED (errors.motif — F-13)")
    void admin_cannot_close_without_motif_400() throws Exception {
        String adminToken = adminToken();
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.motif").isNotEmpty());
    }

    @Test
    @DisplayName("§8/RG-ACC-05 : clôture avec solde non nul → 409 ILLEGAL_STATE_TRANSITION")
    void admin_cannot_close_account_with_nonzero_balance_409() throws Exception {
        String adminToken = adminToken();
        String tellerToken = tellerToken();
        MvcResult customer = registerCustomer(uniqueEmail());
        String accountId = read(openAccount(tellerToken, customerId(customer), "CURRENT", "XOF"), "$.id");

        Account account = accountRepository.findById(UUID.fromString(accountId)).orElseThrow();
        account.setBalance(BigDecimal.valueOf(10));
        accountRepository.save(account);

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\",\"motif\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("RG-ACC-06 : statut des comptes systèmes — ADMIN → 409 ILLEGAL_STATE_TRANSITION")
    void admin_cannot_freeze_system_account_409() throws Exception {
        String adminToken = adminToken();
        String systemId = accountRepository.findAllBySystemCode(SystemCode.CASH_VAULT).get(0).getId().toString();

        mockMvc.perform(patch("/api/v1/accounts/" + systemId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\",\"motif\":\"test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    }
}
