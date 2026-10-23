package com.ruoyi.vlstream.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Keeps the legacy owner playback permission and tenant check on shared preview entry points. */
@Service
public class VlStreamDeviceOwnerPlaybackGuard {
    private final VlStreamDeviceTenantService tenants;

    public VlStreamDeviceOwnerPlaybackGuard(VlStreamDeviceTenantService tenants) {
        this.tenants = tenants;
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:play')")
    public void requireOwnerPlayback(Long deviceRowId) {
        tenants.requireDevice(deviceRowId);
    }
}
