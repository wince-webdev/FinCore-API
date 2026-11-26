package com.wince.fincore.payment.application;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.customer.domain.Customer;
import com.wince.fincore.customer.repository.CustomerRepository;
import com.wince.fincore.ledger.repository.FinancialTransactionRepository;
import com.wince.fincore.payment.domain.LimitRule;
import com.wince.fincore.payment.repository.LimitRuleRepository;
import com.wince.fincore.shared.domain.Currency;
import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class LimitService {
    private final LimitRuleRepository limitRuleRepository;
    private final CustomerRepository customerRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final Clock clock;
    public LimitService(LimitRuleRepository limitRuleRepository, CustomerRepository customerRepository,
                       FinancialTransactionRepository transactionRepository, Clock clock) {
        this.limitRuleRepository = limitRuleRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }
    private LimitRule rule(String kycLevel, Currency currency) {
        return limitRuleRepository.findByKycLevelAndCurrency(kycLevel, currency)
                .orElseThrow(() -> new FinCoreException(ErrorCode.VALIDATION_FAILED, "Plafond introuvable."));
    }
    public Customer customerOf(UUID customerId) {
        return customerRepository.findById(customerId).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Client introuvable."));
    }
    public void checkTransfer(Account source, Account destination, BigDecimal amount) {
        Customer customer = customerRepository.findById(source.getCustomerId()).orElseThrow(
                () -> new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Client introuvable."));
        LimitRule r = rule(customer.getKycLevel().name(), source.getCurrency());
        if (r.getPerTransactionMax() != null && amount.compareTo(r.getPerTransactionMax()) > 0) {
            throw new FinCoreException(ErrorCode.LIMIT_EXCEEDED, "Plafond par transaction depasse : " + r.getPerTransactionMax());
        }
        if (r.getDailyOutgoingMax() != null) {
            Instant from = LocalDate.now(ClockConfig.BANKING_ZONE).atStartOfDay(ClockConfig.BANKING_ZONE).toInstant();
            BigDecimal cumulative = transactionRepository.sumOutgoingSince(source.getId(), from);
            if (cumulative.add(amount).compareTo(r.getDailyOutgoingMax()) > 0) {
                throw new FinCoreException(ErrorCode.LIMIT_EXCEEDED, "Plafond quotidien depasse : " + r.getDailyOutgoingMax());
            }
        }
        if (r.getBalanceMax() != null) {
            BigDecimal after = destination.getBalance().add(amount);
            if (after.compareTo(r.getBalanceMax()) > 0) {
                throw new FinCoreException(ErrorCode.LIMIT_EXCEEDED, "Plafond de solde depasse : " + r.getBalanceMax());
            }
        }
    }
    public void checkWithdrawal(Account account, BigDecimal amount) {
        if (account.getCustomerId() == null) return;
        Customer customer = customerRepository.findById(account.getCustomerId()).orElse(null);
        if (customer == null) return;
        LimitRule r = limitRuleRepository.findByKycLevelAndCurrency(customer.getKycLevel().name(), account.getCurrency()).orElse(null);
        if (r == null) return;
        if (r.getPerTransactionMax() != null && amount.compareTo(r.getPerTransactionMax()) > 0) {
            throw new FinCoreException(ErrorCode.LIMIT_EXCEEDED, "Plafond par transaction depasse : " + r.getPerTransactionMax());
        }
        if (r.getDailyOutgoingMax() != null) {
            Instant from = LocalDate.now(ClockConfig.BANKING_ZONE).atStartOfDay(ClockConfig.BANKING_ZONE).toInstant();
            BigDecimal cumulative = transactionRepository.sumOutgoingSince(account.getId(), from);
            if (cumulative.add(amount).compareTo(r.getDailyOutgoingMax()) > 0) {
                throw new FinCoreException(ErrorCode.LIMIT_EXCEEDED, "Plafond quotidien depasse : " + r.getDailyOutgoingMax());
            }
        }
    }

    public void checkDeposit(Account account, BigDecimal amount) {
        if (account.getCustomerId() == null) return;
        Customer customer = customerRepository.findById(account.getCustomerId()).orElse(null);
        if (customer == null) return;
        LimitRule r = limitRuleRepository.findByKycLevelAndCurrency(customer.getKycLevel().name(), account.getCurrency()).orElse(null);
        if (r == null || r.getBalanceMax() == null) return;
        BigDecimal after = account.getBalance().add(amount);
        if (after.compareTo(r.getBalanceMax()) > 0) {
            throw new FinCoreException(ErrorCode.LIMIT_EXCEEDED, "Plafond de solde depasse : " + r.getBalanceMax());
        }
    }
    public BigDecimal remainingDailyLimit(Account source) {
        if (source.getCustomerId() == null) return null;
        Customer customer = customerRepository.findById(source.getCustomerId()).orElse(null);
        if (customer == null) return null;
        LimitRule r = limitRuleRepository.findByKycLevelAndCurrency(customer.getKycLevel().name(), source.getCurrency()).orElse(null);
        if (r == null || r.getDailyOutgoingMax() == null) return null;
        Instant from = LocalDate.now(ClockConfig.BANKING_ZONE).atStartOfDay(ClockConfig.BANKING_ZONE).toInstant();
        BigDecimal cumulative = transactionRepository.sumOutgoingSince(source.getId(), from);
        return r.getDailyOutgoingMax().subtract(cumulative);
    }
}
