package com.logplatform.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.logplatform.identity.domain.*;
import com.logplatform.identity.dto.Dtos.ApiKeyValidationResponse;
import com.logplatform.identity.dto.Dtos.CreateApiKeyRequest;
import com.logplatform.identity.dto.Dtos.CreatedApiKeyResponse;
import com.logplatform.identity.repository.ApiKeyRepository;
import com.logplatform.identity.repository.TenantRepository;
import com.logplatform.identity.security.CurrentUser;
import com.logplatform.identity.util.HashUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock ApiKeyRepository keys;
    @Mock TenantRepository tenants;
    @Mock AuditService audit;

    ApiKeyService service;
    final CurrentUser actor = new CurrentUser("user-1", "tenant-1", Set.of(Role.DEVELOPER));

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(keys, tenants, audit);
    }

    @Test
    void createStoresOnlyTheHashAndReturnsPlaintextOnce() {
        when(keys.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatedApiKeyResponse created = service.create(new CreateApiKeyRequest("billing", null), actor);

        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        verify(keys).save(saved.capture());
        assertThat(created.apiKey()).startsWith("lap_");
        assertThat(saved.getValue().getKeyHash()).isEqualTo(HashUtil.sha256Hex(created.apiKey()));
        assertThat(saved.getValue().getKeyHash()).doesNotContain(created.apiKey());
        assertThat(saved.getValue().getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    void revokedKeyIsRejected() {
        ApiKey k = new ApiKey();
        k.setStatus(ApiKeyStatus.REVOKED);
        when(keys.findByKeyHash(HashUtil.sha256Hex("lap_abc_secret"))).thenReturn(Optional.of(k));

        ApiKeyValidationResponse r = service.validate("lap_abc_secret");

        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("REVOKED");
    }

    @Test
    void activeKeyOfActiveTenantIsAccepted() {
        ApiKey k = new ApiKey();
        k.setTenantId("tenant-1");
        when(keys.findByKeyHash(HashUtil.sha256Hex("lap_abc_secret"))).thenReturn(Optional.of(k));
        Tenant t = new Tenant();
        when(tenants.findById("tenant-1")).thenReturn(Optional.of(t));

        ApiKeyValidationResponse r = service.validate("lap_abc_secret");

        assertThat(r.valid()).isTrue();
        assertThat(r.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void malformedKeyNeverHitsTheDatabase() {
        assertThat(service.validate("not-our-format").reason()).isEqualTo("MALFORMED");
        assertThat(service.validate(null).valid()).isFalse();
    }

    @Test
    void unknownKeyIsRejected() {
        when(keys.findByKeyHash(HashUtil.sha256Hex("lap_unknown_secret")))
                .thenReturn(Optional.empty());

        ApiKeyValidationResponse r = service.validate("lap_unknown_secret");

        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("UNKNOWN");
    }

    @Test
    void expiredKeyIsRejected() {
        ApiKey k = new ApiKey();
        k.setTenantId("tenant-1");
        k.setExpiresAt(Instant.now().minusSeconds(60));

        when(keys.findByKeyHash(HashUtil.sha256Hex("lap_expired_secret")))
                .thenReturn(Optional.of(k));

        ApiKeyValidationResponse r = service.validate("lap_expired_secret");

        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("EXPIRED");
        verifyNoInteractions(tenants);
    }

    @Test
    void missingTenantIsRejected() {
        ApiKey k = new ApiKey();
        k.setTenantId("missing-tenant");

        when(keys.findByKeyHash(HashUtil.sha256Hex("lap_missing_tenant")))
                .thenReturn(Optional.of(k));
        when(tenants.findById("missing-tenant")).thenReturn(Optional.empty());

        ApiKeyValidationResponse r = service.validate("lap_missing_tenant");

        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("TENANT_INACTIVE");
    }

    @Test
    void inactiveTenantIsRejected() {
        ApiKey k = new ApiKey();
        k.setTenantId("tenant-1");

        Tenant tenant = new Tenant();
        TenantStatus inactiveStatus = java.util.Arrays.stream(TenantStatus.values())
                .filter(status -> status != TenantStatus.ACTIVE)
                .findFirst()
                .orElseThrow();

        tenant.setStatus(inactiveStatus);

        when(keys.findByKeyHash(HashUtil.sha256Hex("lap_inactive_tenant")))
                .thenReturn(Optional.of(k));
        when(tenants.findById("tenant-1")).thenReturn(Optional.of(tenant));

        ApiKeyValidationResponse r = service.validate("lap_inactive_tenant");

        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("TENANT_INACTIVE");
    }
}
