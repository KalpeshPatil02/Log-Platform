package com.logplatform.notification.repository;

import com.logplatform.notification.domain.NotificationDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByEventKeyAndTenantId(String eventKey, String tenantId);

    Page<NotificationDelivery> findByTenantId(String tenantId, Pageable pageable);

    Page<NotificationDelivery> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    @Query(value = "SELECT * FROM notification_delivery WHERE status = 'PENDING' AND next_attempt_at <= :now ORDER BY created_at LIMIT :lim",
           nativeQuery = true)
    List<NotificationDelivery> due(@Param("now") Instant now, @Param("lim") int lim);

    List<NotificationDelivery> findByStatusAndNextAttemptAtBefore(String status, Instant now);
}
