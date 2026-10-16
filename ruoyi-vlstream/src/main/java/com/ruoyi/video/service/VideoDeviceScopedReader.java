package com.ruoyi.video.service;

import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.video.domain.VideoDeviceQuery;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.video.mapper.VideoDeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

/** Separate bean so each protocol's existing role data scope passes through AOP. */
@Service
public class VideoDeviceScopedReader {
    private final VideoDeviceMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.ruoyi.common.deviceavailability.ProtocolAvailabilityService availability;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.ruoyi.custom.service.CustomDeviceStateService customStates;

    private List<VideoDeviceSource> availability(List<VideoDeviceSource> sources) {
        if (availability == null) return sources;
        for (VideoDeviceSource source : sources) {
            String state = availability.get(source.getProtocol(), source.getRowId()).getState();
            source.setState("ONLINE".equals(state) || "OFFLINE".equals(state) ? state : "UNKNOWN");
        }
        return sources;
    }


    public VideoDeviceScopedReader(VideoDeviceMapper mapper) { this.mapper = mapper; }

    @PreAuthorize("@ss.hasPermi('vlstream:device:list')")
    public List<VideoDeviceSource> vlstream(VideoDeviceQuery query) { return mapper.selectSources(query); }

    @PreAuthorize("@ss.hasPermi('custom:device:list')")
    public List<VideoDeviceSource> custom(VideoDeviceQuery query) {
        List<VideoDeviceSource> sources = mapper.selectSources(query);
        if (customStates != null) return customStates.enrichSources(sources);
        for (VideoDeviceSource source : sources) { source.setConfiguredState(source.getState()); source.setState("UNKNOWN"); }
        return sources;
    }

    @PreAuthorize("@ss.hasPermi('isup:lsupDevice:list')")
    @DataScope(deptAlias = "d", permission = "isup:lsupDevice:list")
    public List<VideoDeviceSource> isup(VideoDeviceQuery query) { return mapper.selectSources(query); }

    @PreAuthorize("@ss.hasPermi('ehome:device:list')")
    @DataScope(deptAlias = "d", permission = "ehome:device:list")
    public List<VideoDeviceSource> ehome(VideoDeviceQuery query) { return mapper.selectSources(query); }

    @PreAuthorize("@ss.hasPermi('rtsp:RtspDevice:list')")
    @DataScope(deptAlias = "d", permission = "rtsp:RtspDevice:list")
    public List<VideoDeviceSource> rtsp(VideoDeviceQuery query) { return availability(mapper.selectSources(query)); }

    @PreAuthorize("@ss.hasPermi('onvif:device:list')")
    @DataScope(deptAlias = "d", permission = "onvif:device:list")
    public List<VideoDeviceSource> onvif(VideoDeviceQuery query) { return availability(mapper.selectSources(query)); }

    @PreAuthorize("@ss.hasPermi('wvp:device:list')")
    @DataScope(deptAlias = "d", permission = "wvp:device:list")
    public List<VideoDeviceSource> gb(VideoDeviceQuery query) { return mapper.selectSources(query); }

    @PreAuthorize("@ss.hasPermi('dahua:device:list')")
    @DataScope(deptAlias = "d", permission = "dahua:device:list")
    public List<VideoDeviceSource> dahua(VideoDeviceQuery query) { return mapper.selectSources(query); }
}

