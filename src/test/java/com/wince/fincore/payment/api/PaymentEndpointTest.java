package com.wince.fincore.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wince.fincore.TestcontainersConfiguration;
import com.wince.fincore.account.api.OpenAccountRequest;
import com.wince.fincore.account.application.AccountService;
import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.domain.AppUser;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.identity.repository.AppUserRepository;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.LedgerEntry;
import com.wince.fincore.ledger.repository.LedgerEntryRepository;
import com.wince.fincore.payment.application.DepositService;
import com.wince.fincore.payment.application.WithdrawalService;
import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration F-05 — preuve par le HTTP des critères
 * d'acceptation et des règles (§8, §5) :
 *
 * <ul>
 *   <li><b>CA-DEP-01</b> : dépôt de 50 000 XOF → solde 0 → 50 000 — 2 écritures — CASH_VAULT −50 000 + client +50 000 ;</li>
 *   <li><b>CA-DEP-02</b> : retrait supérieur au solde → 422 INSUFFICIENT_FUNDS — 0 écriture ;</li>
 *   <li>RG-DEP-03 — échelle XOF — 400 INVALID_AMOUNT_SCALE ; RG-DEP-04 — FROZEN → 409 ;</li>
 *   <li>Matrice §5 : CUSTOMER → 403 — TELLER → 201 ;</li>
 *   <li><b>CA-LED-02</b> : UPDATE SQL direct sur ledger_entry → exception — déclencheur — RG-LED-03 ;</li>
 *   <li><b>CA-LED-03</b> : GET /api/v1/admin/ledger/verify — ok=true — tamper détecté (INSERT) — ok=false ;</li>
 *   <li><b>CA-LED-04</b> : opérations concourantes — Σ balance de tous les comptes d'une devise = 0 ;</li>
 *   <li>RG-LED-01/02 — écritures atomiques — vérifiées via AccountService.open (Phase 4) + LedgerEntryRepository.</li>
 * </ul>
 *
 * <p>Base réelle (Testcontainers PostgreSQL) — « ne jamais mocker la base de données pour
 * les tests du grand livre » (§11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentEndpointTest {

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
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private DepositService depositService;
    @Autowired
    private WithdrawalService withdrawalService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;

    // ── Helpers ───────────────────────────────────────────────────────────

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
    private UUID customerId(MvcResult customer) throws Exception {
        return customerRepository.findByUserId(UUID.fromString(read(customer, "$.userId")))
                .orElseThrow(() -> new AssertionError("le client n'a pas été créé à l'inscription"))
                .getId();
    }

    /** Crée un TELLER, renvoie son jeton d'accès (§5 — ouvrir un compte). */
    private String tellerToken() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new AppUser(email, passwordEncoder.encode(STRONG_PASSWORD), Role.TELLER));
        return read(login(email), "$.accessToken");
    }

    /** Crée un ADMIN, renvoie son jeton d'accès (§5 — statut, vérification — CA-LED-03). */
    private String adminToken() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new AppUser(email, passwordEncoder.encode(STRONG_PASSWORD), Role.ADMIN));
        return read(login(email), "$.accessToken");
    }

    /** Crée un AUDITOR, renvoie son jeton d'accès (§5 — CA-LED-03). */
    private String auditorToken() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new AppUser(email, passwordEncoder.encode(STRONG_PASSWORD), Role.AUDITOR));
        return read(login(email), "$.accessToken");
    }

    /** Id de l'AppUser TELLER — `initiated_by` (§6.1) pour les services concrets (CA-LED-04). */
    private UUID tellerAppUserId() throws Exception {
        String email = uniqueEmail();
        return userRepository.save(new AppUser(email, passwordEncoder.encode(STRONG_PASSWORD), Role.TELLER)).getId();
    }

    /** Compte client — via {@link AccountService.open} (Phase 4 — RG-ACC-02 — KYC non REJECTED). */
    private Account openClientAccount(String email) throws Exception {
        return accountService.open(new OpenAccountRequest(customerId(registerCustomer(email)), AccountKind.CURRENT, Currency.XOF));
    }

    /** Lecture d'un champ de la réponse sans bibliothèque JSON lourde. */
    private static String read(MvcResult result, String jsonPath) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), jsonPath);
        return String.valueOf(value);
    }

    private static String uniqueEmail() {
        return "pay" + UUID.randomUUID() + "@fincore.test";
    }

    // ── F-05 — CA-DEP-01 — dépôt — 2 écritures ────────────────────────────

    @Test
    @DisplayName("CA-DEP-01 : dépôt de 50 000 XOF → solde 0 → 50 000 — 2 écritures — CASH_VAULT −50 000, client +50 000")
    void ca_dep_01_deposit_50000_xof_creates_2_entries_and_balance() throws Exception {
        String tellerToken = tellerToken();
        Account account = openClientAccount(uniqueEmail());

        MvcResult result = mockMvc.perform(post("/api/v1/deposits")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account.getId() + "\",\"amount\":50000,\"description\":\"Dépôt initial\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        assertThat(read(result, "$.amount")).isEqualTo("50000");

        // RG-LED-07 — dénormalisation — solde = Σ crédits − Σ débits.
        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        // RG-LED-01/02 — écritures — débit CASH_VAULT / crédit client — Σ DEBIT = Σ CREDIT.
        List<LedgerEntry> clientEntries = ledgerEntryRepository.findByAccountIdOrderById(account.getId());
        System.out.println("DEBUG ca_dep_01 — account=" + account.getId() + " balance=" + updated.getBalance()
                + " size=" + clientEntries.size()
                + " entries=" + clientEntries.stream().map(e -> e.getId() + "/" + e.getAccountId() + "/" + e.getDirection() + "/" + e.getAmount()).toList());
        assertThat(clientEntries).hasSize(1);
        assertThat(clientEntries.get(0).getDirection()).isEqualTo(Direction.CREDIT);
        assertThat(clientEntries.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(clientEntries.get(0).getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        Account cashVault = accountRepository.findBySystemCodeAndCurrency(SystemCode.CASH_VAULT, Currency.XOF).orElseThrow();
        List<LedgerEntry> vaultEntries = ledgerEntryRepository.findByAccountIdOrderById(cashVault.getId());
        System.out.println("DEBUG ca_dep_01 — vault=" + cashVault.getId() + " balance=" + cashVault.getBalance()
                + " size=" + vaultEntries.size()
                + " entries=" + vaultEntries.stream().map(e -> e.getId() + "/" + e.getDirection() + "/" + e.getAmount() + "/" + e.getBalanceAfter()).toList());
        assertThat(vaultEntries).hasSize(1);
        assertThat(vaultEntries.get(0).getDirection()).isEqualTo(Direction.DEBIT);
        assertThat(vaultEntries.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(vaultEntries.get(0).getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(-50000));
    }

    // ── F-05 — CA-DEP-02 — retrait > solde — 0 écriture ───────────────────

    @Test
    @DisplayName("CA-DEP-02 : retrait supérieur au solde → 422 INSUFFICIENT_FUNDS — 0 écriture — aucune trace")
    void ca_dep_02_withdrawal_above_balance_refused_without_entries() throws Exception {
        String tellerToken = tellerToken();
        Account account = openClientAccount(uniqueEmail());

        // Solde 0 — retrait impossible — CA-DEP-02.
        mockMvc.perform(post("/api/v1/withdrawals")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account.getId() + "\",\"amount\":100}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        // RG-LED-01 — aucune écriture persistante — compte inchangé.
        assertThat(ledgerEntryRepository.findByAccountIdOrderById(account.getId())).isEmpty();
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── F-05 — RG-DEP-03/04 — échelle + compte gelé ──────────────────────

    @Test
    @DisplayName("RG-DEP-03 : échelle XOF — 1,50 → 400 INVALID_AMOUNT_SCALE")
    void rg_dep_03_amount_with_scale_xof_is_rejected() throws Exception {
        String tellerToken = tellerToken();
        Account account = openClientAccount(uniqueEmail());

        mockMvc.perform(post("/api/v1/deposits")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account.getId() + "\",\"amount\":1.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT_SCALE"));
    }

    @Test
    @DisplayName("RG-DEP-04 : dépôt sur compte FROZEN → 409 ACCOUNT_NOT_ACTIVE")
    void rg_dep_04_deposit_on_frozen_account_is_rejected() throws Exception {
        String tellerToken = tellerToken();
        Account account = openClientAccount(uniqueEmail());
        Account frozen = accountRepository.findById(account.getId()).orElseThrow();
        frozen.setStatus(AccountStatus.FROZEN);
        accountRepository.save(frozen);

        mockMvc.perform(post("/api/v1/deposits")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account.getId() + "\",\"amount\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    // ── Matrice §5 — CUSTOMER 403 — TELLER 201 ───────────────────────────

    @Test
    @DisplayName("Matrice §5 : CUSTOMER POST /deposits → 403 — TELLER → 201 — (§5 — aucun CUSTOMER — TELLER uniquement)")
    void matrice_customer_403_teller_201() throws Exception {
        MvcResult customer = registerCustomer(uniqueEmail());
        String customerToken = read(customer, "$.accessToken");
        Account account = openClientAccount(uniqueEmail());
        String tellerToken = tellerToken();

        // CUSTOMER → 403 (TELLER seul — §5).
        mockMvc.perform(post("/api/v1/deposits")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account.getId() + "\",\"amount\":100}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // TELLER → 201.
        mockMvc.perform(post("/api/v1/deposits")
                        .header("Authorization", "Bearer " + tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account.getId() + "\",\"amount\":100}"))
                .andExpect(status().isCreated());
    }

    // ── F-04 — CA-LED-02 — immuabilité — triggers — RG-LED-03 ────────────

    @Test
    @Transactional  // — EM dans une transaction — update/delete natifs — trigger
    @DisplayName("CA-LED-02 : UPDATE direct sur ledger_entry (SQL) → exception du déclencheur")
    void direct_sql_update_on_ledger_entry_fails_rg_led_03() throws Exception {
        Account account = openClientAccount(uniqueEmail());
        depositService.deposit(new DepositCommand(account.getId(), BigDecimal.valueOf(100000), null), tellerAppUserId());

        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdOrderById(account.getId());
        assertThat(entries).hasSize(1);
        LedgerEntry entry = entries.get(0);

        // RG-LED-03 — UPDATE direct sur ledger_entry → exception explicite — trigger.
        assertThatThrownBy(() -> entityManager.createNativeQuery(
                        "UPDATE ledger_entry SET balance_after = 999 WHERE id = " + entry.getId()).executeUpdate())
                .hasMessageContaining("RG-LED-03");
        // RG-LED-03 — DELETE direct → exception explicite — trigger.
        assertThatThrownBy(() -> entityManager.createNativeQuery(
                        "DELETE FROM ledger_entry WHERE id = " + entry.getId()).executeUpdate())
                .hasMessageContaining("RG-LED-03");
    }

    // ── F-04 — CA-LED-03 — préservation — GET /api/v1/admin/ledger/verify ─

    @Test
    @DisplayName("CA-LED-03 : verify() ok=true — ADMIN + AUDITOR → 200 — et tamper détecté (INSERT) — ok=false")
    void verify_ok_and_tamper_detected_ca_led_03() throws Exception {
        Account account = openClientAccount(uniqueEmail());
        depositService.deposit(new DepositCommand(account.getId(), BigDecimal.valueOf(50000), null), tellerAppUserId());

        // Ok sur la chaîne cohérente — ADMIN + AUDITOR (§5).
        for (String token : List.of(adminToken(), auditorToken())) {
            MvcResult verify = mockMvc.perform(get("/api/v1/admin/ledger/verify")
                            .header("Authorization", "Bearer " + token)
                            .param("accountId", account.getId().toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            List<?> errors = JsonPath.read(verify.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.errors");
            System.out.println("DEBUG verify_ok — ok=" + read(verify, "$.ok") + " errors=" + errors + " body=" + verify.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(read(verify, "$.ok")).isEqualTo("true");
            assertThat(errors).as("errors — chaîne de hachages + solde — ok=true attendu").isEmpty();
        }

        // Tamper — INSERT d'une écriture altérée — simulate (UPDATE bloqué par le trigger — RG-LED-03).
        LedgerEntry realEntry = ledgerEntryRepository.findByAccountIdOrderById(account.getId()).get(0);
        LedgerEntry tampered = new LedgerEntry(UUID.randomUUID(), account.getId(), Direction.CREDIT,
                BigDecimal.valueOf(100), Currency.XOF, BigDecimal.valueOf(100),
                realEntry.getEntryHash(), "0".repeat(64));
        tampered.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(tampered);

        mockMvc.perform(get("/api/v1/admin/ledger/verify")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("accountId", account.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    // ── F-04 — CA-LED-04 — concurrence — Σ balance = 0 ────────────────────

    @Test
    @DisplayName("CA-LED-04 : opérations concurrentes — Σ balance de tous les comptes d'une devise = 0")
    void concurrent_operations_balance_zero() throws Exception {
        UUID tellerId = tellerAppUserId();
        Account account = openClientAccount(uniqueEmail());
        // Pré-charge — CA-DEP-01 — pour que les retraits aient du sang.
        depositService.deposit(new DepositCommand(account.getId(), BigDecimal.valueOf(100000), null), tellerId);

        int threads = 8;
        int perThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            futures.add(executor.submit(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perThread; i++) {
                    try {
                        if ((i + seed) % 2 == 0) {
                            depositService.deposit(new DepositCommand(account.getId(), BigDecimal.valueOf(1000), null), tellerId);
                        } else {
                            withdrawalService.withdraw(new WithdrawalCommand(account.getId(), BigDecimal.valueOf(500), null), tellerId);
                        }
                    } catch (RuntimeException ignored) {
                        // INSUFFICIENT_FUNDS attendu — refusé — aucune écriture — invariant.
                    }
                }
            }));
        }
        startGate.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Invariant — Σ balance de tous les comptes d'une devise — client + système.
        BigDecimal sum = accountRepository.findAll().stream()
                .filter(a -> a.getCurrency() == Currency.XOF)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        accountRepository.findAll().stream().filter(a -> a.getCurrency() == Currency.XOF)
                .forEach(a -> {
                    BigDecimal entrySum = ledgerEntryRepository.findByAccountIdOrderById(a.getId()).stream()
                            .map(e -> e.getDirection() == Direction.CREDIT ? e.getAmount() : e.getAmount().negate())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    System.out.println("DEBUG concurrent — account=" + a.getId() + " number=" + a.getAccountNumber()
                            + " balance=" + a.getBalance() + " ledgerSum=" + entrySum
                            + (a.getBalance().compareTo(entrySum) != 0 ? " ← DIFF" : ""));
                });
        System.out.println("DEBUG concurrent — sum=" + sum);
        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
