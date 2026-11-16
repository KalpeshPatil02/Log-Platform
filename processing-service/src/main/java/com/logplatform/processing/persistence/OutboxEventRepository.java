package com.logplatform.processing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEventEntity, Long> {

 @Modifying
 @Query(value = """
            INSERT INTO outbox_events (
                event_key,
                payload,
                status,
                attempt_count,
                next_attempt_at,
                created_at
            )
            VALUES (
                :eventKey,
                :payload,
                'PENDING',
                0,
                :now,
                :now
            )
            ON DUPLICATE KEY UPDATE event_key = event_key
            """, nativeQuery = true)
 int insertIfAbsent(
         @Param("eventKey") String eventKey,
         @Param("payload") String payload,
         @Param("now") Instant now
 );

 List<OutboxEventEntity>
 findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
         String status,
         Instant now
 );
}