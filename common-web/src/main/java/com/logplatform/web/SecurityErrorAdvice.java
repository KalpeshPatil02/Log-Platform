package com.logplatform.web;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
/** Without this, the catch-all handler would turn @PreAuthorize denials into 500s. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityErrorAdvice {
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException e) {
    return ResponseEntity.status(403).body(new ApiError(Instant.now(), 403, "FORBIDDEN", "You do not have permission to perform this action", MDC.get(CorrelationIdFilter.MDC_KEY), null));
  }
}
