package com.logplatform.ingestion.exception;

import com.logplatform.web.ApiError;
import com.logplatform.common.ApiException;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RedisExceptionHandler {
    @ExceptionHandler(BackpressureException.class)
    ResponseEntity<ApiError> handleBackpressure(BackpressureException ex) {
        return error(503, ex.getCode(), ex.getMessage(), "5");
    }

    @ExceptionHandler(IdentityUnavailableException.class)
    ResponseEntity<ApiError> handleIdentityUnavailable(IdentityUnavailableException ex) {
        return error(503, ex.getCode(), ex.getMessage(), "2");
    }

    private ResponseEntity<ApiError> error(int status, String code, String message, String retryAfter) {
        ApiError body = new ApiError(Instant.now(), status, code, message, MDC.get("correlationId"), null);
        return ResponseEntity.status(status).header(HttpHeaders.RETRY_AFTER, retryAfter).body(body);
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> handleRedis(DataAccessException ex) {
        return error(503, "STORE_UNAVAILABLE", "Log pipeline temporarily unavailable, retry later", "5");
    }
}
