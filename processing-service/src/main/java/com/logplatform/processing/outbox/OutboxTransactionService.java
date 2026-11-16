package com.logplatform.processing.outbox;

import com.logplatform.processing.persistence.OutboxEventEntity;
import com.logplatform.processing.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxTransactionService {

    private final OutboxEventRepository repository;

    public OutboxTransactionService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markPublished(Long id) {
        repository.findById(id).ifPresent(event ->
                event.published(Instant.now())
        );
    }

    @Transactional
    public void markFailed(Long id, String error) {
        repository.findById(id).ifPresent(event -> {
            long delay = Math.min(
                    300,
                    1L << Math.min(event.getAttemptCount() + 1, 8)
            );

            event.failed(
                    error,
                    Instant.now().plusSeconds(delay)
            );
        });
    }
}