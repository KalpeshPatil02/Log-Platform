package com.logplatform.alert.web;
import com.logplatform.alert.service.IncidentService;import jakarta.validation.Valid;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/incidents") public class IncidentController {private final IncidentService service;public IncidentController(IncidentService s){service=s;}
 private long tenant(String h){try{return Long.parseLong(h);}catch(Exception e){throw new IllegalArgumentException("Missing or invalid trusted X-Tenant-Id");}}
 @GetMapping public List<IncidentResponse> list(@RequestHeader("X-Tenant-Id") String t,@RequestParam(required=false) String status){return service.list(tenant(t),status).stream().map(IncidentResponse::from).toList();}
 @GetMapping("/{id}") public IncidentResponse get(@RequestHeader("X-Tenant-Id") String t,@PathVariable long id){return IncidentResponse.from(service.get(tenant(t),id));}
 @PostMapping("/{id}/acknowledge") public IncidentResponse acknowledge(@RequestHeader("X-Tenant-Id") String t,@RequestHeader(value="X-User-Id",defaultValue="system") String actor,@PathVariable long id){return IncidentResponse.from(service.acknowledge(tenant(t),id,actor));}
 @PostMapping("/{id}/assign") public IncidentResponse assign(@RequestHeader("X-Tenant-Id") String t,@Valid @RequestBody ActionRequest q,@PathVariable long id){return IncidentResponse.from(service.assign(tenant(t),id,q.value()));}
 @PostMapping("/{id}/resolve") public IncidentResponse resolve(@RequestHeader("X-Tenant-Id") String t,@PathVariable long id){return IncidentResponse.from(service.resolve(tenant(t),id));}
}
