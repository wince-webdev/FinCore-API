package com.wince.fincore.ledger.repository;

import com.wince.fincore.ledger.domain.LedgerEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Dépôt JPA de {@link LedgerEntry} — Phase 5, F-04 — Graphe d'audit et vérifications.
 *
 * <p>{@link #findByAccountIdOrderById} — écritures d'un compte — {@code GET /admin/ledger/verify}
 * (§8, F-04) — ordre d'insertion = ordre chronologique — index {@code idx_ledger_entry_account (account_id, id)} ;
 * {@link #findTop1ByAccountIdOrderByIdDesc} — dernière écriture — dernier hachage du chaînage — {@code RG-LED-05}.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Écritures d'un compte — vérifications du chaînage et du solde — {@code GET /admin/ledger/verify}. */
    List<LedgerEntry> findByAccountIdOrderById(UUID accountId);

    /** Dernière écriture du compte — hachage de continuation du chaînage. */
    Optional<LedgerEntry> findTop1ByAccountIdOrderByIdDesc(UUID accountId);
}
