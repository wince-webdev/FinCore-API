package com.wince.fincore.account.repository;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.shared.domain.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Dépôt JPA de {@link Account} — Phase 4, F-03.
 *
 * <p>Au-delà du {@code CrudRepository}, les requêtes spécifiques :
 * <ul>
 *   <li>{@link #findByCustomerIdAndKindAndCurrency} — identifiant de
 *       « couple (type, devise) » (RG-ACC-02) — backstop Java du
 *       {@code UNIQUE} partiel en base (selon le chemin gagnant) ;</li>
 *   <li>{@link #existsByAccountNumber} — arbitre de la unicité des
 *       numéros avant l'ouverture (le {@code UNIQUE} en base reste l'arbitre
 *       final — RG-LED/§6.2 « Clés étrangères systématiques, index…») ;</li>
 *   <li>{@link #findByCustomerId} — {@code GET /accounts} (CUSTOMER, §8) ;</li>
 *   <li>{@link #findAllBySystemCode} — lecture des comptes systèmes (seed,
 *       RG-ACC-06, F-13).</li>
 * </ul>
 *
 * <p>Équivalent Symfony : Doctrine ORM + {@code EntityRepository}.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Client — un seul compte par couple (type, devise) (RG-ACC-02). */
    Optional<Account> findByCustomerIdAndKindAndCurrency(
            UUID customerId, AccountKind kind, Currency currency);

    /** Compte par numéro — unique (§6.1) — consommé par le pré-contrôle (§8). */
    Optional<Account> findByAccountNumber(String accountNumber);

    /** Existence de numéro — pre-check de la unicité (§6.2 arbitre final). */
    boolean existsByAccountNumber(String accountNumber);

    /** « Mes comptes » — tous les comptes du client (§8, GET /accounts). */
    List<Account> findByCustomerId(UUID customerId);

    /** Comptes systèmes — une par (code, devise) à la fin du démarrage (RG-ACC-06). */
    List<Account> findAllBySystemCode(SystemCode systemCode);
}
