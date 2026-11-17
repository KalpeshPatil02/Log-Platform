package com.logplatform.alert.repository;
import com.logplatform.alert.domain.RuleEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional;
public interface RuleRepository extends JpaRepository<RuleEntity,Long>{ List<RuleEntity> findByTenantIdAndEnabledTrue(String tenantId); List<RuleEntity> findByTenantId(String tenantId); Optional<RuleEntity> findByIdAndTenantId(Long id,String tenantId); }
