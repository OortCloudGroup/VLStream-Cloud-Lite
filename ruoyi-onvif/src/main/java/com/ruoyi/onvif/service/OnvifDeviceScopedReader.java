package com.ruoyi.onvif.service;

import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.utils.VlStreamTenantContext;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.mapper.OnvifDeviceMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Tenant ownership and the existing department scope apply to every device read. */
@Service
public class OnvifDeviceScopedReader {
    private final OnvifDeviceMapper mapper;
    private final String defaultTenant;
    public OnvifDeviceScopedReader(OnvifDeviceMapper mapper,
            @Value("${vlstream.device.default-tenant-id:000000}") String defaultTenant) {
        this.mapper = mapper;
        this.defaultTenant = defaultTenant;
    }
    public String currentTenant() { return VlStreamTenantContext.currentTenant(defaultTenant); }
    public void scope(BaseEntity query) {
        query.getParams().put("tenantId", currentTenant());
        query.getParams().put("defaultTenantId", defaultTenant);
    }
    @DataScope(deptAlias = "d", permission = "onvif:device:list")
    public List<OnvifDevice> list(OnvifDevice query, boolean map) { return select(query, map); }
    @DataScope(deptAlias = "d")
    public List<OnvifDevice> forOperation(OnvifDevice query, boolean map) { return select(query, map); }
    private List<OnvifDevice> select(OnvifDevice query, boolean map) {
        scope(query);
        return map ? mapper.selectOnvifDeviceListMap(query) : mapper.selectOnvifDeviceList(query);
    }
}
