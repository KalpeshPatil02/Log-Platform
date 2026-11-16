package com.logplatform.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.logplatform.ingestion.support.CacheKeys;
import com.logplatform.common.Hashing;
import com.logplatform.ingestion.config.IngestionProperties;
import com.logplatform.ingestion.exception.IdentityUnavailableException;
import com.logplatform.ingestion.security.ApiKeyPrincipal;
import com.logplatform.ingestion.security.ApiKeyResolver;
import com.logplatform.ingestion.security.IdentityClient;
import com.logplatform.ingestion.security.IdentityClient.ValidationResult;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ApiKeyResolverTest {
    static final String KEY = "lap_abc123_secretsecret";
    static final String CACHE_KEY = CacheKeys.apiKey(Hashing.sha256Hex(KEY));

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock IdentityClient identity;

    ApiKeyResolver resolver;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(ops);
        IngestionProperties props = new IngestionProperties("http://identity", "t", 100, Duration.ofHours(24),
                new IngestionProperties.Cache(Duration.ofMinutes(5), Duration.ofSeconds(30)));
        resolver = new ApiKeyResolver(redis, identity, props);
    }

    @Test
    void cacheHitSkipsIdentity() {
        when(ops.get(CACHE_KEY)).thenReturn("tenant-1|key-1");

        Optional<ApiKeyPrincipal> p = resolver.resolve(KEY);

        assertThat(p).contains(new ApiKeyPrincipal("tenant-1", "key-1"));
        verifyNoInteractions(identity);
    }

    @Test
    void cacheMissValidKeyIsCachedWithValidTtl() {
        when(ops.get(CACHE_KEY)).thenReturn(null);
        when(identity.validate(KEY)).thenReturn(new ValidationResult(true, null, "tenant-1", "key-1"));

        Optional<ApiKeyPrincipal> p = resolver.resolve(KEY);

        assertThat(p).contains(new ApiKeyPrincipal("tenant-1", "key-1"));
        verify(ops).set(CACHE_KEY, "tenant-1|key-1", Duration.ofMinutes(5));
    }

    @Test
    void invalidKeyIsNegativelyCachedWithShortTtl() {
        when(ops.get(CACHE_KEY)).thenReturn(null);
        when(identity.validate(KEY)).thenReturn(new ValidationResult(false, "REVOKED", null, null));

        assertThat(resolver.resolve(KEY)).isEmpty();
        verify(ops).set(CACHE_KEY, "INVALID", Duration.ofSeconds(30));
    }

    @Test
    void negativeCacheHitSkipsIdentity() {
        when(ops.get(CACHE_KEY)).thenReturn("INVALID");

        assertThat(resolver.resolve(KEY)).isEmpty();
        verifyNoInteractions(identity);
    }

    @Test
    void identityOutageFailsClosedAndIsNeverCached() {
        when(ops.get(CACHE_KEY)).thenReturn(null);
        when(identity.validate(KEY)).thenThrow(new IdentityUnavailableException("down", null));

        assertThatThrownBy(() -> resolver.resolve(KEY)).isInstanceOf(IdentityUnavailableException.class);
        verify(ops, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void malformedKeyTouchesNeitherRedisNorIdentity() {
        assertThat(resolver.resolve("not-our-format")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
        verifyNoInteractions(identity);
        verifyNoInteractions(ops);
    }
}
