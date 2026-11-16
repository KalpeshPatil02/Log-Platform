package com.logplatform.identity.web;

import com.logplatform.identity.dto.Dtos.ApiKeyResponse;
import com.logplatform.identity.dto.Dtos.CreateApiKeyRequest;
import com.logplatform.identity.dto.Dtos.CreatedApiKeyResponse;
import com.logplatform.identity.security.CurrentUser;
import com.logplatform.identity.service.ApiKeyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {
    private final ApiKeyService apiKeys;

    public ApiKeyController(ApiKeyService apiKeys) { this.apiKeys = apiKeys; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','DEVELOPER')")
    public CreatedApiKeyResponse create(@Valid @RequestBody CreateApiKeyRequest req, @AuthenticationPrincipal Jwt jwt) {
        return apiKeys.create(req, CurrentUser.from(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','DEVELOPER','VIEWER')")
    public List<ApiKeyResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return apiKeys.list(CurrentUser.from(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public void revoke(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        apiKeys.revoke(id, CurrentUser.from(jwt));
    }
}
