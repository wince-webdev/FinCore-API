package com.wince.fincore.account.repository;

import com.wince.fincore.account.domain.InterestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface InterestRunRepository extends JpaRepository<InterestRun, UUID> {
    Optional<InterestRun> findByRunYearAndRunMonth(int year, int month);
}
