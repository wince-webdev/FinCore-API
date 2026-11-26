package com.wince.fincore.admin.repository;

import com.wince.fincore.admin.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:actorId IS NULL OR a.actorId = :actorId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:from IS NULL OR a.occurredAt >= :from) AND " +
           "(:to IS NULL OR a.occurredAt <= :to)")
    Page<AuditLog> search(@Param("actorId") UUID actorId,
                          @Param("action") String action,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
