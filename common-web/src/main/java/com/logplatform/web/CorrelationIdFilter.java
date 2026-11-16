package com.logplatform.web;
import com.logplatform.common.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
/** Reads X-Correlation-Id (set by the gateway) or creates one, and exposes it to logs via MDC key "correlationId". */
public class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String MDC_KEY = "correlationId";
  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String id = req.getHeader(Headers.X_CORRELATION_ID);
    if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
    MDC.put(MDC_KEY, id);
    res.setHeader(Headers.X_CORRELATION_ID, id);
    try { chain.doFilter(req, res); } finally { MDC.remove(MDC_KEY); }
  }
}
