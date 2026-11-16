package com.logplatform.ingestion.security;

import com.logplatform.common.Hashing;
import com.logplatform.ingestion.support.CacheKeys;
import com.logplatform.ingestion.config.IngestionProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * API key -> principal, with a Redis cache in front of identity-service.
 * Cache key = CacheKeys.apiKey(sha256(rawKey)), the SAME hash identity stores, so identity can evict on revoke.
 * Valid entries live valid-ttl (revocation eviction is the fast path, TTL the backstop);
 * invalid entries live invalid-ttl (a short negative cache so garbage keys can't hammer identity).
 */
@Service
public class ApiKeyResolver {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyResolver.class);
    private static final String INVALID = "INVALID";

    private final StringRedisTemplate redis;
    private final IdentityClient identity;
    private final IngestionProperties props;

    public ApiKeyResolver(StringRedisTemplate redis, IdentityClient identity, IngestionProperties props) {
        this.redis = redis;
        this.identity = identity;
        this.props = props;
    }

    public Optional<ApiKeyPrincipal> resolve(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("lap_") || rawKey.length() > 128) {
            return Optional.empty();   // cheap rejection, no Redis, no identity call
        }
        String cacheKey = CacheKeys.apiKey(Hashing.sha256Hex(rawKey));

        String cached = read(cacheKey);
        if (cached != null) {
            return INVALID.equals(cached) ? Optional.empty() : Optional.of(parse(cached));
        }

        IdentityClient.ValidationResult result = identity.validate(rawKey);   // throws if identity is down: NOT cached
        if (result.valid()) {
            write(cacheKey, result.tenantId() + "|" + result.apiKeyId(), props.cache().validTtl());
            return Optional.of(new ApiKeyPrincipal(result.tenantId(), result.apiKeyId()));
        }
        write(cacheKey, INVALID, props.cache().invalidTtl());
        return Optional.empty();
    }

    private String read(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("API key cache read failed, falling back to identity-service: {}", e.getMessage());
            return null;
        }
    }

    private void write(String key, String value, java.time.Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            log.warn("API key cache write failed: {}", e.getMessage());
        }
    }

    private static ApiKeyPrincipal parse(String cached) {
        int i = cached.indexOf('|');
        return new ApiKeyPrincipal(cached.substring(0, i), cached.substring(i + 1));
    }
}
