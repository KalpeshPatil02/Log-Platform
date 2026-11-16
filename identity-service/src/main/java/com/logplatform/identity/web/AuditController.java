package com.logplatform.identity.web;

import com.logplatform.identity.dto.Dtos.AuditLogResponse;
import com.logplatform.identity.security.CurrentUser;
import com.logplatform.identity.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Page<AuditLogResponse> list(@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                       @AuthenticationPrincipal Jwt jwt) {
        return audit.list(CurrentUser.from(jwt).tenantId(), pageable);
    }
}
