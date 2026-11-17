package com.logplatform.alert.web;

import com.logplatform.alert.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService s) {
        service = s;
    }

    private String tenant(String h) {
        return TenantHeader.require(h);
    }

    @GetMapping
    public List<IncidentResponse> list(@RequestHeader("X-Tenant-Id") String t,
                                       @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                       @RequestParam(required = false) String status) {
        RoleGuard.requireRead(roles);
        return service.list(tenant(t), status).stream().map(IncidentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@RequestHeader("X-Tenant-Id") String t,
                                @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                @PathVariable long id) {
        RoleGuard.requireRead(roles);
        return IncidentResponse.from(service.get(tenant(t), id));
    }

    @PostMapping("/{id}/acknowledge")
    public IncidentResponse acknowledge(@RequestHeader("X-Tenant-Id") String t,
                                        @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                        @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor,
                                        @PathVariable long id) {
        RoleGuard.requireWrite(roles);
        return IncidentResponse.from(service.acknowledge(tenant(t), id, actor));
    }

    @PostMapping("/{id}/assign")
    public IncidentResponse assign(@RequestHeader("X-Tenant-Id") String t,
                                   @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                   @Valid @RequestBody ActionRequest q,
                                   @PathVariable long id) {
        RoleGuard.requireWrite(roles);
        return IncidentResponse.from(service.assign(tenant(t), id, q.value()));
    }

    @PostMapping("/{id}/resolve")
    public IncidentResponse resolve(@RequestHeader("X-Tenant-Id") String t,
                                    @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                    @PathVariable long id) {
        RoleGuard.requireWrite(roles);
        return IncidentResponse.from(service.resolve(tenant(t), id));
    }
}
