package com.wince.fincore.identity.repository;

import com.wince.fincore.identity.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux utilisateurs. La recherche par email est le chemin critique
 * du login : l'index UNIQUE sur {@code email} la rend en O(log n).
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Email normalisé en minuscules par le service avant l'appel. */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
