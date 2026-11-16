package com.logplatform.alert;
import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertServiceApplication {
 public static void main(String[] args) { SpringApplication.run(AlertServiceApplication.class, args); }
}
