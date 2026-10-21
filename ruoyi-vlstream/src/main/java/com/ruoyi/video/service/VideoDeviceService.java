package com.ruoyi.video.service;

import com.ruoyi.common.utils.VlStreamTenantContext;
import com.ruoyi.video.config.VideoDeviceIdentityProperties;
import com.ruoyi.video.domain.VideoDeviceQuery;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@Service
public class VideoDeviceService {
    static final Map<String, String> PERMISSIONS = new LinkedHashMap<>();
    static {
        PERMISSIONS.put("VLSTREAM", "vlstream:device:list");
        PERMISSIONS.put("ISUP", "isup:lsupDevice:list");
        PERMISSIONS.put("EHOME", "ehome:device:list");
        PERMISSIONS.put("RTSP", "rtsp:RtspDevice:list");
        PERMISSIONS.put("ONVIF", "onvif:device:list");
        PERMISSIONS.put("GB28181", "wvp:device:list");
        PERMISSIONS.put("DAHUA", "dahua:device:list");
        PERMISSIONS.put("CUSTOM", "custom:device:list");
    }
    public static final String LIST_PERMISSIONS = String.join(",", PERMISSIONS.values());
    private final VideoDeviceScopedReader reader;
    private final VlStreamDeviceTenantService tenants;
    private final VideoDeviceIdentityProperties identities;
    // Existing protocol ownership uses this configured legacy fallback, independently
    // of the MQTT/custom default selected by single/multi business mode.
    @org.springframework.beans.factory.annotation.Value("${vlstream.device.default-tenant-id:000000}")
    private String protocolDefaultTenant = "000000";

    public VideoDeviceService(VideoDeviceScopedReader reader, VlStreamDeviceTenantService tenants,
                              VideoDeviceIdentityProperties identities) {
        this.reader = reader;
        this.tenants = tenants;
        this.identities = identities;
    }

    public Map<String, Object> list(int pageNum, int pageSize, String keyword) {
        String defaultTenant = tenants.defaultTenant();
        return list(pageNum, pageSize, keyword, VlStreamTenantContext.currentTenant(defaultTenant),
            defaultTenant);
    }

    Map<String, Object> list(int pageNum, int pageSize, String keyword, String tenant, String defaultTenant) {
        List<VideoDeviceSource> sources = new ArrayList<>();
        List<String> protocols = new ArrayList<>();
        for (Map.Entry<String, String> entry : PERMISSIONS.entrySet()) {
            String protocol = entry.getKey();
            // RTSP and ONVIF now carry tenant ownership, just like VLSTREAM.
            // Only directories without ownership remain exclusive to the default tenant.
            boolean tenantOwned = "VLSTREAM".equals(protocol) || "RTSP".equals(protocol) || "ONVIF".equals(protocol);
            if (!tenantOwned && !tenant.equals(defaultTenant)) continue;
            VideoDeviceQuery query = new VideoDeviceQuery();
            query.setProtocol(protocol);
            query.setTenantId(tenant);
            query.setDefaultTenantId("RTSP".equals(protocol) || "ONVIF".equals(protocol)
                ? protocolDefaultTenant : defaultTenant);
            try {
                // Each reader is secured by the same @ss.hasPermi expression as its original API.
                // In particular, federated protocol access is not a static permission-set lookup.
                sources.addAll(read(protocol, query));
                protocols.add(protocol);
            } catch (AccessDeniedException denied) {
                // A user may have access to only a subset of the protocol directories.
            }
        }
        Map<String, Object> result = VideoDeviceCatalog.page(
            VideoDeviceCatalog.consolidate(sources, identities.sourceIdentities()), pageNum, pageSize, keyword);
        result.put("protocols", protocols);
        return result;
    }

    private List<VideoDeviceSource> read(String protocol, VideoDeviceQuery query) {
        switch (protocol) {
            case "VLSTREAM": return reader.vlstream(query);
            case "ISUP": return reader.isup(query);
            case "EHOME": return reader.ehome(query);
            case "RTSP": return reader.rtsp(query);
            case "ONVIF": return reader.onvif(query);
            case "GB28181": return reader.gb(query);
            case "DAHUA": return reader.dahua(query);
            case "CUSTOM": return reader.custom(query);
            default: throw new IllegalArgumentException("Unsupported video protocol");
        }
    }
}

