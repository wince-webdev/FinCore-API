package com.wince.fincore.payment.domain;

import com.wince.fincore.shared.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_rule")
public class FeeRule {
    protected FeeRule() {}
    public FeeRule(UUID id, String operationType, Currency currency, BigDecimal percentRate,
                   BigDecimal fixedAmount, BigDecimal minFee, BigDecimal maxFee, Instant validFrom, boolean active) {
        this.id = id; this.operationType = operationType; this.currency = currency;
        this.percentRate = percentRate; this.fixedAmount = fixedAmount; this.minFee = minFee;
        this.maxFee = maxFee; this.validFrom = validFrom; this.active = active;
    }
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="operation_type",nullable=false,length=30) private String operationType;
    @Enumerated(EnumType.STRING) @Column(name="currency",nullable=false,length=3) private Currency currency;
    @Column(name="percent_rate",precision=19,scale=8,nullable=false) private BigDecimal percentRate;
    @Column(name="fixed_amount",precision=19,scale=4,nullable=false) private BigDecimal fixedAmount;
    @Column(name="min_fee",precision=19,scale=4,nullable=false) private BigDecimal minFee;
    @Column(name="max_fee",precision=19,scale=4,nullable=false) private BigDecimal maxFee;
    @Column(name="valid_from",nullable=false) private Instant validFrom;
    @Column(name="active",nullable=false) private boolean active;
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public String getOperationType(){return operationType;}
    public Currency getCurrency(){return currency;}
    public BigDecimal getPercentRate(){return percentRate;}
    public BigDecimal getFixedAmount(){return fixedAmount;}
    public BigDecimal getMinFee(){return minFee;}
    public BigDecimal getMaxFee(){return maxFee;}
    public Instant getValidFrom(){return validFrom;}
    public boolean isActive(){return active;}
    public void setOperationType(String v){operationType=v;}
    public void setCurrency(Currency v){currency=v;}
    public void setPercentRate(BigDecimal v){percentRate=v;}
    public void setFixedAmount(BigDecimal v){fixedAmount=v;}
    public void setMinFee(BigDecimal v){minFee=v;}
    public void setMaxFee(BigDecimal v){maxFee=v;}
    public void setValidFrom(Instant v){validFrom=v;}
    public void setActive(boolean v){active=v;}
}
