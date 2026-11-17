package com.logplatform.alert.web;
import com.logplatform.alert.service.RuleService;import jakarta.validation.Valid;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/alert-rules") public class AlertRuleController {private final RuleService service;public AlertRuleController(RuleService s){service=s;}
 private String tenant(String h){return TenantHeader.require(h);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public RuleResponse create(@RequestHeader("X-Tenant-Id") String t,@Valid @RequestBody RuleRequest q){return RuleResponse.from(service.create(tenant(t),q));}
 @GetMapping public List<RuleResponse> list(@RequestHeader("X-Tenant-Id") String t){return service.list(tenant(t)).stream().map(RuleResponse::from).toList();}
 @PutMapping("/{id}") public RuleResponse update(@RequestHeader("X-Tenant-Id") String t,@PathVariable long id,@Valid @RequestBody RuleRequest q){return RuleResponse.from(service.update(tenant(t),id,q));}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@RequestHeader("X-Tenant-Id") String t,@PathVariable long id){service.delete(tenant(t),id);}
}
