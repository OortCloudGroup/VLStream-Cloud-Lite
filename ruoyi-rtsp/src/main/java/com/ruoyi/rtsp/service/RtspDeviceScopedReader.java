package com.ruoyi.rtsp.service;

import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.utils.VlStreamTenantContext;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.mapper.RtspDeviceMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Tenant ownership and the existing department scope apply to every device read. */
@Service
public class RtspDeviceScopedReader {
    private final RtspDeviceMapper mapper;
    private final String defaultTenant;
    public RtspDeviceScopedReader(RtspDeviceMapper mapper,
            @Value("${vlstream.device.default-tenant-id:000000}") String defaultTenant) {
        this.mapper = mapper;
        this.defaultTenant = defaultTenant;
    }
    public String currentTenant() { return VlStreamTenantContext.currentTenant(defaultTenant); }
    public void scope(BaseEntity query) {
        query.getParams().put("tenantId", currentTenant());
        query.getParams().put("defaultTenantId", defaultTenant);
    }
    @DataScope(deptAlias = "d", permission = "rtsp:RtspDevice:list")
    public List<RtspDevice> list(RtspDevice query, boolean map) { return select(query, map); }
    @DataScope(deptAlias = "d")
    public List<RtspDevice> forOperation(RtspDevice query, boolean map) { return select(query, map); }
    private List<RtspDevice> select(RtspDevice query, boolean map) {
        scope(query);
        return map ? mapper.selectRtspDeviceListMap(query) : mapper.selectRtspDeviceList(query);
    }
}
