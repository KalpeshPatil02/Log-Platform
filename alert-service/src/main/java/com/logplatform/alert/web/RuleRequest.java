package com.logplatform.alert.web;
import jakarta.validation.constraints.*;
public record RuleRequest(@NotBlank @Size(max=150) String name,@Size(max=500) String description,String severity,String serviceName,String environment,@Min(1) @Max(100000) int thresholdCount,@Min(1) @Max(86400) int windowSeconds,@Min(0) @Max(86400) int cooldownSeconds,boolean enabled) { }
