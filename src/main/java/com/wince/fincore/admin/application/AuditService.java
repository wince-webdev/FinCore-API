package com.wince.fincore.admin.application;

import com.wince.fincore.admin.domain.AuditLog;
import com.wince.fincore.admin.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    public AuditService(AuditLogRepository repository) { this.repository = repository; }
    public void log(UUID actorId, String actorRole, String action, String entityType, String entityId,
                    HttpServletRequest request, String details) {
        String ip = request == null ? null : request.getRemoteAddr();
        String corr = org.slf4j.MDC.get(com.wince.fincore.shared.infrastructure.CorrelationIdFilter.MDC_KEY);
        AuditLog entry = new AuditLog(actorId, actorRole, action, entityType, entityId, ip, corr, details);
        repository.save(entry);
    }
}
