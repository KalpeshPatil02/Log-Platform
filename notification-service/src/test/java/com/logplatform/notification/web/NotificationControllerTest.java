package com.logplatform.notification.web;

import com.logplatform.notification.domain.NotificationDelivery;
import com.logplatform.notification.repository.NotificationDeliveryRepository;
import com.logplatform.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationDeliveryRepository repo;

    @Mock
    private NotificationService service;

    @InjectMocks
    private NotificationController controller;

    private MockMvc mvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void viewerCanListNotifications() throws Exception {
        NotificationDelivery d = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "http://1.2.3.4/hook", Instant.now());
        d.setId(1L);
        d.setStatus(NotificationDelivery.SENT);
        Page<NotificationDelivery> page = new PageImpl<>(List.of(d), PageRequest.of(0, 20), 1);
        when(repo.findByTenantId(eq("t1"), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        mvc.perform(get("/api/v1/notifications")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCanGetNotification() throws Exception {
        NotificationDelivery d = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "http://1.2.3.4/hook", Instant.now());
        d.setId(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(d));

        mvc.perform(get("/api/v1/notifications/1")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotRetry() throws Exception {
        mvc.perform(post("/api/v1/notifications/1/retry")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "VIEWER"))
                .andExpect(status().isForbidden());
        verify(service, never()).retry(eq("t1"), eq(1L));
    }

    @Test
    void developerCanRetry() throws Exception {
        NotificationDelivery d = new NotificationDelivery("evt-1", "t1", "WEBHOOK", "{}", "http://1.2.3.4/hook", Instant.now());
        d.setId(1L);
        d.setStatus(NotificationDelivery.SENT);
        when(service.retry("t1", 1L)).thenReturn(d);
        mvc.perform(post("/api/v1/notifications/1/retry")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "DEVELOPER"))
                .andExpect(status().isOk());
        verify(service).retry("t1", 1L);
    }

    @Test
    void missingRolesDenied() throws Exception {
        mvc.perform(get("/api/v1/notifications")
                        .header("X-Tenant-Id", "t1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRoleDenied() throws Exception {
        mvc.perform(get("/api/v1/notifications")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "HACKER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTenantIdDenied() throws Exception {
        mvc.perform(get("/api/v1/notifications")
                        .header("X-User-Roles", "VIEWER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantIsolationGet() throws Exception {
        when(repo.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/notifications/1")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "VIEWER"))
                .andExpect(status().isNotFound());
        verify(repo).findById(1L);
    }

    @Test
    void listFiltersByStatus() throws Exception {
        Page<NotificationDelivery> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(repo.findByTenantIdAndStatus(eq("t1"), eq("FAILED"), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        mvc.perform(get("/api/v1/notifications")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "VIEWER")
                        .param("status", "FAILED"))
                .andExpect(status().isOk());
        verify(repo).findByTenantIdAndStatus(eq("t1"), eq("FAILED"), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void listWithPaginationAndSort() throws Exception {
        Page<NotificationDelivery> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(repo.findByTenantId(eq("t1"), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        mvc.perform(get("/api/v1/notifications")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "VIEWER")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk());
        verify(repo).findByTenantId("t1", org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdAt")));
    }

    @Test
    void tenantIsolationPreventsCrossTenantAccess() throws Exception {
        NotificationDelivery otherTenant = new NotificationDelivery("evt-2", "t2", "WEBHOOK", "{}", "http://1.2.3.4/hook", Instant.now());
        otherTenant.setId(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(otherTenant));
        mvc.perform(get("/api/v1/notifications/1")
                        .header("X-Tenant-Id", "t1")
                        .header("X-User-Roles", "VIEWER"))
                .andExpect(status().isNotFound());
    }
}
