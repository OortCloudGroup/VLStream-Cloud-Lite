package com.ruoyi.video.service;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.video.domain.VideoDeviceQuery;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Authenticated catalog containing only cross-tenant VLStream sharing metadata. */
@Service
public class VideoDeviceSharingCatalogService {
    private final VideoDeviceScopedReader reader;
    private final VlStreamDeviceTenantService tenants;

    public VideoDeviceSharingCatalogService(VideoDeviceScopedReader reader, VlStreamDeviceTenantService tenants) {
        this.reader = reader;
        this.tenants = tenants;
    }

    public Map<String, Object> list(int pageNum, int pageSize, String keyword) {
        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setProtocol("VLSTREAM");
        query.setTenantId(tenants.currentTenantId());
        query.setDefaultTenantId(tenants.defaultTenant());
        query.setViewerUserId(SecurityUtils.getUserId());
        query.setKeyword(keyword);
        List<VideoDeviceSource> sources = reader.shareableVlstream(query);
        Map<String, Object> result = VideoDeviceCatalog.page(
            VideoDeviceCatalog.consolidate(sources), pageNum, pageSize, keyword);
        result.put("protocols", sources.isEmpty() ? Collections.emptyList() : Collections.singletonList("VLSTREAM"));
        return result;
    }
}
