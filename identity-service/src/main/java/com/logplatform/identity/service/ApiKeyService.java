package com.logplatform.identity.service;

import com.logplatform.identity.domain.ApiKey;
import com.logplatform.identity.domain.ApiKeyStatus;
import com.logplatform.identity.domain.Tenant;
import com.logplatform.identity.domain.TenantStatus;
import com.logplatform.identity.dto.Dtos.ApiKeyResponse;
import com.logplatform.identity.dto.Dtos.ApiKeyValidationResponse;
import com.logplatform.identity.dto.Dtos.CreateApiKeyRequest;
import com.logplatform.identity.dto.Dtos.CreatedApiKeyResponse;
import com.logplatform.identity.exception.ApiException;
import com.logplatform.identity.repository.ApiKeyRepository;
import com.logplatform.identity.repository.TenantRepository;
import com.logplatform.identity.security.CurrentUser;
import com.logplatform.identity.util.HashUtil;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {
    public static final String KEY_PREFIX = "lap_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository keys;
    private final TenantRepository tenants;
    private final AuditService audit;

    public ApiKeyService(ApiKeyRepository keys, TenantRepository tenants, AuditService audit) {
        this.keys = keys;
        this.tenants = tenants;
        this.audit = audit;
    }

    /** Plaintext key is returned exactly once; only its SHA-256 is stored. */
    @Transactional
    public CreatedApiKeyResponse create(CreateApiKeyRequest req, CurrentUser actor) {
        String shortId = randomToken(6);
        String plain = KEY_PREFIX + shortId + "_" + randomToken(24);

        ApiKey k = new ApiKey();
        k.setTenantId(actor.tenantId());
        k.setName(req.name().trim());
        k.setKeyPrefix(KEY_PREFIX + shortId);
        k.setKeyHash(HashUtil.sha256Hex(plain));
        k.setCreatedBy(actor.userId());
        if (req.expiresInDays() != null) {
            k.setExpiresAt(Instant.now().plus(Duration.ofDays(req.expiresInDays())));
        }
        k = keys.save(k);

        audit.record(actor.tenantId(), actor.userId(), "API_KEY_CREATED", "API_KEY", k.getId(), "name=" + k.getName());
        return new CreatedApiKeyResponse(k.getId(), k.getName(), k.getKeyPrefix(), plain, k.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(CurrentUser actor) {
        return keys.findByTenantIdOrderByCreatedAtDesc(actor.tenantId()).stream().map(ApiKeyResponse::from).toList();
    }

    @Transactional
    public void revoke(String id, CurrentUser actor) {
        ApiKey k = keys.findByIdAndTenantId(id, actor.tenantId())
                .orElseThrow(() -> ApiException.notFound("API key not found"));
        if (k.getStatus() == ApiKeyStatus.REVOKED) {
            return;   // idempotent
        }
        k.setStatus(ApiKeyStatus.REVOKED);
        k.setRevokedAt(Instant.now());
        audit.record(actor.tenantId(), actor.userId(), "API_KEY_REVOKED", "API_KEY", k.getId(), "name=" + k.getName());
        // Phase 3: also publish a revocation event so ingestion-service evicts its Redis cache entry.
    }

    /** Called by ingestion-service (service-to-service). */
    @Transactional
    public ApiKeyValidationResponse validate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX) || rawKey.length() > 128) {
            return ApiKeyValidationResponse.invalid("MALFORMED");
        }
        Optional<ApiKey> found = keys.findByKeyHash(HashUtil.sha256Hex(rawKey));
        if (found.isEmpty()) {
            return ApiKeyValidationResponse.invalid("UNKNOWN");
        }
        ApiKey k = found.get();
        Instant now = Instant.now();
        if (k.getStatus() == ApiKeyStatus.REVOKED) {
            return ApiKeyValidationResponse.invalid("REVOKED");
        }
        if (k.getExpiresAt() != null && k.getExpiresAt().isBefore(now)) {
            return ApiKeyValidationResponse.invalid("EXPIRED");
        }
        Tenant tenant = tenants.findById(k.getTenantId()).orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) {
            return ApiKeyValidationResponse.invalid("TENANT_INACTIVE");
        }
        // Throttled write: managed entity, so the flush at commit persists it.
        if (k.getLastUsedAt() == null || k.getLastUsedAt().isBefore(now.minusSeconds(60))) {
            k.setLastUsedAt(now);
        }
        return ApiKeyValidationResponse.ok(k.getTenantId(), k.getId());
    }

    private static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
