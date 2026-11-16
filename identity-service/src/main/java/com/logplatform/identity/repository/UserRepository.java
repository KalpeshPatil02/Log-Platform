package com.logplatform.identity.repository;

import com.logplatform.identity.domain.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByTenantIdAndEmail(String tenantId, String email);
    Optional<User> findByIdAndTenantId(String id, String tenantId);
    boolean existsByTenantIdAndEmail(String tenantId, String email);
    Page<User> findByTenantId(String tenantId, Pageable pageable);
}
