package com.logplatform.processing;

import com.logplatform.web.PrometheusSecurityFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessingServiceApplication.class, args);
    }

    @Bean
    FilterRegistrationBean<PrometheusSecurityFilter> prometheusSecurityFilter(
            @Value("${INTERNAL_TOKEN:dev-internal-token}") String internalToken) {
        FilterRegistrationBean<PrometheusSecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PrometheusSecurityFilter(internalToken));
        registration.addUrlPatterns("/actuator/prometheus");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
