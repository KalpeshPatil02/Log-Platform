package com.logplatform.ingestion.security;

import com.logplatform.common.Headers;
import com.logplatform.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates BEFORE the body is parsed. Same message for unknown/revoked/expired keys on purpose.
 * Exceptions thrown here go through GlobalExceptionHandler, so errors have one consistent shape.
 */
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {
    public static final String ATTR_PRINCIPAL = "ingestion.principal";

    private final ApiKeyResolver resolver;

    public ApiKeyAuthInterceptor(ApiKeyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = request.getHeader(Headers.X_API_KEY);
        if (key == null || key.isBlank()) {
            throw ApiException.unauthorized("Missing X-API-KEY header");
        }
        ApiKeyPrincipal principal = resolver.resolve(key.trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid API key"));
        request.setAttribute(ATTR_PRINCIPAL, principal);
        return true;
    }
}
