package com.logplatform.identity.service;

import com.logplatform.identity.domain.AuditLog;
import com.logplatform.identity.dto.Dtos.AuditLogResponse;
import com.logplatform.identity.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    /** REQUIRES_NEW so a failed login audit survives the rollback of the surrounding operation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String tenantId, String actorId, String action,
                       String resourceType, String resourceId, String details) {
        AuditLog a = new AuditLog();
        a.setTenantId(tenantId);
        a.setActorId(actorId);
        a.setAction(action);
        a.setResourceType(resourceType);
        a.setResourceId(resourceId);
        a.setDetails(details);
        a.setIpAddress(clientIp());
        repo.save(a);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(String tenantId, Pageable pageable) {
        return repo.findByTenantId(tenantId, pageable).map(AuditLogResponse::from);
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getRemoteAddr();   // behind the gateway, switch to X-Forwarded-For (Phase 2)
        }
        return null;
    }
}
