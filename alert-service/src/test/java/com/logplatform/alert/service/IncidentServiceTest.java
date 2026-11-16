package com.logplatform.alert.service;

import com.logplatform.alert.domain.IncidentEntity;
import com.logplatform.alert.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentServiceTest {

    private IncidentRepository repo;
    private IncidentService service;

    @BeforeEach
    void setUp() {
        repo = mock(IncidentRepository.class);
        service = new IncidentService(repo);
    }

    @Test
    void listReturnsAllIncidentsForTenantWhenStatusIsNull() {
        List<IncidentEntity> expected = List.of(incident(), incident());
        when(repo.findByTenantIdOrderByLastSeenAtDesc(42L)).thenReturn(expected);

        List<IncidentEntity> result = service.list(42L, null);

        assertSame(expected, result);
        verify(repo).findByTenantIdOrderByLastSeenAtDesc(42L);
        verify(repo, never()).findByTenantIdAndStatusOrderByLastSeenAtDesc(anyLong(), anyString());
    }

    @Test
    void listNormalizesStatusToUppercase() {
        List<IncidentEntity> expected = List.of(incident());
        when(repo.findByTenantIdAndStatusOrderByLastSeenAtDesc(42L, "ACKNOWLEDGED"))
                .thenReturn(expected);

        List<IncidentEntity> result = service.list(42L, "acknowledged");

        assertSame(expected, result);
        verify(repo).findByTenantIdAndStatusOrderByLastSeenAtDesc(42L, "ACKNOWLEDGED");
    }

    @Test
    void getReturnsIncidentBelongingToTenant() {
        IncidentEntity expected = incident();
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(expected));

        IncidentEntity result = service.get(42L, 7L);

        assertSame(expected, result);
        verify(repo).findByIdAndTenantId(7L, 42L);
    }

    @Test
    void getThrowsWhenIncidentDoesNotBelongToTenantOrDoesNotExist() {
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.empty());

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.get(42L, 7L)
        );

        assertEquals("Incident not found", exception.getMessage());
    }

    @Test
    void acknowledgeChangesOpenIncidentToAcknowledged() {
        IncidentEntity incident = incident();
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(incident));

        IncidentEntity result = service.acknowledge(42L, 7L, "admin-user");

        assertSame(incident, result);
        assertEquals("ACKNOWLEDGED", result.getStatus());
        assertEquals("admin-user", result.getAcknowledgedBy());
    }

    @Test
    void acknowledgeRejectsIncidentThatIsNotOpen() {
        IncidentEntity incident = incident();
        incident.acknowledge("first-admin");
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(incident));

        assertThrows(
                IllegalStateException.class,
                () -> service.acknowledge(42L, 7L, "second-admin")
        );

        assertEquals("ACKNOWLEDGED", incident.getStatus());
        assertEquals("first-admin", incident.getAcknowledgedBy());
    }

    @Test
    void assignUpdatesAssignee() {
        IncidentEntity incident = incident();
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(incident));

        IncidentEntity result = service.assign(42L, 7L, "on-call-engineer");

        assertSame(incident, result);
        assertEquals("on-call-engineer", result.getAssignedTo());
    }

    @Test
    void assignRejectsResolvedIncident() {
        IncidentEntity incident = incident();
        incident.resolve(Instant.parse("2026-11-14T06:00:00Z"));
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(incident));

        assertThrows(
                IllegalStateException.class,
                () -> service.assign(42L, 7L, "on-call-engineer")
        );

        assertNull(incident.getAssignedTo());
    }

    @Test
    void resolveChangesIncidentToResolved() {
        IncidentEntity incident = incident();
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(incident));

        IncidentEntity result = service.resolve(42L, 7L);

        assertSame(incident, result);
        assertEquals("RESOLVED", result.getStatus());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    void resolvingAlreadyResolvedIncidentDoesNotChangeOriginalResolvedAt() {
        IncidentEntity incident = incident();
        Instant originalResolvedAt = Instant.parse("2026-11-14T06:00:00Z");
        incident.resolve(originalResolvedAt);
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(incident));

        IncidentEntity result = service.resolve(42L, 7L);

        assertSame(incident, result);
        assertEquals("RESOLVED", result.getStatus());
        assertEquals(originalResolvedAt, result.getResolvedAt());
    }

    private IncidentEntity incident() {
        Instant now = Instant.parse("2026-11-14T05:00:00Z");
        return new IncidentEntity(
                42L,
                3L,
                "fingerprint-123",
                "Payment API errors",
                "Error threshold reached",
                now
        );
    }
}
