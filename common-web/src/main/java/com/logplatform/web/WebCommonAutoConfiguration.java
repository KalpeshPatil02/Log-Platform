package com.logplatform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebCommonAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler();
  }

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    FilterRegistrationBean<CorrelationIdFilter> r = new FilterRegistrationBean<>(new CorrelationIdFilter());
    r.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return r;
  }

  @Bean
  FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
      ObjectMapper objectMapper,
      @Value("${platform.web.max-request-bytes:5242880}") long maxBytes) {
    FilterRegistrationBean<RequestSizeLimitFilter> r =
        new FilterRegistrationBean<>(new RequestSizeLimitFilter(maxBytes, objectMapper));
    r.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return r;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
  static class SecurityAdviceConfiguration {
    @Bean
    SecurityErrorAdvice securityErrorAdvice() {
      return new SecurityErrorAdvice();
    }
  }
}
