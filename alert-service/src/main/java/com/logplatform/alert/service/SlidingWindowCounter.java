package com.logplatform.alert.service;
import org.springframework.data.redis.core.StringRedisTemplate; import org.springframework.data.redis.core.script.DefaultRedisScript; import org.springframework.stereotype.Component; import java.time.Instant; import java.util.List;
@Component public class SlidingWindowCounter {
 private final StringRedisTemplate redis; private final DefaultRedisScript<Long> script;
 public SlidingWindowCounter(StringRedisTemplate redis){this.redis=redis;this.script=new DefaultRedisScript<>();this.script.setResultType(Long.class);this.script.setScriptText("local k=KEYS[1]; local now=tonumber(ARGV[1]); local cutoff=now-tonumber(ARGV[2]); redis.call('ZREMRANGEBYSCORE',k,'-inf',cutoff); redis.call('ZADD',k,now,ARGV[3]); redis.call('PEXPIRE',k,tonumber(ARGV[2])*1000+5000); return redis.call('ZCARD',k)");}
 public long addAndCount(String key,String eventId,Instant at,int windowSeconds){Long n=redis.execute(script,List.of(key),String.valueOf(at.toEpochMilli()),String.valueOf(windowSeconds*1000L),eventId);return n==null?0:n;}
}
