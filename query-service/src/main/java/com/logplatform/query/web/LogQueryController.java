package com.logplatform.query.web;
import com.logplatform.query.service.LogQueryService;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import java.time.Instant;
@RestController
@RequestMapping("/api/v1/logs")
@Validated
public class LogQueryController {
 private final LogQueryService service;
 public LogQueryController(LogQueryService service){this.service=service;}
 // X-Tenant-Id must be injected by the trusted gateway, never trusted from public client input.
 @GetMapping
 public Page<LogResponse> search(@RequestHeader("X-Tenant-Id") String tenantId,
 @RequestParam(required=false) String severity,@RequestParam(required=false) String serviceName,
 @RequestParam(required=false) String environment,
 @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
 @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
 @RequestParam(required=false) String keyword,@RequestParam(defaultValue="0") @Min(0) int page,
 @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
  return service.search(tenantId,severity,serviceName,environment,from,to,keyword,page,size);
 }
 @GetMapping("/{eventId}")
 public LogResponse get(@RequestHeader("X-Tenant-Id") String tenantId,@PathVariable String eventId) {
  return service.findByEventId(tenantId,eventId);
 }
}
