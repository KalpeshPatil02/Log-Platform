package com.logplatform.alert.web;

import com.logplatform.alert.domain.IncidentEntity;
import com.logplatform.alert.service.IncidentService;
import com.logplatform.alert.service.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleEnforcementTest {

    private RuleService rules;
    private IncidentService incidents;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rules = mock(RuleService.class);
        incidents = mock(IncidentService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AlertRuleController(rules), new IncidentController(incidents))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void viewerCanReadButCannotDeleteRules() throws Exception {
        mvc.perform(get("/api/v1/alert-rules").header("X-Tenant-Id", "t1").header("X-User-Roles", "VIEWER"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/alert-rules/1").header("X-Tenant-Id", "t1").header("X-User-Roles", "VIEWER"))
                .andExpect(status().isForbidden());
        verify(rules, never()).delete(anyString(), anyLong());
    }

    @Test
    void developerAndTenantAdminCanDeleteRules() throws Exception {
        mvc.perform(delete("/api/v1/alert-rules/1").header("X-Tenant-Id", "t1").header("X-User-Roles", "DEVELOPER"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/alert-rules/2").header("X-Tenant-Id", "t1").header("X-User-Roles", "TENANT_ADMIN"))
                .andExpect(status().isNoContent());
        verify(rules).delete("t1", 1L);
        verify(rules).delete("t1", 2L);
    }

    @Test
    void missingOrUnknownRolesAreDenied() throws Exception {
        mvc.perform(get("/api/v1/alert-rules").header("X-Tenant-Id", "t1"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alert-rules").header("X-Tenant-Id", "t1").header("X-User-Roles", "HACKER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rules);
    }

    @Test
    void viewerCannotChangeIncidentsButDeveloperCan() throws Exception {
        IncidentEntity incident = new IncidentEntity("t1", 1L, "fp", "title", "desc", Instant.now());
        when(incidents.resolve(eq("t1"), eq(1L))).thenReturn(incident);

        mvc.perform(post("/api/v1/incidents/1/resolve").header("X-Tenant-Id", "t1").header("X-User-Roles", "VIEWER"))
                .andExpect(status().isForbidden());
        verify(incidents, never()).resolve(anyString(), anyLong());

        mvc.perform(post("/api/v1/incidents/1/resolve").header("X-Tenant-Id", "t1").header("X-User-Roles", "DEVELOPER"))
                .andExpect(status().isOk());
        verify(incidents).resolve("t1", 1L);
    }

    @Test
    void rolesHeaderMayContainSeveralRoles() {
        assertDoesNotThrow(() -> RoleGuard.requireWrite("VIEWER, developer"));
        assertThrows(ForbiddenException.class, () -> RoleGuard.requireWrite("VIEWER,PLATFORM_ADMIN"));
        assertThrows(ForbiddenException.class, () -> RoleGuard.requireWrite(null));
    }
}
