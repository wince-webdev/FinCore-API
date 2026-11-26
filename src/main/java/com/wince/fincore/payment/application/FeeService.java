package com.wince.fincore.payment.application;

import com.wince.fincore.payment.domain.FeeRule;
import com.wince.fincore.payment.repository.FeeRuleRepository;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FeeService {
    private final FeeRuleRepository feeRuleRepository;
    private final Clock clock;
    public FeeService(FeeRuleRepository feeRuleRepository, Clock clock) {
        this.feeRuleRepository = feeRuleRepository;
        this.clock = clock;
    }
    public BigDecimal calculate(String operationType, Currency currency, BigDecimal amount) {
        FeeRule rule = feeRuleRepository.findTopByOperationTypeAndCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
                operationType, currency, clock.instant()).orElse(null);
        if (rule == null) {
            return BigDecimal.ZERO.setScale(currency.scale(), RoundingMode.HALF_EVEN);
        }
        BigDecimal raw = amount.multiply(rule.getPercentRate()).add(rule.getFixedAmount());
        BigDecimal clamped = raw;
        if (raw.compareTo(rule.getMinFee()) < 0) clamped = rule.getMinFee();
        if (clamped.compareTo(rule.getMaxFee()) > 0 && rule.getMaxFee().compareTo(BigDecimal.ZERO) >= 0) clamped = rule.getMaxFee();
        // if max_fee is 0 (deposit) force 0
        if (rule.getMaxFee().compareTo(BigDecimal.ZERO) == 0) clamped = BigDecimal.ZERO;
        return clamped.setScale(currency.scale(), RoundingMode.HALF_UP);
    }
}
