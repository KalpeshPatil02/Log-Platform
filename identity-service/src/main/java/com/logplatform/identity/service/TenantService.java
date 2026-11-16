package com.logplatform.identity.service;

import com.logplatform.identity.domain.Role;
import com.logplatform.identity.domain.Tenant;
import com.logplatform.identity.domain.User;
import com.logplatform.identity.dto.Dtos.CreateTenantRequest;
import com.logplatform.identity.dto.Dtos.TenantResponse;
import com.logplatform.identity.exception.ApiException;
import com.logplatform.identity.repository.TenantRepository;
import com.logplatform.identity.repository.UserRepository;
import com.logplatform.identity.security.CurrentUser;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public TenantService(TenantRepository tenants, UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.tenants = tenants;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** Creates the tenant and its first TENANT_ADMIN atomically. */
    @Transactional
    public TenantResponse create(CreateTenantRequest req, CurrentUser actor) {
        String slug = req.slug().toLowerCase();
        if (tenants.existsBySlug(slug)) {
            throw ApiException.conflict("Tenant slug already in use: " + slug);
        }
        Tenant tenant = new Tenant();
        tenant.setName(req.name().trim());
        tenant.setSlug(slug);
        tenant = tenants.save(tenant);

        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setEmail(req.adminEmail().trim().toLowerCase());
        admin.setFullName(req.adminFullName().trim());
        admin.setPasswordHash(encoder.encode(req.adminPassword()));
        admin.setRoles(Set.of(Role.TENANT_ADMIN));
        users.save(admin);

        audit.record(tenant.getId(), actor.userId(), "TENANT_CREATED", "TENANT", tenant.getId(), "slug=" + slug);
        return TenantResponse.from(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listAll() {
        return tenants.findAll().stream().map(TenantResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse get(String tenantId) {
        return tenants.findById(tenantId).map(TenantResponse::from)
                .orElseThrow(() -> ApiException.notFound("Tenant not found"));
    }
}
