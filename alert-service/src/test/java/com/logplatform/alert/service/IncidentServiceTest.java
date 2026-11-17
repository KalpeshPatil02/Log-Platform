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
        when(repo.findByTenantIdOrderByLastSeenAtDesc("tenant-42")).thenReturn(expected);

        List<IncidentEntity> result = service.list("tenant-42", null);

        assertSame(expected, result);
        verify(repo).findByTenantIdOrderByLastSeenAtDesc("tenant-42");
        verify(repo, never()).findByTenantIdAndStatusOrderByLastSeenAtDesc(anyString(), anyString());
    }

    @Test
    void listNormalizesStatusToUppercase() {
        List<IncidentEntity> expected = List.of(incident());
        when(repo.findByTenantIdAndStatusOrderByLastSeenAtDesc("tenant-42", "ACKNOWLEDGED"))
                .thenReturn(expected);

        List<IncidentEntity> result = service.list("tenant-42", "acknowledged");

        assertSame(expected, result);
        verify(repo).findByTenantIdAndStatusOrderByLastSeenAtDesc("tenant-42", "ACKNOWLEDGED");
    }

    @Test
    void getReturnsIncidentBelongingToTenant() {
        IncidentEntity expected = incident();
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.of(expected));

        IncidentEntity result = service.get("tenant-42", 7L);

        assertSame(expected, result);
        verify(repo).findByIdAndTenantId(7L, "tenant-42");
    }

    @Test
    void getThrowsWhenIncidentDoesNotBelongToTenantOrDoesNotExist() {
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.empty());

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.get("tenant-42", 7L)
        );

        assertEquals("Incident not found", exception.getMessage());
    }

    @Test
    void acknowledgeChangesOpenIncidentToAcknowledged() {
        IncidentEntity incident = incident();
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.of(incident));

        IncidentEntity result = service.acknowledge("tenant-42", 7L, "admin-user");

        assertSame(incident, result);
        assertEquals("ACKNOWLEDGED", result.getStatus());
        assertEquals("admin-user", result.getAcknowledgedBy());
    }

    @Test
    void acknowledgeRejectsIncidentThatIsNotOpen() {
        IncidentEntity incident = incident();
        incident.acknowledge("first-admin");
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.of(incident));

        assertThrows(
                IllegalStateException.class,
                () -> service.acknowledge("tenant-42", 7L, "second-admin")
        );

        assertEquals("ACKNOWLEDGED", incident.getStatus());
        assertEquals("first-admin", incident.getAcknowledgedBy());
    }

    @Test
    void assignUpdatesAssignee() {
        IncidentEntity incident = incident();
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.of(incident));

        IncidentEntity result = service.assign("tenant-42", 7L, "on-call-engineer");

        assertSame(incident, result);
        assertEquals("on-call-engineer", result.getAssignedTo());
    }

    @Test
    void assignRejectsResolvedIncident() {
        IncidentEntity incident = incident();
        incident.resolve(Instant.parse("2026-11-14T06:00:00Z"));
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.of(incident));

        assertThrows(
                IllegalStateException.class,
                () -> service.assign("tenant-42", 7L, "on-call-engineer")
        );

        assertNull(incident.getAssignedTo());
    }

    @Test
    void resolveChangesIncidentToResolved() {
        IncidentEntity incident = incident();
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.of(incident));

        IncidentEntity result = service.resolve("tenant-42", 7L);

        assertSame(incident, result);
        assertEquals("RESOLVED", result.getStatus());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    void resolvingAlreadyResolvedIncidentDoesNotChangeOriginalResolvedAt() {
        IncidentEntity incident = incident();
        Instant originalResolvedAt = Instant.parse("2026-11-14T06:00:00Z");
        incident.resolve(originalResolvedAt);
        when(repo.findByIdAndTenantId(7L, "tenant-42")).thenReturn(Optional.of(incident));

        IncidentEntity result = service.resolve("tenant-42", 7L);

        assertSame(incident, result);
        assertEquals("RESOLVED", result.getStatus());
        assertEquals(originalResolvedAt, result.getResolvedAt());
    }

    private IncidentEntity incident() {
        Instant now = Instant.parse("2026-11-14T05:00:00Z");
        return new IncidentEntity(
                "tenant-42",
                3L,
                "fingerprint-123",
                "Payment API errors",
                "Error threshold reached",
                now
        );
    }
}
