package com.ruoyi.common.deviceavailability;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.redis.RedisCache;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProtocolAvailabilityServiceTest {
    @Test public void staleEvidencePreservesTimestampsAndBecomesUnknown() {
        RedisCache redis = mock(RedisCache.class);
        AvailabilitySnapshot old = new AvailabilitySnapshot();
        old.setState("ONLINE"); old.setCheckedAt(System.currentTimeMillis() - 100_000); old.setLastAvailableAt(123);
        when(redis.getCacheObject(anyString())).thenReturn(JSON.toJSONString(old));
        AvailabilitySnapshot state = new ProtocolAvailabilityService(redis, "tenant-a", 90, 3, 2).get("RTSP", "7");
        assertEquals("UNKNOWN", state.getState()); assertEquals("STALE", state.getReason());
        assertEquals(123, state.getLastAvailableAt());
        verify(redis).getCacheObject("video:availability:v1:tenant-a:RTSP:7");
    }
    @Test public void redisFailureAndBadProtocolNeverFabricateOnline() {
        RedisCache redis = mock(RedisCache.class);
        when(redis.getCacheObject(anyString())).thenThrow(new IllegalStateException("fixture"));
        ProtocolAvailabilityService service = new ProtocolAvailabilityService(redis, "tenant-a", 90, 3, 2);
        assertNull(service.get("ONVIF", "23").getOnline());
        assertEquals("MONITOR_UNAVAILABLE", service.get("ONVIF", "23").getReason());
    }
}
