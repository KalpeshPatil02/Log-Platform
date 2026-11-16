package com.logplatform.query.service;
import com.logplatform.query.repository.ProcessedLogRepository;
import com.logplatform.query.web.LogResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Service
@Transactional(readOnly=true)
public class LogQueryService {
 private final ProcessedLogRepository repository;
 public LogQueryService(ProcessedLogRepository repository){this.repository=repository;}
 public Page<LogResponse> search(String tenantId,String severity,String serviceName,String environment,
 Instant from,Instant to,String keyword,int page,int size) {
  if(tenantId==null||tenantId.isBlank()) throw new IllegalArgumentException("Tenant context is required");
  if(from!=null&&to!=null&&from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
  if(page<0||size<1||size>100) throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
  Pageable pageable=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"occurredAt").and(Sort.by(Sort.Direction.DESC,"id")));
  return repository.search(tenantId,norm(severity),norm(serviceName),norm(environment),from,to,norm(keyword),pageable).map(LogResponse::from);
 }
 public LogResponse findByEventId(String tenantId,String eventId) {
  if(tenantId==null||tenantId.isBlank()) throw new IllegalArgumentException("Tenant context is required");
  return repository.findByTenantIdAndEventId(tenantId,eventId).map(LogResponse::from).orElseThrow(()->new LogNotFoundException(eventId));
 }
 private String norm(String s){return s==null||s.isBlank()?null:s.trim();}
}
