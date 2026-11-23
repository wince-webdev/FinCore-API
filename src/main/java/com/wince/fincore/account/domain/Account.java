package com.wince.fincore.account.domain;

import com.wince.fincore.shared.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité JPA {@code account} — table créée par {@code V4__create_account.sql}
 * (cahier §6.1, Phase 4, F-03).
 *
 * <p><strong>Pourquoi l'entité « classe vide » style ({@link #Account()} vide) ?
 * </strong> — JPA exige un constructeur par défaut pour l'instanciation
 * réflexive ; les données véritables arrivent via
 * {@link #Account(UUID, String, UUID, AccountKind, SystemCode, Currency, BigDecimal)}.
 * Le {@code version} est une propriété simple ({@code BIGINT}) — le
 * verrou optimiste JPA est activé à la Phase 5, quand la partie double
 * écrit le solde (F-04/RG-LED-06) — Phase 4 ne déplace encore aucun
 * solde (dépôts = F-05).
 *
 * <p><strong>État mutable :</strong> le solde est positionné à 0 à
 * l'ouverture, modifié par {@link AccountService} (gel/clôture) — les
 * écritures de mouvements viennent en Phase 5 (F-04/F-05 — RG-LED-07 :
 * solde = Σ crédits − Σ débits).
 *
 * <p>{@link #isSystem()} — l'identité d'un compte système est {@code system_code}
 * non null — elle veille à ce que les statuts systèmes ne soient modifiables
 * que par le processus (RG-ACC-06).
 */
@Entity
@Table(name = "account")
public class Account {

    /** Nécessaire pour JPA — jamais utilisé directement (§2.2). */
    protected Account() {
    }

    /**
     * Création à l'ouverture — F-03 / RG-ACC-01 à 07 : statut {@code ACTIVE},
     * solde zéro, {@code openedAt} fixé par le service ({@code Clock}).
     *
     * @param id UUID, {@code accountNumber} avec clé mod 97, {@code customerId}
     *           NULL pour les comptes systèmes, {@code systemCode} non null
     *           pour les systèmes
     * @param balance solde initial — {@code Money.zero(currency).amount()}
     */
    public Account(UUID id, String accountNumber, UUID customerId, AccountKind kind,
                   SystemCode systemCode, Currency currency, BigDecimal balance) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.kind = kind;
        this.systemCode = systemCode;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
        this.balance = balance;
        this.version = 0L;
    }

    // ── Champs (cahier §6.1) ──────────────────────────────────────────────

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Numéro de compte — {@code BJ} + 10 chiffres + clé mod 97 — RG-ACC-03. */
    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    /** FK vers {@code customer} — NULL pour les comptes systèmes (§6.1). */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Type de compte — CURRENT/SAVINGS/WALLET/SYSTEM (RG-ACC-01). */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private AccountKind kind;

    /** Catégorie système — NULL pour les comptes clients. */
    @Enumerated(EnumType.STRING)
    @Column(name = "system_code")
    private SystemCode systemCode;

    /** Devises : XOF (défaut), EUR, USD (RG-ACC-01). */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    /** Statuts : ACTIVE/FROZEN/CLOSED (RG-ACC-04). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    /** Solde courant — §4.3 : {@code solde = Σ crédits − Σ débits} (F-04). */
    @Column(name = "balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal balance;

    /** Verrou optimiste — BIGINT — usage Phase 5 (F-04, RG-LED-06). */
    @Column(name = "version", nullable = false)
    private Long version;

    /** Quand le compte a été ouvert — DB {@code now()} par défaut. */
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    /** Date de clôture — NULL tant que le compte n'est pas {@code CLOSED}. */
    @Column(name = "closed_at")
    private Instant closedAt;

    // ── Accesseurs ────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public AccountKind getKind() {
        return kind;
    }

    public SystemCode getSystemCode() {
        return systemCode;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    // ── Identification (RG-ACC-06, CA-KYC-01) ─────────────────────────────

    /** Compte système — {@code system_code} non null (caisse, frais, compensation…). */
    public boolean isSystem() {
        return systemCode != null;
    }

    // ── Mutations (service — AccountService) ──────────────────────────────

    /** Écrit {@code openedAt} — appelé par le service à l'ouverture (§6.1). */
    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    /** Transitions de statut — appelées par {@code AccountService.changeStatus}. */
    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    /** Positionné à la clôture — {@link AccountService#changeStatus}. */
    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    /** Solde — positionné par le service (Phase 5) et les fichiers. */
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
