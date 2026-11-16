package com.logplatform.ingestion.config;

import com.logplatform.ingestion.security.ApiKeyAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiKeyWebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuth;

    public ApiKeyWebMvcConfig(ApiKeyAuthInterceptor apiKeyAuth) {
        this.apiKeyAuth = apiKeyAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuth)
                .addPathPatterns("/api/v1/logs", "/api/v1/logs/**");
    }
}