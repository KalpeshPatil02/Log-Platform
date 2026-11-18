package com.logplatform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class PrometheusSecurityFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private PrometheusSecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PrometheusSecurityFilter("secret-token");
    }

    @Test
    void prometheusEndpointWithValidTokenPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/prometheus");
        when(request.getHeader(PrometheusSecurityFilter.INTERNAL_TOKEN_HEADER)).thenReturn("secret-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void prometheusEndpointWithoutTokenRejected() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/prometheus");
        when(request.getHeader(PrometheusSecurityFilter.INTERNAL_TOKEN_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal token required");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void prometheusEndpointWithWrongTokenRejected() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/prometheus");
        when(request.getHeader(PrometheusSecurityFilter.INTERNAL_TOKEN_HEADER)).thenReturn("wrong-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal token required");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void nonPrometheusEndpointPassesThroughWithoutToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/logs");
        lenient().when(request.getHeader(PrometheusSecurityFilter.INTERNAL_TOKEN_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
}
