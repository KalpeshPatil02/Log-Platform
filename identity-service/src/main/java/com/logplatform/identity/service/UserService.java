package com.logplatform.identity.service;

import com.logplatform.identity.domain.Role;
import com.logplatform.identity.domain.User;
import com.logplatform.identity.dto.Dtos.CreateUserRequest;
import com.logplatform.identity.dto.Dtos.UserResponse;
import com.logplatform.identity.exception.ApiException;
import com.logplatform.identity.repository.UserRepository;
import com.logplatform.identity.security.CurrentUser;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional
    public UserResponse create(CreateUserRequest req, CurrentUser actor) {
        Set<Role> roles = (req.roles() == null || req.roles().isEmpty())
                ? Set.of(Role.VIEWER) : new HashSet<>(req.roles());
        if (roles.contains(Role.PLATFORM_ADMIN)) {
            throw ApiException.forbidden("PLATFORM_ADMIN cannot be granted through the API");
        }
        String email = req.email().trim().toLowerCase();
        if (users.existsByTenantIdAndEmail(actor.tenantId(), email)) {
            throw ApiException.conflict("A user with this email already exists in the tenant");
        }
        User u = new User();
        u.setTenantId(actor.tenantId());           // tenant comes from the JWT, never from the request body
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRoles(roles);
        u = users.save(u);

        audit.record(actor.tenantId(), actor.userId(), "USER_CREATED", "USER", u.getId(), "email=" + email + " roles=" + roles);
        return UserResponse.from(u);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(CurrentUser actor, Pageable pageable) {
        return users.findByTenantId(actor.tenantId(), pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser actor) {
        return users.findByIdAndTenantId(actor.userId(), actor.tenantId()).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
