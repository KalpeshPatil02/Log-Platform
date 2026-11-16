package com.logplatform.query.repository;

import com.logplatform.query.domain.ProcessedLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ProcessedLogRepository extends JpaRepository<ProcessedLogEntity, Long> {

    @Query(
        value = "SELECT p FROM ProcessedLogEntity p WHERE p.tenantId = :tenantId " +
                "AND (:severity IS NULL OR p.severity = :severity) " +
                "AND (:serviceName IS NULL OR p.serviceName = :serviceName) " +
                "AND (:environment IS NULL OR p.environment = :environment) " +
                "AND (:fromTime IS NULL OR p.occurredAt >= :fromTime) " +
                "AND (:toTime IS NULL OR p.occurredAt <= :toTime) " +
                "AND (:keyword IS NULL OR LOWER(p.message) LIKE LOWER(CONCAT('%', :keyword, '%')))",
        countQuery = "SELECT COUNT(p) FROM ProcessedLogEntity p WHERE p.tenantId = :tenantId " +
                "AND (:severity IS NULL OR p.severity = :severity) " +
                "AND (:serviceName IS NULL OR p.serviceName = :serviceName) " +
                "AND (:environment IS NULL OR p.environment = :environment) " +
                "AND (:fromTime IS NULL OR p.occurredAt >= :fromTime) " +
                "AND (:toTime IS NULL OR p.occurredAt <= :toTime) " +
                "AND (:keyword IS NULL OR LOWER(p.message) LIKE LOWER(CONCAT('%', :keyword, '%')))"
    )
    Page<ProcessedLogEntity> search(
            @Param("tenantId") String tenantId,
            @Param("severity") String severity,
            @Param("serviceName") String serviceName,
            @Param("environment") String environment,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    Optional<ProcessedLogEntity> findByTenantIdAndEventId(
            String tenantId,
            String eventId
    );
}
