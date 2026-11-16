package com.logplatform.identity.web;

import com.logplatform.identity.dto.Dtos.CreateTenantRequest;
import com.logplatform.identity.dto.Dtos.TenantResponse;
import com.logplatform.identity.security.CurrentUser;
import com.logplatform.identity.service.TenantService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {
    private final TenantService tenants;

    public TenantController(TenantService tenants) { this.tenants = tenants; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest req, @AuthenticationPrincipal Jwt jwt) {
        return tenants.create(req, CurrentUser.from(jwt));
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<TenantResponse> list() {
        return tenants.listAll();
    }

    @GetMapping("/current")
    public TenantResponse current(@AuthenticationPrincipal Jwt jwt) {
        return tenants.get(CurrentUser.from(jwt).tenantId());
    }
}
