package com.wince.fincore.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    protected AuditLog() {}
    public AuditLog(UUID actorId, String actorRole, String action, String entityType, String entityId,
                    String ipAddress, String correlationId, String details) {
        this.actorId = actorId; this.actorRole = actorRole; this.action = action; this.entityType = entityType;
        this.entityId = entityId; this.ipAddress = ipAddress; this.correlationId = correlationId;
        this.details = details; this.occurredAt = Instant.now();
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name="id",nullable=false) private Long id;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    @Column(name="actor_id") private UUID actorId;
    @Column(name="actor_role",length=20) private String actorRole;
    @Column(name="action",nullable=false,length=100) private String action;
    @Column(name="entity_type",length=50) private String entityType;
    @Column(name="entity_id",length=100) private String entityId;
    @Column(name="ip_address",length=45) private String ipAddress;
    @Column(name="correlation_id",length=100) private String correlationId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="details",columnDefinition="jsonb") private String details;
    public Long getId(){return id;}
    public Instant getOccurredAt(){return occurredAt;}
    public UUID getActorId(){return actorId;}
    public String getActorRole(){return actorRole;}
    public String getAction(){return action;}
    public String getEntityType(){return entityType;}
    public String getEntityId(){return entityId;}
    public String getIpAddress(){return ipAddress;}
    public String getCorrelationId(){return correlationId;}
    public String getDetails(){return details;}
}
