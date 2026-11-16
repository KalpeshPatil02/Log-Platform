package com.logplatform.identity.service;

import com.logplatform.identity.config.IdentityProperties;
import com.logplatform.identity.domain.Role;
import com.logplatform.identity.domain.Tenant;
import com.logplatform.identity.domain.User;
import com.logplatform.identity.repository.TenantRepository;
import com.logplatform.identity.repository.UserRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Solves the chicken-and-egg problem: someone must be able to create the first tenant. */
@Component
public class PlatformBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);
    public static final String PLATFORM_SLUG = "platform";

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final IdentityProperties props;
    private final AuditService audit;

    public PlatformBootstrap(TenantRepository tenants, UserRepository users, PasswordEncoder encoder,
                             IdentityProperties props, AuditService audit) {
        this.tenants = tenants;
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var cfg = props.bootstrap();
        if (!StringUtils.hasText(cfg.adminEmail()) || !StringUtils.hasText(cfg.adminPassword())) {
            log.warn("Bootstrap admin not configured - skipping");
            return;
        }
        if (tenants.existsBySlug(PLATFORM_SLUG)) {
            return;
        }
        Tenant t = new Tenant();
        t.setName("Platform");
        t.setSlug(PLATFORM_SLUG);
        t = tenants.save(t);

        User admin = new User();
        admin.setTenantId(t.getId());
        admin.setEmail(cfg.adminEmail().toLowerCase());
        admin.setFullName("Platform Admin");
        admin.setPasswordHash(encoder.encode(cfg.adminPassword()));
        admin.setRoles(Set.of(Role.PLATFORM_ADMIN));
        admin = users.save(admin);

        audit.record(t.getId(), admin.getId(), "PLATFORM_BOOTSTRAPPED", "TENANT", t.getId(), null);
        log.info("Created platform tenant and bootstrap admin {} - CHANGE THE DEFAULT PASSWORD", admin.getEmail());
    }
}
