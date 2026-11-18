package com.logplatform.notification.service;

import com.logplatform.notification.config.NotificationProperties;
import com.logplatform.notification.domain.NotificationDelivery;
import com.logplatform.notification.repository.NotificationDeliveryRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.NoSuchElementException;

@Service
public class NotificationService {

    private final NotificationDeliveryRepository repo;
    private final NotificationDispatcher dispatcher;
    private final NotificationProperties props;

    public NotificationService(NotificationDeliveryRepository repo, NotificationDispatcher dispatcher, NotificationProperties props) {
        this.repo = repo;
        this.dispatcher = dispatcher;
        this.props = props;
    }

    @Transactional
    public void deliver(String eventKey, String tenantId, String channel, String payload, String destination) {
        // Idempotency: skip if already sent for this event+tenant
        if (repo.findByEventKeyAndTenantId(eventKey, tenantId).map(d -> d.getStatus().equals(NotificationDelivery.SENT)).orElse(false)) {
            return;
        }

        NotificationDelivery delivery = repo.findByEventKeyAndTenantId(eventKey, tenantId)
            .map(d -> {
                d.setAttempts(d.getAttempts() + 1);
                d.setStatus(NotificationDelivery.PROCESSING);
                d.setNextAttemptAt(Instant.now());
                return d;
            })
            .orElseGet(() -> new NotificationDelivery(eventKey, tenantId, channel, payload, destination, Instant.now()));

        delivery = repo.save(delivery);

        try {
            dispatcher.send(delivery);
            delivery.setStatus(NotificationDelivery.SENT);
            delivery.setSentAt(Instant.now());
            delivery.setLastError(null);
            repo.save(delivery);
        } catch (Exception ex) {
            int attempts = delivery.getAttempts();
            if (attempts >= props.maxDeliveries()) {
                delivery.setStatus(NotificationDelivery.DEAD);
            } else {
                delivery.setStatus(NotificationDelivery.FAILED);
                long delayMs = Math.min(props.backoffMaxMs(), props.backoffBaseMs() * (1L << attempts));
                delivery.setNextAttemptAt(Instant.now().plusMillis(delayMs));
            }
            delivery.setLastError(ex.getMessage());
            repo.save(delivery);
            throw new RuntimeException("Delivery failed for eventKey=" + eventKey, ex);
        }
    }

    @Transactional
    public void markDead(String eventKey, String tenantId, String error) {
        repo.findByEventKeyAndTenantId(eventKey, tenantId).ifPresent(d -> {
            d.setStatus(NotificationDelivery.DEAD);
            d.setLastError(error);
            repo.save(d);
        });
    }

    @Transactional
    public NotificationDelivery retry(String tenantId, Long id) {
        NotificationDelivery d = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found"));
        if (!d.getTenantId().equals(tenantId)) {
            throw new NoSuchElementException("Notification not found");
        }
        if (!d.getStatus().equals(NotificationDelivery.FAILED) && !d.getStatus().equals(NotificationDelivery.DEAD)) {
            throw new IllegalStateException("Only FAILED or DEAD notifications can be retried");
        }

        d.setStatus(NotificationDelivery.PENDING);
        d.setAttempts(0);
        d.setNextAttemptAt(Instant.now());
        d.setLastError(null);

        try {
            d = repo.save(d);
        } catch (OptimisticLockingFailureException ex) {
            throw new IllegalStateException("Notification is already being processed by another request", ex);
        }

        try {
            dispatcher.send(d);
            d.setStatus(NotificationDelivery.SENT);
            d.setSentAt(Instant.now());
            d.setLastError(null);
            repo.save(d);
        } catch (OptimisticLockingFailureException ex) {
            throw new IllegalStateException("Notification is already being processed by another request", ex);
        } catch (Exception ex) {
            int attempts = d.getAttempts();
            if (attempts >= props.maxDeliveries()) {
                d.setStatus(NotificationDelivery.DEAD);
            } else {
                d.setStatus(NotificationDelivery.FAILED);
                long delayMs = Math.min(props.backoffMaxMs(), props.backoffBaseMs() * (1L << attempts));
                d.setNextAttemptAt(Instant.now().plusMillis(delayMs));
            }
            d.setLastError(ex.getMessage());
            repo.save(d);
            throw new RuntimeException("Retry failed for notification id=" + id, ex);
        }
        return d;
    }
}
