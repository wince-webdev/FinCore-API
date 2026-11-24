package com.wince.fincore.account.application;

import com.wince.fincore.account.api.AccountLookupResponse;
import com.wince.fincore.account.api.AccountStatusUpdateRequest;
import com.wince.fincore.account.api.OpenAccountRequest;
import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.domain.KycStatus;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.identity.domain.Role;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.domain.Money;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage F-03 — comptes (§8 — catalogue des endpoints, Phase 4).
 *
 * <p>Responsabilités :
 * <ul>
 *   <li>ouvrir un compte pour un client (§8, TELLER/ADMIN — RG-ACC-01 à 07) :
 *       {@link #open(OpenAccountRequest)} — validation, génération du
 *       numéro mod 97, solde à zéro, statut {@code ACTIVE};</li>
 *   <li>{@link #findByCustomerId} — « mes comptes » (§8, CUSTOMER) ;</li>
 *   <li>{@link #findById} — détail avec la règle §5 (404 pour les autres) —
 *       CUSTOMER lue son compte, ADMIN tout compte — CA-ACC-02 ;</li>
 *   <li>{@link #changeStatus} — geler / dégeler / clôturer (§8, ADMIN —
 *       RG-ACC-04, F-13) ;</li>
 *   <li>{@link #lookupBeneficiary} — pré-contrôle bénéficiaire (§8,
 *       F-02 / F-06).</li>
 * </ul>
 *
 * <p><strong>Design — visibilité :</strong> la matrice §5 est déclarée en
 * SpEL — « un CUSTOMER ne lit que ses comptes — autre client → 404 »
 * (CA-ACC-02) est une règle <em>impliquant le contexte courant</em> — le
 * rôle et l'identifiant sont passés en paramètre du service (§5 —
 * « non-révélation d'une ressource » — exemple de la Phase 2).
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    // ── F-03 / §8 — Ouvrir un compte (TELLER, ADMIN) ─────────────────────

    /**
     * Ouvre un compte pour un client — F-03 (§8, RG-ACC-01 à 07).
     *
     * <ul>
     *   <li>{@code SYSTEM} par l'API → 400 — créé au démarrage (RG-ACC-06) ;</li>
     *   <li>client inconnu → 404 — §5 ;</li>
     *   <li>client rejeté par le KYC → 400 — RG-ACC-02 ;</li>
     *   <li>couple (type, devise) existant → 400 — RG-ACC-02 ;</li>
     *   <li>solde ouvert = zéro, statut {@code ACTIVE}, numéro avec clé mod 97 — RG-ACC-03
     *       ({@link AccountNumberGenerator#generate()}).
     * </ul>
     */
    public Account open(OpenAccountRequest request) {
        // RG-ACC-01 : « Devises : XOF (défaut), EUR, USD » — XOF quand le DTO ne le fournit pas.
        Currency currency = request.currency() == null ? Currency.XOF : request.currency();
        if (request.kind() == AccountKind.SYSTEM) {
            // RG-ACC-06 : comptes systèmes — créés au démarrage (seed en migration).
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
                    "Les comptes systèmes (caisse, frais, compensation…) sont créés au démarrage (RG-ACC-06) — pas d'ouverture par l'API.");
        }
        Customer customer = customerRepository.findById(request.customerId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Client introuvable."));
        if (customer.getKycStatus() == KycStatus.REJECTED) {
            // RG-ACC-02 : client rejeté par le KYC — son compte ne peut être ouvert.
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
                    "Le client est rejeté par le KYC (RG-ACC-02) — le compte ne peut être ouvert.");
        }
        if (accountRepository.findByCustomerIdAndKindAndCurrency(
                request.customerId(), request.kind(), currency).isPresent()) {
            // RG-ACC-02 : un seul compte par couple (type, devise).
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
                    "Un compte " + request.kind() + " en " + currency + " existe déjà pour ce client (RG-ACC-02).");
        }
        Account account = new Account(
                UUID.randomUUID(), generateUniqueAccountNumber(), request.customerId(),
                request.kind(), null, currency, Money.zero(currency).amount());
        account.setOpenedAt(clock.instant());
        return accountRepository.save(account);
    }

    /** « Mes comptes » — {@code GET /accounts} (CUSTOMER, §8). */
    public List<Account> findByCustomerId(UUID customerId) {
        return accountRepository.findByCustomerId(customerId);
    }

    /**
     * Compte visible selon le rôle courant — {@code GET /accounts/{id}}
     * (CUSTOMER, ADMIN — §8).
     *
     * <p>CUSTOMER n'a vu que ses comptes — système ou d'un autre client —
     * sinon {@code 404 RESOURCE_NOT_FOUND} (§5, CA-ACC-02 — le non-révélation
     * de l'existence d'une ressource). ADMIN peut lire n'importe quel compte.
     *
     * @param currentCustomerId — id du {@code Customer} lié à l'utilisateur
     *        authentifié — {@code account.customer_id} (§6.1) — distinct de
     *        l'id de {@code app_user} (le JWT {@code sub}).
     */
    public Account findById(UUID id, Role role, UUID currentCustomerId) {
        Account account = accountRepository.findById(id).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        if (role == Role.CUSTOMER
                && (account.isSystem()
                        || currentCustomerId == null
                        || account.getCustomerId() == null
                        || !account.getCustomerId().equals(currentCustomerId))) {
            throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable.");
        }
        return account;
    }

    // ── F-03 / F-13 — Statut du compte (ADMIN, §8) ────────────────────────

    /**
     * Geler / dégeler / clôturer — {@code PATCH /accounts/{id}/status}
     * (ADMIN — §8).
     *
     * <p>États valides (RG-ACC-04) :
     * <ul>
     *   <li>{@code ACTIVE → FROZEN} : gel — motif obligatoire (F-13) ;</li>
     *   <li>{@code FROZEN → ACTIVE} : dégel (motif facultatif) ;</li>
     *   <li>{@code ACTIVE|FROZEN → CLOSED} : clôture — motif obligatoire
     *       (F-13) + solde à zéro (RG-ACC-05).</li>
     * </ul>
     *
     * <p>État définitif : {@code CLOSED} — plus aucune transition —
     * {@code ILLEGAL_STATE_TRANSITION} (RG-ACC-04). Comptes systèmes —
     * {@code ILLEGAL_STATE_TRANSITION} (RG-ACC-06).
     */
    @Transactional
    public Account changeStatus(UUID id, AccountStatusUpdateRequest request) {
        Account account = accountRepository.findById(id).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        if (account.isSystem()) {
            throw new FinCoreException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Le statut des comptes systèmes (caisse, frais, compensation…) ne peut être modifié par l'API (RG-ACC-06).");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            // RG-ACC-04 : statut définitif — aucune transition sortante.
            throw new FinCoreException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Compte déjà clôturé — statut définitif (RG-ACC-04).");
        }
        AccountStatus target = request.status();
        // F-13 : gel et clôture — motif obligatoire.
        if (target == AccountStatus.FROZEN || target == AccountStatus.CLOSED) {
            requireMotif(request);
        }
        if (target == AccountStatus.CLOSED && !isZeroBalance(account)) {
            // RG-ACC-05 : clôture uniquement avec un solde à zéro.
            throw new FinCoreException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Le solde du compte n'est pas à 0 (RG-ACC-05) — la clôture est impossible.");
        }
        if (target == account.getStatus()) {
            return account; // Idempotent — rien à faire.
        }
        account.setStatus(target);
        if (target == AccountStatus.CLOSED) {
            account.setClosedAt(clock.instant());
        }
        return accountRepository.save(account);
    }

    // ── Pré-contrôle bénéficiaire (§8, F-02 / F-06) ──────────────────────

    /**
     * Pré-contrôle d'un numéro de compte — {@code GET /accounts/lookup?number=}
     * (§8, F-06 — F-03).
     *
     * <p>Valide le format + la clé (RG-ACC-03 — CA-ACC-01 : 400
     * {@code INVALID_ACCOUNT_NUMBER}), puis renvoie le <strong>nom masqué</strong>
     * du détenteur — « {@code J*** D***} » (F-06, RG-KYC-03) — « n'expose
     * que son nom masqué ».
     */
    public AccountLookupResponse lookupBeneficiary(String accountNumber) {
        if (!AccountNumberGenerator.isValid(accountNumber)) {
            throw new FinCoreException(ErrorCode.INVALID_ACCOUNT_NUMBER,
                    "Le numéro de compte est invalide — vérifiez la clé de contrôle (RG-ACC-03).");
        }
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        if (account.isSystem()) {
            // Les comptes systèmes n'ont pas de bénéficiaire (customerId NULL).
            throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable.");
        }
        Customer customer = customerRepository.findById(account.getCustomerId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        return AccountLookupResponse.of(customer.getFirstName(), customer.getLastName());
    }

    // ── Internes ─────────────────────────────────────────────────────────

    /** Génère un numéro unique — retentions de collision (§6.2 arbitre final). */
    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = AccountNumberGenerator.generate();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new FinCoreException(ErrorCode.INTERNAL_ERROR,
                "Impossible de générer un numéro de compte unique (RG-ACC-03).");
    }

    /** Motif obligatoire pour FROZEN/CLOSED (F-13) — dans la réponse, pas en base (§6.1). */
    private static void requireMotif(AccountStatusUpdateRequest request) {
        if (request.motif() == null || request.motif().isBlank()) {
            throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
                    "Le motif est obligatoire pour le statut " + request.status() + " (F-13).",
                    Map.of("errors", Map.of("motif", "obligatoire")));
        }
    }

    private static boolean isZeroBalance(Account account) {
        return account.getBalance() != null
                && account.getBalance().compareTo(BigDecimal.ZERO) == 0;
    }
}
