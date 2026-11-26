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
@Table(name = "fx_rate")
public class FxRate {
    protected FxRate() {}
    public FxRate(UUID id, Currency baseCurrency, Currency quoteCurrency, BigDecimal rate, BigDecimal spreadPercent, Instant validFrom, String source) {
        this.id = id; this.baseCurrency = baseCurrency; this.quoteCurrency = quoteCurrency;
        this.rate = rate; this.spreadPercent = spreadPercent; this.validFrom = validFrom; this.source = source;
    }
    @Id @Column(name="id",nullable=false) private UUID id;
    @Enumerated(EnumType.STRING) @Column(name="base_currency",nullable=false,length=3) private Currency baseCurrency;
    @Enumerated(EnumType.STRING) @Column(name="quote_currency",nullable=false,length=3) private Currency quoteCurrency;
    @Column(name="rate",precision=19,scale=8,nullable=false) private BigDecimal rate;
    @Column(name="spread_percent",precision=19,scale=4,nullable=false) private BigDecimal spreadPercent;
    @Column(name="valid_from",nullable=false) private Instant validFrom;
    @Column(name="source",length=50) private String source;
    public UUID getId(){return id;}
    public Currency getBaseCurrency(){return baseCurrency;}
    public Currency getQuoteCurrency(){return quoteCurrency;}
    public BigDecimal getRate(){return rate;}
    public BigDecimal getSpreadPercent(){return spreadPercent;}
    public Instant getValidFrom(){return validFrom;}
    public String getSource(){return source;}
    public void setId(UUID v){id=v;}
    public void setBaseCurrency(Currency v){baseCurrency=v;}
    public void setQuoteCurrency(Currency v){quoteCurrency=v;}
    public void setRate(BigDecimal v){rate=v;}
    public void setSpreadPercent(BigDecimal v){spreadPercent=v;}
    public void setValidFrom(Instant v){validFrom=v;}
    public void setSource(String v){source=v;}
}
