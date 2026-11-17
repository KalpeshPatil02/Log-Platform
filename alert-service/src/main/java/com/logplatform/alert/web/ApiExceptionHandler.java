package com.logplatform.alert.web;
import org.springframework.http.*;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(ForbiddenException.class) @ResponseStatus(HttpStatus.FORBIDDEN) public Map<String,Object> forbidden(Exception e){return Map.of("status",403,"error","FORBIDDEN","message",e.getMessage());}
 @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND) public Map<String,Object> notFound(Exception e){return Map.of("status",404,"error","NOT_FOUND","message",e.getMessage());}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST) public Map<String,Object> bad(Exception e){return Map.of("status",400,"error","BAD_REQUEST","message",e.getMessage()==null?"Invalid request":e.getMessage());}
 @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public Map<String,Object> validation(MethodArgumentNotValidException e){return Map.of("status",400,"error","VALIDATION_ERROR","message","Request validation failed");}
}
