package com.wince.fincore.payment.repository;

import com.wince.fincore.payment.domain.FeeRule;
import com.wince.fincore.shared.domain.Currency;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeRuleRepository extends JpaRepository<FeeRule, UUID> {
    Optional<FeeRule> findTopByOperationTypeAndCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
            String operationType, Currency currency, Instant now);
    List<FeeRule> findByOperationTypeAndCurrency(String operationType, Currency currency);
}
