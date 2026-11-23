package com.wince.fincore.customer.repository;

import com.wince.fincore.customer.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Dépôt JPA du client — Phase 3, F-02.
 *
 * <p>{@link #findByUserId(UUID)} est le lien deux en un : une inscription =
 * un {@code AppUser} + un {@code Customer} liés par {@code user_id} unique
 * (§6.1) — utilisé par le contrôleur {@code GET /customers/me} pour résoudre
 * « qui suis-je ? » à partir du JWT ({@code sub} = id de l'utilisateur).</p>
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /** Client par id de l'utilisateur authentifié — le flux {@code /me}. */
    Optional<Customer> findByUserId(UUID userId);
}
