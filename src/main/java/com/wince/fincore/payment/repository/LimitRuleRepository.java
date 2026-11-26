package com.wince.fincore.payment.repository;

import com.wince.fincore.payment.domain.LimitRule;
import com.wince.fincore.shared.domain.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitRuleRepository extends JpaRepository<LimitRule, UUID> {
    Optional<LimitRule> findByKycLevelAndCurrency(String kycLevel, Currency currency);
    List<LimitRule> findByKycLevel(String kycLevel);
}
