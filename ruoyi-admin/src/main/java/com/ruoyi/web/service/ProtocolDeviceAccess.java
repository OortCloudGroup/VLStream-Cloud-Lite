package com.ruoyi.web.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.VlStreamTenantContext;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.service.IRtspDeviceService;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.service.IOnvifDeviceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Saved protocol sources use authenticated tenant ownership and the existing department AOP. */
@Service
public class ProtocolDeviceAccess {
    private final IRtspDeviceService rtsp;
    private final IOnvifDeviceService onvif;
    private final String tenant;
    public ProtocolDeviceAccess(IRtspDeviceService rtsp, IOnvifDeviceService onvif,
            @Value("${vlstream.device.default-tenant-id:000000}") String tenant) {
        this.rtsp = rtsp; this.onvif = onvif; this.tenant = tenant;
    }
    private boolean owns(String rowTenant, String currentTenant) {
        String owner = rowTenant == null || rowTenant.isEmpty() ? tenant : rowTenant;
        return owner.equals(currentTenant);
    }
    public RtspDevice rtsp(Long id) {
        String currentTenant = VlStreamTenantContext.currentTenant(tenant);
        RtspDevice query = new RtspDevice(); query.setId(id);
        return rtsp.selectRtspDeviceList(query).stream().filter(row -> id.equals(row.getId()) && owns(row.getTenantId(), currentTenant)).findFirst()
            .orElseThrow(() -> new ServiceException("设备不存在或无权访问", 403));
    }
    public OnvifDevice onvif(Long id) {
        String currentTenant = VlStreamTenantContext.currentTenant(tenant);
        OnvifDevice query = new OnvifDevice(); query.setId(id);
        return onvif.selectOnvifDeviceList(query).stream().filter(row -> id.equals(row.getId()) && owns(row.getTenantId(), currentTenant)).findFirst()
            .orElseThrow(() -> new ServiceException("设备不存在或无权访问", 403));
    }
}
