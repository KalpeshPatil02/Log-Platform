package com.logplatform.query.web;

import com.logplatform.query.service.LogNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

 @ExceptionHandler(LogNotFoundException.class)
 public ResponseEntity<?> notFound(LogNotFoundException ex) {
  return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
          Map.of(
                  "timestamp", Instant.now(),
                  "status", 404,
                  "error", "Not Found",
                  "message", ex.getMessage()
          )
  );
 }

 @ExceptionHandler({
         IllegalArgumentException.class,
         ConstraintViolationException.class,
         MethodArgumentTypeMismatchException.class
 })
 public ResponseEntity<?> badRequest(Exception ex) {
  return ResponseEntity.badRequest().body(
          Map.of(
                  "timestamp", Instant.now(),
                  "status", 400,
                  "error", "Bad Request",
                  "message", ex.getMessage() == null
                          ? "Invalid request parameters"
                          : ex.getMessage()
          )
  );
 }
}