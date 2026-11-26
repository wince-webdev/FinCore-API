package com.wince.fincore.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {
    protected IdempotencyRecord() {}
    public IdempotencyRecord(UUID id, String idempotencyKey, UUID userId, String endpoint, String requestHash,
                            String status, Instant expiresAt) {
        this.id = id; this.idempotencyKey = idempotencyKey; this.userId = userId; this.endpoint = endpoint;
        this.requestHash = requestHash; this.status = status; this.expiresAt = expiresAt;
    }
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="idempotency_key",nullable=false,length=64) private String idempotencyKey;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="endpoint",nullable=false,length=100) private String endpoint;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="status",nullable=false,length=20) private String status;
    @Column(name="response_status") private Integer responseStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_body",columnDefinition="jsonb") private String responseBody;
    @Column(name="transaction_id") private UUID transactionId;
    @Column(name="created_at",nullable=false) private Instant createdAt = Instant.now();
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    public UUID getId(){return id;}
    public String getIdempotencyKey(){return idempotencyKey;}
    public UUID getUserId(){return userId;}
    public String getEndpoint(){return endpoint;}
    public String getRequestHash(){return requestHash;}
    public String getStatus(){return status;}
    public Integer getResponseStatus(){return responseStatus;}
    public String getResponseBody(){return responseBody;}
    public UUID getTransactionId(){return transactionId;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;}
    public void setStatus(String v){status=v;}
    public void setResponseStatus(Integer v){responseStatus=v;}
    public void setResponseBody(String v){responseBody=v;}
    public void setTransactionId(UUID v){transactionId=v;}
}
