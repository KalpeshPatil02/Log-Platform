package com.logplatform.alert.web;

import com.logplatform.alert.service.RuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {

    private final RuleService service;

    public AlertRuleController(RuleService s) {
        service = s;
    }

    private String tenant(String h) {
        return TenantHeader.require(h);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@RequestHeader("X-Tenant-Id") String t,
                               @RequestHeader(value = "X-User-Roles", required = false) String roles,
                               @Valid @RequestBody RuleRequest q) {
        RoleGuard.requireWrite(roles);
        return RuleResponse.from(service.create(tenant(t), q));
    }

    @GetMapping
    public List<RuleResponse> list(@RequestHeader("X-Tenant-Id") String t,
                                   @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        RoleGuard.requireRead(roles);
        return service.list(tenant(t)).stream().map(RuleResponse::from).toList();
    }

    @PutMapping("/{id}")
    public RuleResponse update(@RequestHeader("X-Tenant-Id") String t,
                               @RequestHeader(value = "X-User-Roles", required = false) String roles,
                               @PathVariable long id,
                               @Valid @RequestBody RuleRequest q) {
        RoleGuard.requireWrite(roles);
        return RuleResponse.from(service.update(tenant(t), id, q));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Tenant-Id") String t,
                       @RequestHeader(value = "X-User-Roles", required = false) String roles,
                       @PathVariable long id) {
        RoleGuard.requireWrite(roles);
        service.delete(tenant(t), id);
    }
}
