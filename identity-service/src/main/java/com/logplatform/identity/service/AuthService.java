package com.logplatform.identity.service;

import com.logplatform.identity.domain.Tenant;
import com.logplatform.identity.domain.TenantStatus;
import com.logplatform.identity.domain.User;
import com.logplatform.identity.domain.UserStatus;
import com.logplatform.identity.dto.Dtos.LoginRequest;
import com.logplatform.identity.dto.Dtos.TokenResponse;
import com.logplatform.identity.exception.ApiException;
import com.logplatform.identity.repository.TenantRepository;
import com.logplatform.identity.repository.UserRepository;
import com.logplatform.identity.security.IssuedToken;
import com.logplatform.identity.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    /** Compared against when the user does not exist, so response time doesn't reveal valid emails. */
    private final String dummyHash;

    public AuthService(TenantRepository tenants, UserRepository users, PasswordEncoder encoder,
                       JwtService jwt, AuditService audit) {
        this.tenants = tenants;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public TokenResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        Tenant tenant = tenants.findBySlug(req.tenantSlug().trim().toLowerCase())
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE).orElse(null);
        User user = tenant == null ? null
                : users.findByTenantIdAndEmail(tenant.getId(), email).orElse(null);

        boolean passwordOk = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        boolean ok = user != null && passwordOk && user.getStatus() == UserStatus.ACTIVE;

        if (!ok) {
            audit.record(tenant == null ? null : tenant.getId(), user == null ? null : user.getId(),
                    "LOGIN_FAILED", "USER", null, "email=" + email);
            throw ApiException.unauthorized("Invalid credentials");   // same message for every failure reason
        }
        audit.record(tenant.getId(), user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), null);
        IssuedToken token = jwt.issue(user);
        return new TokenResponse(token.accessToken(), "Bearer", token.expiresInSeconds());
    }
}
