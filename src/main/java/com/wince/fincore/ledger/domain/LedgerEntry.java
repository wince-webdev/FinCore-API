package com.wince.fincore.ledger.domain;

import com.wince.fincore.shared.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité JPA {@code ledger_entry} — table créée par
 * {@code V5__create_financial_transaction.sql} (cahier §6.1, F-04).
 *
 * <p><strong>Immuable (RG-LED-03) — Triggers PostgreSQL :
 * {@code trg_ledger_entry_update_immutable} et
 * {@code trg_ledger_entry_delete_immutable} —
 * {@code RAISE EXCEPTION} explicite — correction par contre-passation (F-09 — Phase 6).</p>
 *
 * <p><strong>Chaînage de hachages (RG-LED-05) :</strong>
 * {@code entry_hash = SHA-256(prev_hash ‖ transaction_id ‖ account_id ‖ direction ‖ amount ‖ currency ‖ created_at)}
 * — calculé en Java dans {@link LedgerService#computeHash} — parité avec le SQL
 * (l'ordre de concaténation est documenté dans V5) — vérifiable via
 * {@code GET /admin/ledger/verify} (§8, F-04 — CA-LED-03).
 *
 * <p><strong>{@code balance_after} (RG-LED-04) :</strong> solde du compte juste
 * après l'écriture — MAJ par {@link LedgerService#apply} dans la même
 * transaction — dénormalisation également {@link com.wince.fincore.account.domain.Account#getBalance()}
 * (RG-LED-07).
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    /** Nécessaire pour JPA — jamais utilisé directement (§2.2). */
    protected LedgerEntry() {
    }

    /** Création par {@link LedgerService#apply} — {@code prev_hash} et {@code entry_hash} calculés en Java. */
    public LedgerEntry(UUID transactionId, UUID accountId, Direction direction,
                       BigDecimal amount, Currency currency, BigDecimal balanceAfter,
                       String prevHash, String entryHash) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.balanceAfter = balanceAfter;
        this.prevHash = prevHash;
        this.entryHash = entryHash;
    }

    // ── Champs (cahier §6.1) ─────────────────────────────────────────────

    /** BIGSERIAL — auto-incrémenté par PostgreSQL (identity strategy JPA). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    /** Compte concerné — FK vers {@code account} (index {@code idx_ledger_entry_account — (account_id, id)}). */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** {@link Direction} — DEBIT/CREDIT — {@code CHECK} de V5 — « CREDIT » fait 6 caractères. */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    private Direction direction;

    /** Montant — strictement > 0 — {@code CHECK} de V5 (§6.2). */
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    /** Devises — {@code currency} de la transaction — F-06 (§6.1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    /** Solde du compte juste après l'écriture — {@code RG-LED-04}. */
    @Column(name = "balance_after", precision = 19, scale = 4, nullable = false)
    private BigDecimal balanceAfter;

    /** Hash de l'écriture précédente du même compte — valeur initiale : 64 zéros (§6.1). */
    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    /** SHA-256(prev_hash ‖ transaction_id ‖ account_id ‖ direction ‖ amount ‖ currency ‖ created_at) (§6.1). */
    @Column(name = "entry_hash", nullable = false, length = 64)
    private String entryHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Accesseurs ────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Mutations — création — {@code LedgerService.apply} — F-04 ─────────

    /** Positionné par {@link LedgerService#apply} — {@code RG-LED-04}. */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
