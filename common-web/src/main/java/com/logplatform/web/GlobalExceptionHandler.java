package com.logplatform.web;
import com.logplatform.common.ApiException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> api(ApiException e) { return build(e.getStatus(), e.getCode(), e.getMessage(), null); }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return build(400, "VALIDATION_FAILED", "Request validation failed", fields);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> constraint(ConstraintViolationException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getConstraintViolations().forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
    return build(400, "VALIDATION_FAILED", "Request validation failed", fields);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) {
        return build(400, "MALFORMED_JSON", "Request body contains invalid JSON", null);
      }
      cause = cause.getCause();
    }
    return build(400, "BAD_REQUEST", "Malformed request", null);
  }

  @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiError> badRequest(Exception e) {
    return build(400, "BAD_REQUEST", "Malformed request", null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> other(Exception e) {
    if (e instanceof ErrorResponse er) { // framework exceptions that already know their status (404, 405, 415 ...)
      HttpStatus s = HttpStatus.valueOf(er.getStatusCode().value());
      return build(s.value(), s.name(), s.getReasonPhrase(), null);
    }
    log.error("Unhandled exception", e);
    return build(500, "INTERNAL_ERROR", "Unexpected error", null);
  }

  private ResponseEntity<ApiError> build(int status, String code, String message, Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ApiError(Instant.now(), status, code, message, MDC.get(CorrelationIdFilter.MDC_KEY), fields));
  }
}
