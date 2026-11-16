package com.logplatform.web;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
/** The single error body returned by every service. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String code, String message, String correlationId, Map<String, String> fieldErrors) {}
