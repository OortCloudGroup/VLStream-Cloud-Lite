package com.ruoyi.vlstream.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamEventLocationMapper;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.junit.Before;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class VlStreamEventLocationControllerTest {
    private final VlStreamEventLocationMapper mapper = mock(VlStreamEventLocationMapper.class);
    private final VlStreamDeviceProperties properties = new VlStreamDeviceProperties();
    private final VlStreamDeviceTenantService tenants = mock(VlStreamDeviceTenantService.class);
    private final VlStreamEventLocationController controller = new VlStreamEventLocationController(mapper, properties, tenants);
    @Before public void trustedTenant() { when(tenants.currentTenantId()).thenReturn("tenant-a"); }

    private Map<String, Object> request(String tenant, List<String> ids) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenant); body.put("deviceIds", ids);
        return body;
    }
    private VlStreamDevice device(String id, String tenant, long age) {
        VlStreamDevice device = new VlStreamDevice();
        device.setId(7L); device.setDeviceId(id); device.setTenantId(tenant); device.setOnline(true);
        device.setLongitude(BigDecimal.ZERO); device.setLatitude(BigDecimal.ZERO);
        device.setLastHeartbeatTime(new Date(System.currentTimeMillis() - age));
        return device;
    }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> data(AjaxResult result) {
        assertEquals(200, result.get("code"));
        return (List<Map<String, Object>>) result.get("data");
    }

    @Test public void batchUsesBusinessIdsTenantAndOneMapperCallAndKeepsZero() {
        VlStreamDevice device = device("CAM-1", "tenant-a", 1000);
        when(mapper.selectLocations(eq("tenant-a"), eq("000000"), eq(Arrays.asList("CAM-1", "CAM-2"))))
            .thenReturn(Collections.singletonList(device));
        List<Map<String, Object>> rows = data(controller.locations(request("tenant-a", Arrays.asList("CAM-1", "CAM-1", "CAM-2"))));
        assertEquals(1, rows.size());
        assertEquals("CAM-1", rows.get(0).get("deviceId"));
        assertEquals("FRESH", rows.get(0).get("status"));
        assertEquals(BigDecimal.ZERO, rows.get(0).get("longitude"));
        assertFalse(rows.get(0).containsKey("id"));
        verify(mapper, times(1)).selectLocations(anyString(), anyString(), anyList());
    }
    @Test public void staleAndOfflineKeepKnownReferenceButFutureMissingAndInvalidDoNotExposeCoordinates() {
        VlStreamDevice stale = device("A", "tenant-a", 181000), offline = device("B", "tenant-a", 1000),
            future = device("C", "tenant-a", -60000), missing = device("D", "tenant-a", 1000),
            invalid = device("E", "tenant-a", 1000), noTime = device("F", "tenant-a", 1000);
        offline.setOnline(false); missing.setLatitude(null); invalid.setLongitude(new BigDecimal("181")); noTime.setLastHeartbeatTime(null);
        when(mapper.selectLocations(anyString(), anyString(), anyList())).thenReturn(Arrays.asList(stale, offline, future, missing, invalid, noTime));
        List<Map<String, Object>> rows = data(controller.locations(request("tenant-a", Arrays.asList("A", "B", "C", "D", "E", "F"))));
        assertTrue(rows.get(0).containsKey("longitude"));
        assertTrue(rows.get(1).containsKey("longitude"));
        assertEquals("STALE", rows.get(1).get("status"));
        for (Map<String, Object> row : rows.subList(2, rows.size())) assertFalse(row.containsKey("longitude"));
        assertEquals("STALE", rows.get(0).get("status"));
        assertEquals("MISSING", rows.get(3).get("status"));
    }
    @Test public void foreignTenantAndUnrequestedBusinessIdAreFilteredAgain() {
        when(mapper.selectLocations(anyString(), anyString(), anyList())).thenReturn(Arrays.asList(
            device("CAM-1", "tenant-b", 1000), device("7", "tenant-a", 1000)));
        assertTrue(data(controller.locations(request("tenant-a", Collections.singletonList("CAM-1")))).isEmpty());
    }
    @Test public void nullLegacyTenantUsesSameConfiguredDefault() {
        properties.setTenantType("multi");
        String tenant = properties.getMultiTenantDefaultTenantId();
        when(tenants.currentTenantId()).thenReturn(tenant);
        when(mapper.selectLocations(eq(tenant), eq(tenant), anyList())).thenReturn(Collections.singletonList(device("CAM-1", null, 1000)));
        assertEquals(tenant, data(controller.locations(request(tenant, Collections.singletonList("CAM-1")))).get(0).get("tenantId"));
    }
    @Test public void rejectsEmptyOversizeAndMalformedRequestsBeforeQuery() {
        assertEquals(400, controller.locations(request("", Collections.singletonList("A"))).get("code"));
        assertEquals(400, controller.locations(request("tenant-a", Collections.emptyList())).get("code"));
        assertEquals(400, controller.locations(request("tenant-a", Collections.nCopies(101, "A"))).get("code"));
        assertEquals(400, controller.locations(request("tenant-a", Collections.singletonList(""))).get("code"));
        verifyNoInteractions(mapper);
    }

    @Test public void rejectsClientTenantThatDiffersFromVerifiedSession() {
        assertEquals(403, controller.locations(request("tenant-b", Collections.singletonList("CAM-1"))).get("code"));
        verifyNoInteractions(mapper);
    }

    @Test public void endpointRequiresExistingDeviceListPermissionAndIsNotAnonymous() throws Exception {
        java.lang.reflect.Method method = VlStreamEventLocationController.class.getMethod("locations", Map.class);
        assertEquals("@ss.hasPermi('vlstream:device:list')", method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());
        assertNull(method.getAnnotation(com.ruoyi.common.annotation.Anonymous.class));
    }
}
