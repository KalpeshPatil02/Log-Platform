package com.logplatform.alert.service;
import com.logplatform.alert.domain.IncidentEntity;import com.logplatform.alert.repository.IncidentRepository;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.Instant;import java.util.*;
@Service public class IncidentService {private final IncidentRepository repo;public IncidentService(IncidentRepository r){repo=r;}
 @Transactional(readOnly=true) public List<IncidentEntity> list(long tenant,String status){return status==null?repo.findByTenantIdOrderByLastSeenAtDesc(tenant):repo.findByTenantIdAndStatusOrderByLastSeenAtDesc(tenant,status.toUpperCase());}
 @Transactional(readOnly=true) public IncidentEntity get(long tenant,long id){return repo.findByIdAndTenantId(id,tenant).orElseThrow(()->new NoSuchElementException("Incident not found"));}
 @Transactional public IncidentEntity acknowledge(long tenant,long id,String actor){IncidentEntity i=get(tenant,id);i.acknowledge(actor);return i;}
 @Transactional public IncidentEntity assign(long tenant,long id,String assignee){IncidentEntity i=get(tenant,id);i.assign(assignee);return i;}
 @Transactional public IncidentEntity resolve(long tenant,long id){IncidentEntity i=get(tenant,id);i.resolve(Instant.now());return i;}
}
