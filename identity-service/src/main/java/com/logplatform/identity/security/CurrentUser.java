package com.logplatform.identity.security;

import com.logplatform.identity.domain.Role;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, as described by the verified JWT. Tenant id ALWAYS comes from here, never from request params. */
public record CurrentUser(String userId, String tenantId, Set<Role> roles) {

    public static CurrentUser from(Jwt jwt) {
        List<String> raw = jwt.getClaimAsStringList("roles");
        Set<Role> roles = raw == null ? Set.of() : raw.stream().map(Role::valueOf).collect(Collectors.toSet());
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsString("tenant_id"), roles);
    }

    public boolean has(Role role) { return roles.contains(role); }
}
