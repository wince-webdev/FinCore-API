package com.wince.fincore.payment.repository;

import com.wince.fincore.payment.domain.FxRate;
import com.wince.fincore.shared.domain.Currency;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, UUID> {
    Optional<FxRate> findTopByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(
            Currency base, Currency quote, Instant now);
}
