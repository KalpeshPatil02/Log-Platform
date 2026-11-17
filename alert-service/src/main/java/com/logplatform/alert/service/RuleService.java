package com.logplatform.alert.service;
import com.logplatform.alert.domain.RuleEntity; import com.logplatform.alert.repository.RuleRepository; import com.logplatform.alert.web.RuleRequest; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service public class RuleService {private final RuleRepository repo;public RuleService(RuleRepository r){repo=r;}
 @Transactional public RuleEntity create(String tenant,RuleRequest q){RuleEntity r=new RuleEntity();apply(r,q);r.setTenantId(tenant);return repo.save(r);}
 @Transactional(readOnly=true) public List<RuleEntity> list(String tenant){return repo.findByTenantId(tenant);}
 @Transactional public RuleEntity update(String tenant,long id,RuleRequest q){RuleEntity r=repo.findByIdAndTenantId(id,tenant).orElseThrow(()->new NoSuchElementException("Alert rule not found"));apply(r,q);return r;}
 @Transactional public void delete(String tenant,long id){RuleEntity r=repo.findByIdAndTenantId(id,tenant).orElseThrow(()->new NoSuchElementException("Alert rule not found"));r.setEnabled(false);}
 private void apply(RuleEntity r,RuleRequest q){if(q.severity()!=null&&!Set.of("TRACE","DEBUG","INFO","WARN","ERROR","FATAL").contains(q.severity().toUpperCase()))throw new IllegalArgumentException("Unsupported severity");r.setName(q.name());r.setDescription(q.description());r.setSeverity(q.severity()==null?null:q.severity().toUpperCase());r.setServiceName(q.serviceName());r.setEnvironment(q.environment());r.setThresholdCount(q.thresholdCount());r.setWindowSeconds(q.windowSeconds());r.setCooldownSeconds(q.cooldownSeconds());r.setEnabled(q.enabled());}
}
