package com.ruoyi.custom.availability;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.deviceavailability.AvailabilitySnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Source revision keys prevent old workers or other protocols from publishing this source's state. */
@Component
public class CustomAvailabilityCache {
    private final RedisCache redis;private final String prefix;private final long stale;private final int failures,recovery;
    @org.springframework.beans.factory.annotation.Autowired
    public CustomAvailabilityCache(RedisCache redis,@Value("${vlstream.device.default-tenant-id:000000}") String tenant,
        @Value("${media.id:default}") String mediaNamespace,
        @Value("${video.custom-availability.camera-rtc-signal-url:}") String gatewayNamespace,
        @Value("${video.custom-availability.allowed-private-networks:10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7}") String policyNamespace,
        @Value("${video.availability.stale-seconds:90}") long staleSeconds,@Value("${video.availability.failure-threshold:3}") int failures,
        @Value("${video.availability.recovery-threshold:2}") int recovery) {
        if(!tenant.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid availability scope");
        this.redis=redis;prefix="video:custom-availability:v3:"+tenant+":"+Base64.getUrlEncoder().withoutPadding().encodeToString((mediaNamespace+"\n"+gatewayNamespace+"\n"+policyNamespace+"\n"+staleSeconds+":"+failures+":"+recovery).getBytes(java.nio.charset.StandardCharsets.UTF_8))+":";stale=Math.min(600,Math.max(30,staleSeconds))*1000L;
        this.failures=Math.max(1,failures);this.recovery=Math.max(1,recovery);
    }
    public CustomAvailabilityCache(RedisCache redis,String tenant,String media,String gateway,long stale,int failures,int recovery){this(redis,tenant,media,gateway,"10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7",stale,failures,recovery);}
    public CustomAvailabilityCache(RedisCache redis,String tenant,long staleSeconds,int failures,int recovery){this(redis,tenant,"default","",staleSeconds,failures,recovery);}
    String key(CustomStreamSpec source){return prefix+source.device.getId()+":"+source.fingerprint;}
    private Evidence raw(CustomStreamSpec source) {String value=redis.getCacheObject(key(source));return value==null?new Evidence():JSON.parseObject(value,Evidence.class);}
    public Evidence get(CustomStreamSpec source) {
        try {Evidence evidence=raw(source);long now=System.currentTimeMillis();
            long stamp="ONLINE".equals(evidence.getSnapshot().getState())?evidence.getSnapshot().getLastAvailableAt():evidence.getSnapshot().getCheckedAt();
            if(stamp<=0||stamp>now||now-stamp>=stale){evidence.getSnapshot().setState("UNKNOWN");if(stamp>0)evidence.getSnapshot().setReason("STALE");}
            evidence.setExpiresAt(stamp<=0?0:stamp+stale);evidence.setSourceRevision(source.fingerprint);return evidence;
        } catch(RuntimeException unavailable){Evidence result=new Evidence();result.getSnapshot().setReason("MONITOR_UNAVAILABLE");return result;}
    }
    public Evidence record(CustomStreamSpec source,CustomStreamProbe.Result result) {
        Evidence previous=raw(source);long now=System.currentTimeMillis();
        long priorStamp="ONLINE".equals(previous.getSnapshot().getState())?previous.getSnapshot().getLastAvailableAt():previous.getSnapshot().getCheckedAt();
        if(priorStamp>now||priorStamp>0&&now-priorStamp>=stale){previous.getSnapshot().setState("UNKNOWN");previous.getSnapshot().setFailureCount(0);previous.getSnapshot().setSuccessCount(0);}
        String transition=result.reason;
        if(transition.startsWith("UNSUPPORTED")||Arrays.asList("AUTHENTICATION_UNSUPPORTED","ADDRESS_BLOCKED","MONITOR_BUSY","MONITOR_UNAVAILABLE","CONFIGURATION_ERROR","TLS_VALIDATION_FAILED").contains(transition))transition="UNSUPPORTED";
        Evidence next=new Evidence();next.setSource(result.source);next.setSourceRevision(source.fingerprint);
        next.setSnapshot(AvailabilitySnapshot.transition(previous.getSnapshot(),transition,now,failures,recovery));next.getSnapshot().setReason(result.reason);
        // Two increasing server-side video-frame samples are stronger than a protocol advertisement.
        if(result.receiving){next.getSnapshot().setState("ONLINE");next.getSnapshot().setSuccessCount(recovery);}
        long stamp="ONLINE".equals(next.getSnapshot().getState())?next.getSnapshot().getLastAvailableAt():now;next.setExpiresAt(stamp+stale);
        redis.setCacheObject(key(source),JSON.toJSONString(next),600,TimeUnit.SECONDS);return next;
    }
    public String claim(CustomStreamSpec source) {
        String owner=UUID.randomUUID().toString();return Boolean.TRUE.equals(redis.redisTemplate.opsForValue().setIfAbsent(key(source)+":probe",owner,20,TimeUnit.SECONDS))?owner:null;
    }
    public void release(CustomStreamSpec source,String owner) {
        if(owner==null)return;
        redis.redisTemplate.execute(new DefaultRedisScript<Long>("if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",Long.class),Collections.singletonList(key(source)+":probe"),owner);
    }
    public void invalidate(CustomStreamSpec source){redis.deleteObject(key(source));}
    public static class Evidence {
        private AvailabilitySnapshot snapshot=new AvailabilitySnapshot();private String source="CUSTOM_STREAM_PROBE",sourceRevision;private long expiresAt;
        public String getSourceRevision(){return sourceRevision;}public void setSourceRevision(String value){sourceRevision=value;}
        public AvailabilitySnapshot getSnapshot(){return snapshot;} public void setSnapshot(AvailabilitySnapshot value){snapshot=value;}
        public String getSource(){return source;} public void setSource(String value){source=value;}
        public long getExpiresAt(){return expiresAt;} public void setExpiresAt(long value){expiresAt=value;}
    }
}
