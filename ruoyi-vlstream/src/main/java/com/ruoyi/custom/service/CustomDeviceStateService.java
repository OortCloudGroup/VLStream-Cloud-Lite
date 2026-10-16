package com.ruoyi.custom.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.VlStreamTenantContext;
import com.ruoyi.custom.domain.*;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/** One status interpretation for management, playback and each custom catalog source.
 * CameraRTC's exact saved videocall target grants only its scoped status projection;
 * matching display names/codes/IPs never link identities or authorize other device data.
 */
@Service
public class CustomDeviceStateService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ruoyi.custom.availability.CustomAvailabilityMonitor availabilityMonitor;
    private final CustomDeviceMapper devices;
    private final CustomTargetStateReader states;
    private final VlStreamDeviceProperties properties;
    private final VlStreamDeviceTenantService tenants;
    public CustomDeviceStateService(CustomDeviceMapper devices, CustomTargetStateReader states,
            VlStreamDeviceProperties properties, VlStreamDeviceTenantService tenants) {
        this.devices = devices; this.states = states; this.properties = properties; this.tenants = tenants;
    }
    private String requireTenant() {
        String tenant = VlStreamTenantContext.currentTenant(tenants.defaultTenant());
        if (!tenants.defaultTenant().equals(tenant)) throw new ServiceException("设备不存在或不属于当前租户", 403);
        return tenant;
    }
    public CustomDeviceState resolve(CustomDevice device) {
        String tenant = requireTenant();
        CustomDeviceState result = new CustomDeviceState(); result.setCheckedAt(System.currentTimeMillis()); result.setObservedAt(result.getCheckedAt());
        result.setState("UNKNOWN"); result.setSource("UNVERIFIED"); result.setReason("SOURCE_UNVERIFIED");
        String target = cameraTarget(device.getStreamUrl());
        if (target == null) {
            if(availabilityMonitor==null)return result;
            try{return fromEvidence(availabilityMonitor.cached(device));}
            catch(com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure failure){result.setReason(failure.reason);return result;}
        }
        if(availabilityMonitor!=null)result=fromEvidence(availabilityMonitor.cached(device));
        result.setTargetDeviceId(target);result.setDeviceState("UNKNOWN");
        CustomTargetHeartbeat heartbeat;
        try { heartbeat = states.read(target, tenant, tenants.defaultTenant()); }
        catch (org.springframework.security.access.AccessDeniedException denied) {
            result.setDeviceReason("TARGET_STATUS_FORBIDDEN"); return result;
        }
        if (heartbeat == null) { result.setDeviceReason("NO_AUTHORIZED_HEARTBEAT"); return result; }
        result.setLastHeartbeatTime(heartbeat.getLastHeartbeatTime()); result.setSourceUpdateTime(heartbeat.getUpdateTime());
        if (Boolean.FALSE.equals(heartbeat.getOnline())) {
            long stamp=heartbeat.getUpdateTime()==null?0:heartbeat.getUpdateTime().getTime();
            long expires=stamp+Math.max(1,properties.getOfflineTimeoutSeconds())*1000L;
            if(stamp<=0||expires<=result.getObservedAt())result.setDeviceReason("STALE_HEARTBEAT");
            else if(stamp>result.getObservedAt())result.setDeviceReason("FUTURE_HEARTBEAT");
            else {result.setDeviceState("OFFLINE"); result.setDeviceReason(heartbeat.getOnlineReason() == null ? "DEVICE_OFFLINE" : heartbeat.getOnlineReason());}
        } else if (Boolean.TRUE.equals(heartbeat.getOnline()) && heartbeat.getLastHeartbeatTime() != null) {
            long seen = heartbeat.getLastHeartbeatTime().getTime();
            long expires = seen + Math.max(1, properties.getOfflineTimeoutSeconds()) * 1000L;
            if (seen > result.getObservedAt()) result.setDeviceReason("FUTURE_HEARTBEAT");
            else if (expires <= result.getObservedAt()) result.setDeviceReason("STALE_HEARTBEAT");
            else { result.setDeviceState("ONLINE"); result.setDeviceReason("FRESH_HEARTBEAT"); }
        } else result.setDeviceReason("STATE_UNREPORTED");
        return result;
    }
    static String cameraTarget(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null || uri.getHost() == null || uri.getFragment()!=null) return null;
            String[] parts = uri.getRawPath().split("/");
            for (int i = 0; i + 1 < parts.length; i++) if ("videocall".equalsIgnoreCase(parts[i]))
                return parts[i + 1].matches("[A-Za-z0-9_-]{1,100}") ? parts[i + 1] : null;
        } catch (RuntimeException ignored) { }
        return null;
    }
    public CustomDevice requireDevice(Long id) {
        requireTenant(); CustomDevice device = devices.selectDeviceById(id);
        if (device == null) throw new ServiceException("设备不存在或无权访问", 403);
        return enrich(device);
    }
    public CustomDevice enrich(CustomDevice device) {
        CustomDeviceState state = resolve(device);
        if (device.getConfiguredStatus() == null) device.setConfiguredStatus(device.getStatus());
        device.setStatus(state.getState()); device.setAvailability(state);
        return device;
    }
    public List<CustomDevice> list(CustomDevice query) {
        requireTenant(); String filter = query.getStatus(); List<CustomDevice> result;
        query.setStatus(null);
        try { result = devices.selectDeviceList(query); } finally { query.setStatus(filter); }
        return result.stream().map(this::enrich).filter(row -> filter == null || filter.trim().isEmpty() ||
            row.getStatus().equalsIgnoreCase(filter.trim())).collect(Collectors.toList());
    }
    public List<VideoDeviceSource> enrichSources(List<VideoDeviceSource> sources) {
        requireTenant();
        for (VideoDeviceSource source : sources) {
            CustomDevice device = devices.selectDeviceById(Long.valueOf(source.getRowId()));
            CustomDeviceState state = device == null ? null : resolve(device);
            source.setConfiguredState(source.getState());
            source.setState(state == null ? "UNKNOWN" : state.getState()); source.setAvailability(state);
        }
        return sources;
    }
    public void requirePlayable(CustomDevice device) {
        requireTenant();CustomDeviceState state;
        try {
            com.ruoyi.custom.availability.CustomStreamSpec spec=new com.ruoyi.custom.availability.CustomStreamSpec(device);
            state=availabilityMonitor!=null?fromEvidence(availabilityMonitor.preflight(device)):resolve(device);
        }catch(com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure failure){throw new ServiceException("视频源地址安全校验未通过："+failure.reason);}
        if(Arrays.asList("ADDRESS_BLOCKED","CONFIGURATION_ERROR").contains(state.getReason()))throw new ServiceException("视频源地址安全校验未通过");
        if("OFFLINE".equals(state.getState()))throw new ServiceException("视频源连接失败，请稍后重试");
        if("AUTH_ERROR".equals(state.getDetailState()))throw new ServiceException("视频源认证失败，请检查视频流凭据");
        if("STREAM_ERROR".equals(state.getDetailState()))throw new ServiceException("视频源未提供有效视频，请稍后重试");
    }
    public void requireScope(){requireTenant();}
    public String validatedProxyUrl(CustomDevice device){requireTenant();try{return availabilityMonitor==null?device.getStreamUrl():availabilityMonitor.proxyUrl(device);}catch(com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure failure){throw new ServiceException("视频源地址安全校验未通过："+failure.reason);}}
    public void validateDirect(CustomDevice device){requireTenant();if(availabilityMonitor!=null)try{availabilityMonitor.validateDirect(device);}catch(com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure failure){throw new ServiceException("视频源地址安全校验未通过："+failure.reason);}}
    public void invalidate(CustomDevice device){if(availabilityMonitor!=null)availabilityMonitor.invalidate(device);}
    private CustomDeviceState fromEvidence(com.ruoyi.custom.availability.CustomAvailabilityCache.Evidence evidence) {
        com.ruoyi.common.deviceavailability.AvailabilitySnapshot snapshot=evidence.getSnapshot();CustomDeviceState result=new CustomDeviceState();
        result.setState("ONLINE".equals(snapshot.getState())?"ONLINE":"OFFLINE".equals(snapshot.getState())?"OFFLINE":"UNKNOWN");
        result.setDetailState(snapshot.getState());result.setSource(evidence.getSource());result.setReason(snapshot.getReason());result.setSourceRevision(evidence.getSourceRevision());
        result.setCheckedAt(snapshot.getCheckedAt());result.setObservedAt(System.currentTimeMillis());
        result.setExpiresAt(evidence.getExpiresAt()>0?evidence.getExpiresAt():null);result.setLastAvailableAt(snapshot.getLastAvailableAt());
        result.setFailureCount(snapshot.getFailureCount());result.setSuccessCount(snapshot.getSuccessCount());return result;
    }
}
