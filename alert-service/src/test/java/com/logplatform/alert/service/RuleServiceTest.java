package com.logplatform.alert.service;

import com.logplatform.alert.domain.RuleEntity;
import com.logplatform.alert.repository.RuleRepository;
import com.logplatform.alert.web.RuleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuleServiceTest {

    private RuleRepository repo;
    private RuleService service;

    @BeforeEach
    void setUp() {
        repo = mock(RuleRepository.class);
        service = new RuleService(repo);
    }

    @Test
    void createSetsTenantAndSavesRule() {
        RuleRequest request = request("Payment errors", "error", true);
        when(repo.save(any(RuleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RuleEntity result = service.create(42L, request);

        assertEquals(42L, result.getTenantId());
        assertEquals("Payment errors", result.getName());
        assertEquals("ERROR", result.getSeverity());
        assertTrue(result.isEnabled());
        verify(repo).save(any(RuleEntity.class));
    }

    @Test
    void listReturnsRulesForTenant() {
        List<RuleEntity> expected = List.of(new RuleEntity(), new RuleEntity());
        when(repo.findByTenantId(42L)).thenReturn(expected);

        List<RuleEntity> result = service.list(42L);

        assertSame(expected, result);
        verify(repo).findByTenantId(42L);
    }

    @Test
    void updateChangesRuleFieldsAndNormalizesSeverity() {
        RuleEntity existing = new RuleEntity();
        existing.setTenantId(42L);
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(existing));

        RuleEntity result = service.update(42L, 7L,
                new RuleRequest("Updated rule", "Updated description", "warn",
                        "billing-api", "production", 10, 120, 30, true));

        assertSame(existing, result);
        assertEquals("Updated rule", result.getName());
        assertEquals("Updated description", result.getDescription());
        assertEquals("WARN", result.getSeverity());
        assertEquals("billing-api", result.getServiceName());
        assertEquals("production", result.getEnvironment());
        assertEquals(10, result.getThresholdCount());
        assertEquals(120, result.getWindowSeconds());
        assertEquals(30, result.getCooldownSeconds());
        assertTrue(result.isEnabled());
    }

    @Test
    void updateThrowsWhenRuleDoesNotBelongToTenantOrDoesNotExist() {
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.empty());

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.update(42L, 7L, request("Rule", "ERROR", true))
        );

        assertEquals("Alert rule not found", exception.getMessage());
        verify(repo, never()).save(any());
    }

    @Test
    void deleteSoftDisablesRuleInsteadOfDeletingIt() {
        RuleEntity existing = new RuleEntity();
        existing.setEnabled(true);
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.of(existing));

        service.delete(42L, 7L);

        assertFalse(existing.isEnabled());
        verify(repo, never()).delete(any(RuleEntity.class));
    }

    @Test
    void deleteThrowsWhenRuleDoesNotBelongToTenantOrDoesNotExist() {
        when(repo.findByIdAndTenantId(7L, 42L)).thenReturn(Optional.empty());

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.delete(42L, 7L)
        );

        assertEquals("Alert rule not found", exception.getMessage());
    }

    @Test
    void createRejectsUnsupportedSeverity() {
        RuleRequest request = request("Payment errors", "URGENT", true);

        assertThrows(IllegalArgumentException.class, () -> service.create(42L, request));

        verify(repo, never()).save(any(RuleEntity.class));
    }

    @Test
    void nullSeverityIsAllowedAndRemainsNull() {
        when(repo.save(any(RuleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RuleEntity result = service.create(42L, request("All severities", null, true));

        assertNull(result.getSeverity());
        verify(repo).save(any(RuleEntity.class));
    }

    private RuleRequest request(String name, String severity, boolean enabled) {
        return new RuleRequest(
                name,
                "Rule description",
                severity,
                "payment-api",
                "production",
                5,
                60,
                300,
                enabled
        );
    }
}
