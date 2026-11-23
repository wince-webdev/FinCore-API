package com.wince.fincore.ledger.repository;

import com.wince.fincore.ledger.domain.FinancialTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Dépôt JPA de {@link FinancialTransaction} — Phase 5, F-04.
 *
 * <p>{@link #findByReference} — l'identifiant externe (UNIQUE — §6.1) — rétro-compatibilité
 * à la Phase 6 (idempotence — F-06 — l'unicité à la base reste l'arbitre) ;
 * {@code status} — requête d'audit de F-13 (Phase 9).
 */
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    /** Transaction par numéro externe — UNIQUE (§6.1). */
    Optional<FinancialTransaction> findByReference(String reference);
}
