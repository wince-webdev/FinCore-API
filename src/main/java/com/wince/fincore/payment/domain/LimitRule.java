package com.wince.fincore.payment.domain;

import com.wince.fincore.shared.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "limit_rule")
public class LimitRule {
    protected LimitRule() {}
    public LimitRule(UUID id, String kycLevel, BigDecimal perTransactionMax, BigDecimal dailyOutgoingMax, BigDecimal balanceMax, Currency currency) {
        this.id=id; this.kycLevel=kycLevel; this.perTransactionMax=perTransactionMax; this.dailyOutgoingMax=dailyOutgoingMax; this.balanceMax=balanceMax; this.currency=currency;
    }
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="kyc_level",nullable=false,length=20) private String kycLevel;
    @Column(name="per_transaction_max",precision=19,scale=4) private BigDecimal perTransactionMax;
    @Column(name="daily_outgoing_max",precision=19,scale=4) private BigDecimal dailyOutgoingMax;
    @Column(name="balance_max",precision=19,scale=4) private BigDecimal balanceMax;
    @Enumerated(EnumType.STRING) @Column(name="currency",nullable=false,length=3) private Currency currency;
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public String getKycLevel(){return kycLevel;}
    public BigDecimal getPerTransactionMax(){return perTransactionMax;}
    public BigDecimal getDailyOutgoingMax(){return dailyOutgoingMax;}
    public BigDecimal getBalanceMax(){return balanceMax;}
    public Currency getCurrency(){return currency;}
    public void setKycLevel(String v){kycLevel=v;}
    public void setPerTransactionMax(BigDecimal v){perTransactionMax=v;}
    public void setDailyOutgoingMax(BigDecimal v){dailyOutgoingMax=v;}
    public void setBalanceMax(BigDecimal v){balanceMax=v;}
    public void setCurrency(Currency v){currency=v;}
}
