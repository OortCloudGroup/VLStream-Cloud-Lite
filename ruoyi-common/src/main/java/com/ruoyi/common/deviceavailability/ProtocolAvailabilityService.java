package com.ruoyi.common.deviceavailability;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.redis.RedisCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/** Shared by protocol lists and authorized catalog readers. Keys contain no camera material. */
@Service
public class ProtocolAvailabilityService {
    private final RedisCache redis;
    private final String prefix;
    private final long staleMillis;
    private final int failures;
    private final int successes;

    public ProtocolAvailabilityService(RedisCache redis,
            @Value("${vlstream.device.default-tenant-id:000000}") String tenant,
            @Value("${video.availability.stale-seconds:90}") long staleSeconds,
            @Value("${video.availability.failure-threshold:3}") int failures,
            @Value("${video.availability.recovery-threshold:2}") int successes) {
        this.redis = redis;
        this.prefix = "video:availability:v1:" + tenant + ":";
        this.staleMillis = Math.max(30, staleSeconds) * 1000;
        this.failures = Math.max(1, failures);
        this.successes = Math.max(1, successes);
    }

    public AvailabilitySnapshot get(String protocol, String rowId) {
        AvailabilitySnapshot snapshot = raw(protocol, rowId);
        long maxAge = "ONLINE".equals(snapshot.getState()) ? staleMillis : Math.max(staleMillis, 600_000);
        if (snapshot.getCheckedAt() > 0 && System.currentTimeMillis() - snapshot.getCheckedAt() > maxAge) {
            snapshot.setState("UNKNOWN");
            snapshot.setReason("STALE");
        }
        return snapshot;
    }

    private AvailabilitySnapshot raw(String protocol, String rowId) {
        try {
            String json = redis.getCacheObject(key(protocol, rowId));
            return json == null ? new AvailabilitySnapshot() : JSON.parseObject(json, AvailabilitySnapshot.class);
        } catch (RuntimeException unavailable) {
            AvailabilitySnapshot result = new AvailabilitySnapshot();
            result.setReason("MONITOR_UNAVAILABLE");
            return result;
        }
    }

    public AvailabilitySnapshot record(String protocol, String rowId, String result) {
        AvailabilitySnapshot next = AvailabilitySnapshot.transition(raw(protocol, rowId), result,
                System.currentTimeMillis(), failures, successes);
        redis.setCacheObject(key(protocol, rowId), JSON.toJSONString(next), 3600, TimeUnit.SECONDS);
        return next;
    }

    public void invalidate(String protocol, String rowId) { redis.deleteObject(key(protocol, rowId)); }

    /** Lease expires naturally: a late worker never deletes another instance's lease. */
    public boolean claim(String protocol, String rowId) {
        return Boolean.TRUE.equals(redis.redisTemplate.opsForValue().setIfAbsent(
                key(protocol, rowId) + ":probe", "1", 20, TimeUnit.SECONDS));
    }

    private String key(String protocol, String rowId) {
        if (!("RTSP".equals(protocol) || "ONVIF".equals(protocol)) || rowId == null || !rowId.matches("[0-9]+"))
            throw new IllegalArgumentException("Invalid protocol device key");
        return prefix + protocol + ":" + rowId;
    }
}
