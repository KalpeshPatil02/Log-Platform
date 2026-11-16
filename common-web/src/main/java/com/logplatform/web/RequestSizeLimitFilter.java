package com.logplatform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestSizeLimitFilter extends OncePerRequestFilter {
    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            ApiError error = new ApiError(
                Instant.now(), 413, "PAYLOAD_TOO_LARGE",
                "Request body exceeds the configured size limit",
                MDC.get(CorrelationIdFilter.MDC_KEY), null
            );
            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
