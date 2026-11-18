package com.logplatform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects the /actuator/prometheus endpoint by requiring the X-Internal-Token header.
 * Only internal scrapers (Prometheus server) should know this token. Client traffic
 * through the API Gateway never reaches actuator endpoints on backend services.
 */
public class PrometheusSecurityFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private final String internalToken;

    public PrometheusSecurityFilter(String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.contains("/actuator/prometheus")) {
            String token = request.getHeader(INTERNAL_TOKEN_HEADER);
            if (internalToken == null || !internalToken.equals(token)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal token required");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
