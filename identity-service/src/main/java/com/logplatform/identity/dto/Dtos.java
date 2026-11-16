package com.logplatform.identity.dto;

import com.logplatform.identity.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;

public final class Dtos {
    private Dtos() {}

    // ---- auth ----
    public record LoginRequest(@NotBlank String tenantSlug, @NotBlank @Email String email, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    // ---- tenants ----
    public record CreateTenantRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$", message = "3-63 chars: lowercase letters, digits, hyphens") String slug,
            @NotBlank @Email String adminEmail,
            @NotBlank @Size(min = 10, max = 72) String adminPassword,
            @NotBlank @Size(max = 150) String adminFullName) {}

    public record TenantResponse(String id, String name, String slug, TenantStatus status, Instant createdAt) {
        public static TenantResponse from(Tenant t) {
            return new TenantResponse(t.getId(), t.getName(), t.getSlug(), t.getStatus(), t.getCreatedAt());
        }
    }

    // ---- users ----
    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            Set<Role> roles) {}

    public record UserResponse(String id, String tenantId, String email, String fullName,
                               UserStatus status, Set<Role> roles, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getTenantId(), u.getEmail(), u.getFullName(),
                    u.getStatus(), Set.copyOf(u.getRoles()), u.getCreatedAt());
        }
    }

    // ---- api keys ----
    public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name, @Min(1) @Max(365) Integer expiresInDays) {}

    /** The only response that ever contains the plaintext key. */
    public record CreatedApiKeyResponse(String id, String name, String keyPrefix, String apiKey, Instant expiresAt) {}

    public record ApiKeyResponse(String id, String name, String keyPrefix, ApiKeyStatus status,
                                 Instant createdAt, Instant expiresAt, Instant lastUsedAt, Instant revokedAt) {
        public static ApiKeyResponse from(ApiKey k) {
            return new ApiKeyResponse(k.getId(), k.getName(), k.getKeyPrefix(), k.getStatus(),
                    k.getCreatedAt(), k.getExpiresAt(), k.getLastUsedAt(), k.getRevokedAt());
        }
    }

    public record ApiKeyValidationRequest(@NotBlank String apiKey) {}

    public record ApiKeyValidationResponse(boolean valid, String reason, String tenantId, String apiKeyId) {
        public static ApiKeyValidationResponse ok(String tenantId, String apiKeyId) {
            return new ApiKeyValidationResponse(true, null, tenantId, apiKeyId);
        }
        public static ApiKeyValidationResponse invalid(String reason) {
            return new ApiKeyValidationResponse(false, reason, null, null);
        }
    }

    // ---- audit ----
    public record AuditLogResponse(Long id, String actorId, String action, String resourceType,
                                   String resourceId, String details, String ipAddress, Instant createdAt) {
        public static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getActorId(), a.getAction(), a.getResourceType(),
                    a.getResourceId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
        }
    }
}
