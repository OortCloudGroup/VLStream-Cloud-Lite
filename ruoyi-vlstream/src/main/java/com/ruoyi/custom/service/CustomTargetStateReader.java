package com.ruoyi.custom.service;

import com.ruoyi.custom.domain.CustomTargetHeartbeat;
import com.ruoyi.custom.mapper.CustomDeviceStateMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Uses the native API permission expression, including federated sessions. */
@Service
public class CustomTargetStateReader {
    private final CustomDeviceStateMapper mapper;
    public CustomTargetStateReader(CustomDeviceStateMapper mapper) { this.mapper = mapper; }
    @PreAuthorize("@ss.hasPermi('vlstream:device:list')")
    public CustomTargetHeartbeat read(String id, String tenant, String defaultTenant) {
        return mapper.selectCameraTarget(id, tenant, defaultTenant);
    }
}
