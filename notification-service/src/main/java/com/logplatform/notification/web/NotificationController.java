package com.logplatform.notification.web;

import com.logplatform.notification.domain.NotificationDelivery;
import com.logplatform.notification.repository.NotificationDeliveryRepository;
import com.logplatform.notification.service.NotificationService;
import com.logplatform.notification.web.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationDeliveryRepository repo;
    private final NotificationService service;

    public NotificationController(NotificationDeliveryRepository repo, NotificationService service) {
        this.repo = repo;
        this.service = service;
    }

    private String tenant(String h) {
        return TenantHeader.require(h);
    }

    @GetMapping
    public Page<NotificationResponse> list(
            @RequestHeader("X-Tenant-Id") String t,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        RoleGuard.requireRead(roles);
        String tenantId = tenant(t);

        // Parse sort: "field,asc" or "field,desc"
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, field));

        Page<NotificationDelivery> pageResult;
        if (status != null) {
            pageResult = repo.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            pageResult = repo.findByTenantId(tenantId, pageable);
        }
        return pageResult.map(NotificationResponse::from);
    }

    @GetMapping("/{id}")
    public NotificationResponse get(
            @RequestHeader("X-Tenant-Id") String t,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @PathVariable Long id) {
        RoleGuard.requireRead(roles);
        String tenantId = tenant(t);
        NotificationDelivery d = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found"));
        if (!d.getTenantId().equals(tenantId)) {
            throw new NoSuchElementException("Notification not found");
        }
        return NotificationResponse.from(d);
    }

    @PostMapping("/{id}/retry")
    public NotificationResponse retry(
            @RequestHeader("X-Tenant-Id") String t,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @PathVariable Long id) {
        RoleGuard.requireWrite(roles);
        String tenantId = tenant(t);
        NotificationDelivery retried = service.retry(tenantId, id);
        return NotificationResponse.from(retried);
    }
}
