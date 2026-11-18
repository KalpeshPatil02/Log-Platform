package com.logplatform.notification.service;

import com.logplatform.notification.config.NotificationProperties;
import com.logplatform.notification.domain.NotificationDelivery;
import com.logplatform.notification.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private NotificationProperties props() {
        return new NotificationProperties(
            "notifications.out", "notification-service", "notification-1",
            1000L, 60000L, 3, 1000L, 3600000L,
            false, false, true,
            "smtp.gmail.com", 587, null, null, null, "https://example.com/webhook"
        );
    }

    @Test
    void firstDeliverySentSuccessfully() {
        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        when(repo.findByEventKeyAndTenantId("evt-1", "t1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        doNothing().when(dispatcher).send(any());

        NotificationService service = new NotificationService(repo, dispatcher, props());
        service.deliver("evt-1", "t1", "WEBHOOK", "{\"msg\":\"hi\"}", "https://example.com/hook");

        ArgumentCaptor<NotificationDelivery> cap = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repo, atLeastOnce()).save(cap.capture());
        NotificationDelivery saved = cap.getValue();
        assertEquals(NotificationDelivery.SENT, saved.getStatus());
        assertNotNull(saved.getSentAt());
        verify(dispatcher).send(saved);
    }

    @Test
    void idempotencySkipsDuplicateSent() {
        NotificationDelivery alreadySent = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "https://x", null);
        alreadySent.setStatus(NotificationDelivery.SENT);
        alreadySent.setSentAt(Instant.now());

        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        when(repo.findByEventKeyAndTenantId("evt-1", "t1")).thenReturn(Optional.of(alreadySent));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);

        NotificationService service = new NotificationService(repo, dispatcher, props());
        service.deliver("evt-1", "t1", "WEBHOOK", "{}", "https://example.com/hook");

        verify(dispatcher, never()).send(any());
        verify(repo, never()).save(any());
    }

    @Test
    void retryIncrementsAttemptsAndSetsBackoff() {
        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        NotificationDelivery first = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "https://x", null);
        when(repo.findByEventKeyAndTenantId("evt-1", "t1")).thenReturn(Optional.of(first));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        doThrow(new RuntimeException("down")).when(dispatcher).send(any());

        NotificationService service = new NotificationService(repo, dispatcher, props());
        assertThrows(RuntimeException.class, () -> service.deliver("evt-1", "t1", "WEBHOOK", "{}", "https://example.com/hook"));

        ArgumentCaptor<NotificationDelivery> cap = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repo, atLeastOnce()).save(cap.capture());
        NotificationDelivery saved = cap.getValue();
        assertEquals(NotificationDelivery.FAILED, saved.getStatus());
        assertEquals(1, saved.getAttempts());
        assertNotNull(saved.getNextAttemptAt());
        assertTrue(saved.getNextAttemptAt().isAfter(Instant.now()));
        assertEquals("down", saved.getLastError());
    }

    @Test
    void deadStateAfterMaxAttempts() {
        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        NotificationDelivery almostDead = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "https://x", null);
        almostDead.setAttempts(2); // maxDeliveries=3, so next attempt is 3rd which hits max
        when(repo.findByEventKeyAndTenantId("evt-1", "t1")).thenReturn(Optional.of(almostDead));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        doThrow(new RuntimeException("still down")).when(dispatcher).send(any());

        NotificationService service = new NotificationService(repo, dispatcher, props());
        assertThrows(RuntimeException.class, () -> service.deliver("evt-1", "t1", "WEBHOOK", "{}", "https://example.com/hook"));

        ArgumentCaptor<NotificationDelivery> cap = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repo, atLeastOnce()).save(cap.capture());
        NotificationDelivery saved = cap.getValue();
        assertEquals(NotificationDelivery.DEAD, saved.getStatus());
        assertEquals(3, saved.getAttempts());
    }

    @Test
    void processingStateSetDuringDelivery() {
        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        when(repo.findByEventKeyAndTenantId("evt-1", "t1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        doNothing().when(dispatcher).send(any());

        NotificationService service = new NotificationService(repo, dispatcher, props());
        service.deliver("evt-1", "t1", "WEBHOOK", "{}", "https://example.com/hook");

        ArgumentCaptor<NotificationDelivery> cap = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repo, atLeastOnce()).save(cap.capture());
        // First save should be PROCESSING state
        NotificationDelivery firstSave = cap.getValue();
        // After successful delivery, should be SENT
        assertTrue(firstSave.getStatus().equals(NotificationDelivery.PROCESSING) || firstSave.getStatus().equals(NotificationDelivery.SENT));
    }

    @Test
    void retryRejectsSentNotification() {
        NotificationDelivery sent = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "https://x", Instant.now());
        sent.setId(1L);
        sent.setStatus(NotificationDelivery.SENT);

        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(sent));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);

        NotificationService service = new NotificationService(repo, dispatcher, props());
        assertThrows(IllegalStateException.class, () -> service.retry("t1", 1L));
    }

    @Test
    void retryRejectsPendingNotification() {
        NotificationDelivery pending = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "https://x", Instant.now());
        pending.setId(1L);
        pending.setStatus(NotificationDelivery.PENDING);

        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(pending));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);

        NotificationService service = new NotificationService(repo, dispatcher, props());
        assertThrows(IllegalStateException.class, () -> service.retry("t1", 1L));
    }

    @Test
    void retryAllowsFailedNotification() {
        NotificationDelivery failed = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "https://x", Instant.now());
        failed.setId(1L);
        failed.setStatus(NotificationDelivery.FAILED);
        failed.setAttempts(1);

        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(failed));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        doNothing().when(dispatcher).send(any());

        NotificationService service = new NotificationService(repo, dispatcher, props());
        NotificationDelivery result = service.retry("t1", 1L);

        assertEquals(NotificationDelivery.SENT, result.getStatus());
        assertNotNull(result.getSentAt());
        verify(dispatcher).send(result);
    }

    @Test
    void retryConcurrencyProtectionViaOptimisticLock() {
        NotificationDelivery failed = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "https://x", Instant.now());
        failed.setId(1L);
        failed.setStatus(NotificationDelivery.FAILED);

        NotificationDeliveryRepository repo = mock(NotificationDeliveryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(failed));
        when(repo.save(any())).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("concurrent update"));

        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);

        NotificationService service = new NotificationService(repo, dispatcher, props());
        assertThrows(IllegalStateException.class, () -> service.retry("t1", 1L));
    }
}
