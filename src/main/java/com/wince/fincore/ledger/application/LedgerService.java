package com.wince.fincore.ledger.application;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.repository.AccountRepository;
import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.LedgerEntry;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.ledger.repository.LedgerEntryRepository;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle du grand livre — {@link #apply(FinancialTransaction, List)} (F-04 — RG-LED-01 à 07).
 *
 * <p><strong>{@link #apply} — une seule transaction de base de données (RG-LED-01) :</strong>
 * la transaction métier + les écritures + la MAJ du solde dénormalisé
 * ({@code account.balance}) + le chaînage de hachages — tout COMMIT ou rien.
 * Le {@code LEDGER_UNBALANCED} (RG-LED-02) est levé en code, avant tout
 * {@code INSERT}.
 *
 * <p><strong>Verrouillage (RG-LED-06) :</strong> {@code SELECT … FOR UPDATE} sur
 * les comptes — {@link AccountRepository#findByIdForUpdate} — dans l'ordre
 * croissant de l'identifiant — vérifié en intégration par
 * {@code PaymentEndpointTest.concurrent_operations_balance_zero}
 * (CA-LED-04 — 100 % reproductible — non-déterministe sur la concurrence).
 *
 * <p><strong>Chaînage (RG-LED-05) :</strong> {@code entry_hash = SHA-256(prev_hash ‖
 * transaction_id ‖ account_id ‖ direction ‖ amount ‖ currency ‖ created_at)} —
 * calculé en Java — parité avec le SQL {@code V5__create_financial_transaction.sql}
 * (l'ordre de concaténation est documenté dans V5).
 *
 * <p><strong>Immuabilité (RG-LED-03) :</strong> triggers PostgreSQL —
 * {@code trg_ledger_entry_update_immutable} — {@code RAISE EXCEPTION} —
 * préservé par
 * {@code PaymentEndpointTest.direct_sql_update_on_ledger_entry_fails_ca_led_02}.
 *
 * <p><strong>{@link #verify(UUID)} — préservation (CA-LED-03) :</strong> chaîne de
 * hachages recomputée + {@code balance_after} (RG-LED-07) — <em>modelée comme
 * {@code ok + errors} — le client {@code GET /api/v1/admin/ledger/verify}
 * renvoie 200 avec {@code ok=false} si altération — le test écrit l'altération
 * par {@code INSERT} — {@code UPDATE} étant bloqué par le trigger.</em>
 */
@Service
public class LedgerService {

    /** Valeur initiale du chaînage (§6.1) — 64 zéros. */
    public static final String ZERO_HASH = "0".repeat(64);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final Clock clock;
    private final EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository,
                         AccountRepository accountRepository,
                         FinancialTransactionRepository financialTransactionRepository,
                         Clock clock,
                         EntityManager entityManager,
                         org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.clock = clock;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }

    // ── F-04 — RG-LED-01 à 07 — apply — l'orchestration du grand livre ──

    /**
     * Poste une {@code FinancialTransaction} + ses écritures — une seule
     * transaction de base de données (RG-LED-01) — {@code @Transactional}.
     *
     * <p>Pour chaque devise — {@code Σ DEBIT = Σ CREDIT} (RG-LED-02).
     * <p>Verrouillage — ordre croissant des identifiants (RG-LED-06).
     * <p>{@code balance_after} — {@code balance = Σ crédits − Σ débits} (RG-LED-04/07).
     */
    @Transactional
    public FinancialTransaction apply(FinancialTransaction transaction, List<LedgerMovement> movements) {
        // RG-LED-02 : Σ DEBIT = Σ CREDIT par devise — en code — avant tout INSERT.
        Map<Currency, BigDecimal> debits = new HashMap<>();
        Map<Currency, BigDecimal> credits = new HashMap<>();
        for (LedgerMovement movement : movements) {
            (movement.direction() == Direction.DEBIT ? debits : credits)
                    .merge(movement.currency(), movement.amount(), BigDecimal::add);
        }
        Set<Currency> currencies = new LinkedHashSet<>();
        currencies.addAll(debits.keySet());
        currencies.addAll(credits.keySet());
        for (Currency currency : currencies) {
            BigDecimal debit = debits.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal credit = credits.getOrDefault(currency, BigDecimal.ZERO);
            if (debit.compareTo(credit) != 0) {
                throw new FinCoreException(ErrorCode.LEDGER_UNBALANCED,
                        "La transaction déséquilibre le grand livre (RG-LED-02) — Σ DEBIT "
                                + debit + " ≠ Σ CREDIT " + credit + " (" + currency.name() + ").");
            }
        }

        // RG-LED-06 : verrouillage — tri par identifiant croissant — SELECT … FOR UPDATE.
        List<UUID> accountIds = movements.stream().map(LedgerMovement::accountId).distinct().toList();
        lockAccounts(accountIds);
        Map<UUID, Account> accounts = new LinkedHashMap<>();
        for (UUID id : accountIds) {
            Account acc = accountRepository.findById(id).orElseThrow(
                    () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
            entityManager.refresh(acc);
            accounts.put(id, acc);
        }

        // RG-LED-07 : solde courant par compte — MAJ dans la même transaction.
        Map<UUID, BigDecimal> balances = new HashMap<>();
        accounts.values().forEach(account -> balances.put(account.getId(), account.getBalance()));
        // RG-LED-05 : dernier hachage par compte — dernière écriture — ZERO_HASH si aucune.
        Map<UUID, String> lastHashes = new HashMap<>();
        accounts.keySet().forEach(id -> lastHashes.put(id,
                ledgerEntryRepository.findTop1ByAccountIdOrderByIdDesc(id)
                        .map(LedgerEntry::getEntryHash)
                        .orElse(ZERO_HASH)));

        financialTransactionRepository.save(transaction);

        // Chaînage de hachages — entry_hash, balance_after — RG-LED-04/05.
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (LedgerMovement movement : movements) {
            Account account = accounts.get(movement.accountId());
            BigDecimal net = movement.direction() == Direction.DEBIT
                    ? movement.amount().negate()
                    : movement.amount();
            BigDecimal balanceAfter = balances.merge(movement.accountId(), net, BigDecimal::add);
            account.setBalance(balanceAfter);

            String prevHash = lastHashes.get(movement.accountId());
            String entryHash = computeHash(prevHash, transaction.getId(), movement.accountId(),
                    movement.direction(), movement.amount(), movement.currency(), createdAt);
            LedgerEntry entry = new LedgerEntry(transaction.getId(), movement.accountId(),
                    movement.direction(), movement.amount(), movement.currency(), balanceAfter, prevHash, entryHash);
            entry.setCreatedAt(createdAt);
            ledgerEntryRepository.save(entry);
            lastHashes.put(movement.accountId(), entryHash);
        }
        transaction.setCompletedAt(createdAt);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TransactionCompletedEvent(transaction.getId()));
        }
        return transaction;
    }

    // ── F-04 — RG-LED-05 — la fonction de hachage canonique ────────────

    /**
     * Verrouillage — {@code RG-LED-06} — tri par identifiant croissant —
     * {@code SELECT … FOR UPDATE} — requête partagée : utilisée par
     * {@link #apply} et par {@link WithdrawalService} (F-05) pour
     * vérifier sous-verrou le {@code INSUFFICIENT_FUNDS} (RG-DEP-02).
     */
    public void lockAccounts(Collection<UUID> accountIds) {
        for (UUID id : accountIds.stream().sorted().toList()) {
            try {
                entityManager.createNativeQuery("SELECT 1 FROM account WHERE id = :id FOR UPDATE")
                        .setParameter("id", id)
                        .getSingleResult();
            } catch (jakarta.persistence.NoResultException e) {
                throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable.");
            }
        }
    }

    /**
     * Calcul du hash d'écriture — {@code entry_hash} (§6.1).
     *
     * <p>Parité Java/SQL — {@code V5__create_financial_transaction.sql} —
     * concaténation simple dans l'ordre documenté —
     * {@code SHA-256(prev_hash ‖ transaction_id ‖ account_id ‖ direction ‖ amount ‖ currency ‖ created_at)}.
     *
     * <p>{@code amount} normalisé — {@code stripTrailingZeros} — <em>car l'entrée
     * lue en base (NUMERIC(19,4)) et le montant du mouvement peuvent différer
     * d'échelle</em> — le chaînage doit être repérable avec la valeur sémantique.
     */
    public static String computeHash(String prevHash, UUID transactionId, UUID accountId,
            Direction direction, BigDecimal amount, Currency currency, Instant createdAt) {
        String payload = prevHash
                + transactionId.toString()
                + accountId.toString()
                + direction.name()
                + amount.stripTrailingZeros().toPlainString()
                + currency.name()
                + createdAt.toString();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 doit être disponible sur tout JDK", e);
        }
    }

    // ── F-04 — CA-LED-03 — préservation (GET /api/v1/admin/ledger/verify) ──

    /** Résultat de {@link #verify} — {@code ok + errors} — 200 + errors à l'API. */
    public record VerificationResult(UUID accountId, boolean ok, List<String> errors) {}

    /**
     * Vérification du chaînage de hachages + solde dénormalisé (CA-LED-03 — F-04).
     *
     * <p>Rejoue la chaîne — {@code entry_hash} recalculé depuis
     * {@code ZERO_HASH} + {@code prev_hash} — vérifie le solde
     * {@link com.wince.fincore.account.domain.Account#getBalance()} ==
     * {@code Σ crédits − Σ débits} des écritures (RG-LED-07) — retourne la
     * liste des erreurs {@code []} si la chaîne est propre.
     */
    public VerificationResult verify(UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable."));
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdOrderById(accountId);
        List<String> errors = new ArrayList<>();

        String prevHash = ZERO_HASH;
        BigDecimal net = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            String expected = computeHash(prevHash, entry.getTransactionId(), accountId,
                    entry.getDirection(), entry.getAmount(), entry.getCurrency(), entry.getCreatedAt());
            if (!expected.equals(entry.getEntryHash())) {
                errors.add("Écriture #" + entry.getId() + " — chaîne de hachages cassée (RG-LED-05)"
                        + " — attendu=" + expected + " — enregistré=" + entry.getEntryHash());
            }
            prevHash = entry.getEntryHash();
            net = net.add(entry.getDirection() == Direction.CREDIT
                    ? entry.getAmount()
                    : entry.getAmount().negate());
        }
        if (account.getBalance().compareTo(net) != 0) {
            errors.add("Solde dénormalisé (RG-LED-07) : compte=" + account.getBalance().toPlainString()
                    + " vs Σ crédits − Σ débits=" + net.toPlainString());
        }
        return new VerificationResult(accountId, errors.isEmpty(), errors);
    }
}
