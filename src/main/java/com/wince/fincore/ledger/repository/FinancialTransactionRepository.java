package com.wince.fincore.ledger.repository;

import com.wince.fincore.ledger.domain.FinancialTransaction;
import com.wince.fincore.ledger.domain.TransactionStatus;
import com.wince.fincore.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt JPA de {@link FinancialTransaction} — Phase 5, F-04.
 *
 * <p>{@link #findByReference} — l'identifiant externe (UNIQUE — §6.1) — rétro-compatibilité
 * à la Phase 6 (idempotence — F-06 — l'unicité à la base reste l'arbitre) ;
 * {@code status} — requête d'audit de F-13 (Phase 9).
 */
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    /** Transaction par numǸro externe �?" UNIQUE (��6.1). */
    Optional<FinancialTransaction> findByReference(String reference);

    /** Transactions dont l'origine est la transaction donnǸe (contre-passation). */
    List<FinancialTransaction> findByReversalOf(UUID reversalOf);

    /** Transactions par type et statut. */
    List<FinancialTransaction> findByTypeAndStatus(TransactionType type, TransactionStatus status);

    /** Somme des montants sortants d'un compte depuis une date (plafond journalier). */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM FinancialTransaction t " +
           "WHERE t.sourceAccountId = :accountId AND t.status IN ('COMPLETED','PENDING') " +
           "AND t.createdAt >= :from")
    BigDecimal sumOutgoingSince(@Param("accountId") UUID accountId, @Param("from") Instant from);
}
