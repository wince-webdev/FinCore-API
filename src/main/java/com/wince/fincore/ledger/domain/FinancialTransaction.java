package com.wince.fincore.ledger.domain;

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
 * Entité JPA {@code financial_transaction} — table créée par
 * {@code V5__create_financial_transaction.sql} (cahier §6.1, Phase 5, F-04).
 *
 * <p><strong>Responsabilités (§7 F-04/F-05) :</strong>
 * <ul>
 *   <li>{@link TransactionType} — dépôt (F-05 — RG-DEP-01), retrait
 *       (F-05 — RG-DEP-02), virements (F-06+ — Phase 6), contre-passation
 *       (F-09 — Phase 6) — enum fermé stocké en {@code VARCHAR} — ajouter un
 *       type = nouveau CHECK + nouveau déploiement — voir {@link #type()};</li>
 *   <li>{@link TransactionStatus} — {@code PENDING} à l'arrivée (virements
 *       externes — F-08, Phase 8), {@code COMPLETED} pour les dépôts/retraits —
 *       {@link LedgerService#apply} — le statut passé en paramètre (Phase 6) ;</li>
 *   <li>écriture dans la <em>même transaction de base de données</em> que les
 *       écritures — {@code RG-LED-01} — le service en est l'orchestrateur ;</li>
 *   <li>{@code reference UNIQUE} — identifiant externe — nommé
 *       {@code TransactionReferenceGenerator} — ex. {@code TXN-20261130-000042} (§6.1).</li>
 * </ul>
 *
 * <p>{@code initiated_by} = l'id de l'{@code AppUser} qui a initié
 * l'opération — pour déposer : le TELLER (§8) — pour virements : le client
 * (Phase 6).
 *
 * <p>{@link #getCreatedAt()} — avec {@code Instant.now()} au constructeur (équivalent
 * de la Phase 3 {@code Customer}/{@code AppUser} — la même idée : horodatage de
 * création dans le service — une horloge peut être injectée à la Phase 5 —
 * {@code LedgerService} — pas encore — Phase 0 : {@code Instant.now()}).
 */
@Entity
@Table(name = "financial_transaction")
public class FinancialTransaction {

    /** Nécessaire pour JPA — jamais utilisé directement (§2.2). */
    protected FinancialTransaction() {
    }

    /**
     * Création — F-04/F-05 — {@code status COMPLETED} par défaut (Phase 5 —
     * dépôts/retraits — Phase 6 passe en PENDING pour les rejets).
     *
     * @param sourceAccountId compte source — NULL pour les Dépôts (CASH_VAULT) — Phase 6 : virements (§6.1 — null ok)
     * @param destinationAccountId compte destination — NULL pour les Retraits — Phase 6 : virements
     */
    public FinancialTransaction(UUID id, String reference, TransactionType type,
                               UUID sourceAccountId, UUID destinationAccountId,
                               UUID initiatedBy, BigDecimal amount, Currency currency,
                               BigDecimal feeAmount, String description) {
        this.id = id;
        this.reference = reference;
        this.type = type;
        this.status = TransactionStatus.COMPLETED;
        this.amount = amount;
        this.feeAmount = feeAmount == null ? BigDecimal.ZERO : feeAmount;
        this.currency = currency;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.reversalOf = null;
        this.initiatedBy = initiatedBy;
        this.description = description;
        this.failureReason = null;
        this.createdAt = Instant.now();
        this.completedAt = null;
    }

    // ── Champs (cahier §6.1) ─────────────────────────────────────────────

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Identifiant externe — {@code TXN-YYYYMMDD-NNNNNN} — Phase 6/9 — {@code TransactionReferenceGenerator}. */
    @Column(name = "reference", nullable = false, length = 20)
    private String reference;

    /** Type de transaction — {@code TransactionType} — fermé — {@code CHECK} de V5. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    /** Statut — {@code TransactionStatus} — {@code COMPLETED} à la Phase 5. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /** Montant — §4.3 : débit = solde − montant — classe {@link com.wince.fincore.shared.domain.Money} (étanchéité à la devise — Phase 6/9 — F-04). */
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    /** Frais — 0 à la Phase 5 — F-06 — Phase 6. */
    @Column(name = "fee_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal feeAmount;

    /** Devises — {@code XOF/EUR/USD} — F-06 (§6.1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    /** FK vers {@code account} — NULL pour les Dépôts — F-06 (virements — §6.1). */
    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    /** FK vers {@code account} — NULL pour les Retraits — F-06 (virements — §6.1). */
    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    /** FK vers {@code financial_transaction} — F-09 (Phase 6) — NULL jusqu'à la contre-passation. */
    @Column(name = "reversal_of")
    private UUID reversalOf;

    /** FK vers {@code app_user} — initiateur — F-06 (§6.1). */
    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    /** Libellé — Phase 6/7 — extension non ouverte à la Phase 5. */
    @Column(name = "description", length = 500)
    private String description;

    /** Raison du rejet — F-06 — Phase 6. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Fond — F-06 — Phase 6 — {@code COMPLETED} à la Phase 5 (les écritures passent en base). */
    @Column(name = "completed_at")
    private Instant completedAt;

    // ── Accesseurs ────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public UUID getReversalOf() {
        return reversalOf;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public String getDescription() {
        return description;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    // ── Mutations — Flow — {@code LedgerService.apply} (F-04 — RG-LED-01) ──

    /** Positionné après exécution — {@code RG-LED-01/02} — voir {@code LedgerService.apply}. */
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    /** Positionné après exécution — {@code completed_at} — F-04. */
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
