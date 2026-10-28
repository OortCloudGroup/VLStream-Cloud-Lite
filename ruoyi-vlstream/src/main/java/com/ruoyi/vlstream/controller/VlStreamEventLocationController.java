package com.ruoyi.vlstream.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamEventLocationMapper;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Authenticated batch projection using the existing WVP device-list permission and trusted tenant. */
@RestController
public class VlStreamEventLocationController {
    private final VlStreamEventLocationMapper mapper;
    private final VlStreamDeviceProperties properties;
    private final VlStreamDeviceTenantService tenants;

    public VlStreamEventLocationController(VlStreamEventLocationMapper mapper, VlStreamDeviceProperties properties,
                                           VlStreamDeviceTenantService tenants) {
        this.mapper = mapper;
        this.properties = properties;
        this.tenants = tenants;
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:list')")
    @PostMapping("/internal/vlstream/device/locations")
    public AjaxResult locations(@RequestBody Map<String, Object> body) {
        Object tenant = body.get("tenantId"), raw = body.get("deviceIds");
        if (!(tenant instanceof String) || !((String) tenant).matches("[A-Za-z0-9_-]{1,64}")
            || !(raw instanceof List) || ((List<?>) raw).isEmpty() || ((List<?>) raw).size() > 100) {
            return AjaxResult.error(400, "位置批量查询参数无效");
        }
        if (!tenant.equals(tenants.currentTenantId())) return AjaxResult.error(403, "设备租户不匹配");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Object id : (List<?>) raw) {
            if (!(id instanceof String) || ((String) id).trim().isEmpty() || ((String) id).length() > 128) {
                return AjaxResult.error(400, "设备编号无效");
            }
            ids.add((String) id);
        }
        String defaultTenant = properties.getEffectiveDefaultTenantId();
        long now = System.currentTimeMillis();
        List<Map<String, Object>> result = new ArrayList<>();
        for (VlStreamDevice device : mapper.selectLocations((String) tenant, defaultTenant, new ArrayList<>(ids))) {
            String owner = device.getTenantId();
            if (owner == null || owner.isEmpty()) owner = defaultTenant;
            if (!tenant.equals(owner) || !ids.contains(device.getDeviceId())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", device.getDeviceId());
            row.put("tenantId", owner);
            Date heartbeat = device.getLastHeartbeatTime();
            row.put("heartbeatAt", heartbeat == null ? null : heartbeat.getTime());
            boolean knownTime = heartbeat != null && heartbeat.getTime() > 0 && heartbeat.getTime() <= now;
            boolean fresh = Boolean.TRUE.equals(device.getOnline()) && knownTime
                && now - heartbeat.getTime() <= Math.max(1, properties.getOfflineTimeoutSeconds()) * 1000;
            boolean valid = valid(device.getLongitude(), 180) && valid(device.getLatitude(), 90);
            row.put("status", knownTime && valid ? (fresh ? "FRESH" : "STALE") : "MISSING");
            // Last-known coordinates may be shown only as explicitly stale reference with a known receipt time.
            // They never become a fresh position or a persisted historical event point.
            if (knownTime && valid) {
                row.put("longitude", device.getLongitude());
                row.put("latitude", device.getLatitude());
            }
            result.add(row);
        }
        return AjaxResult.success(result);
    }

    private static boolean valid(BigDecimal value, int limit) {
        return value != null && value.abs().compareTo(BigDecimal.valueOf(limit)) <= 0;
    }
}
