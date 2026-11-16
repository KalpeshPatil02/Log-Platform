package com.logplatform.alert.web;
import com.logplatform.alert.domain.RuleEntity;
public record RuleResponse(Long id,Long tenantId,String name,String description,String severity,String serviceName,String environment,int thresholdCount,int windowSeconds,int cooldownSeconds,boolean enabled){ public static RuleResponse from(RuleEntity r){return new RuleResponse(r.getId(),r.getTenantId(),r.getName(),r.getDescription(),r.getSeverity(),r.getServiceName(),r.getEnvironment(),r.getThresholdCount(),r.getWindowSeconds(),r.getCooldownSeconds(),r.isEnabled());} }
