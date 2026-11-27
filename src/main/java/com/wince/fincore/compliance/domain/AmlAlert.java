package com.wince.fincore.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aml_alert")
public class AmlAlert {
    protected AmlAlert() {}
    public AmlAlert(UUID id, String ruleCode, String severity, UUID customerId, UUID transactionId, String details) {
        this.id = id; this.ruleCode = ruleCode; this.severity = severity; this.customerId = customerId;
        this.transactionId = transactionId; this.details = details; this.status = "OPEN"; this.createdAt = Instant.now();
    }
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="rule_code",nullable=false,length=20) private String ruleCode;
    @Column(name="severity",nullable=false,length=10) private String severity;
    @Column(name="customer_id") private UUID customerId;
    @Column(name="transaction_id") private UUID transactionId;
    @Column(name="status",nullable=false,length=20) private String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="details",columnDefinition="jsonb") private String details;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="reviewed_at") private Instant reviewedAt;
    public UUID getId(){return id;}
    public String getRuleCode(){return ruleCode;}
    public String getSeverity(){return severity;}
    public UUID getCustomerId(){return customerId;}
    public UUID getTransactionId(){return transactionId;}
    public String getStatus(){return status;}
    public String getDetails(){return details;}
    public Instant getCreatedAt(){return createdAt;}
    public UUID getReviewedBy(){return reviewedBy;}
    public Instant getReviewedAt(){return reviewedAt;}
    public void setStatus(String v){status=v;}
    public void setReviewedBy(UUID v){reviewedBy=v;}
    public void setReviewedAt(Instant v){reviewedAt=v;}
}
