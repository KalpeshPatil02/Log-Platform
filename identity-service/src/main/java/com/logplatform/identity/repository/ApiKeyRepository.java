package com.logplatform.identity.repository;

import com.logplatform.identity.domain.ApiKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    Optional<ApiKey> findByIdAndTenantId(String id, String tenantId);
    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
