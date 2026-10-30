package com.ruoyi.web.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.custom.service.CustomDeviceStateService;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.mapper.OnvifDeviceMapper;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.mapper.RtspDeviceMapper;
import com.ruoyi.vlstream.config.VlStreamCameraRtcProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import com.ruoyi.vlstream.util.VlStreamPlaybackUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** Explicit delegation: one verified tenant, actor and saved source; secrets never come from a URL parameter. */
@Service
public class CloudAiSourceService {
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;
    private final ProtocolDeviceAccess access;
    private final VlStreamDeviceTenantService tenants;
    private final VlStreamDeviceMapper devices;
    private final VlStreamDeviceStreamMapper streams;
    private final RtspDeviceMapper rtsp;
    private final OnvifDeviceMapper onvif;
    private final VlStreamCameraRtcProperties rtc;
    private final CustomDeviceMapper custom;
    private final CustomDeviceStateService customAccess;
    private final CloudAiProtocolSources protocols;
    @org.springframework.beans.factory.annotation.Value("${vlstream.device.default-tenant-id:000000}")
    private String protocolDefaultTenant;

    public CloudAiSourceService(JdbcTemplate jdbc, PermissionService permissions, ProtocolDeviceAccess access,
            VlStreamDeviceTenantService tenants, VlStreamDeviceMapper devices, VlStreamDeviceStreamMapper streams,
            RtspDeviceMapper rtsp, OnvifDeviceMapper onvif, VlStreamCameraRtcProperties rtc,
            CustomDeviceMapper custom, CustomDeviceStateService customAccess, CloudAiProtocolSources protocols) {
        this.jdbc = jdbc; this.permissions = permissions; this.access = access; this.tenants = tenants;
        this.devices = devices; this.streams = streams; this.rtsp = rtsp; this.onvif = onvif; this.rtc = rtc;
        this.custom = custom; this.customAccess = customAccess;
        this.protocols = protocols;
    }

    public static class Selection {
        public String protocol;
        public Long deviceRowId;
        public Long streamId;
    }

    private void requirePermission(String protocol) {
        String permission = "VLSTREAM".equals(protocol) ? "vlstream:device:play"
            : "RTSP".equals(protocol) ? "rtsp:RtspDevice:view" : "ONVIF".equals(protocol) ? "onvif:device:play"
            : "CUSTOM".equals(protocol) ? "custom:device:play" : "GB28181".equals(protocol) ? "wvp:play:start"
            : "EHOME".equals(protocol) ? "ehome:device:preview" : "ISUP".equals(protocol) ? "isup:lsupDevice:start"
            : "DAHUA".equals(protocol) ? "dahua:zlmApi:play" : "";
        if (permission.isEmpty() || !permissions.hasPermi(permission)) throw new ServiceException("无权授权该视频源", 403);
    }

    public List<Map<String, Object>> choices(String protocol, Long rowId) {
        requirePermission(protocol);
        if(protocols.supports(protocol))return protocols.choices(protocol,rowId);
        List<Map<String, Object>> result = new ArrayList<>();
        if ("VLSTREAM".equals(protocol)) {
            tenants.requireDevice(rowId);
            for (VlStreamDeviceStream stream : streams.selectAvailableByDeviceId(rowId)) {
                String type = sourceType(stream.getProtocol(), stream.getSourceUrl());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("streamId", String.valueOf(stream.getId()));
                item.put("name", stream.getStreamName()); item.put("type", type);
                item.put("supported", type != null); result.add(item);
            }
        } else {
            String url;
            if("CUSTOM".equals(protocol))url=customAccess.requireDevice(rowId).getStreamUrl();
            else if("RTSP".equals(protocol)) { RtspDevice device=access.rtsp(rowId); url="3".equals(device.getPlayType())?device.getEasyNTSUrl():device.getUrl();
                if("2".equals(device.getPlayType()))return Collections.singletonList(CloudAiProtocolSources.choice(null,"已配置推流","PLATFORM")); }
            else { OnvifDevice device=access.onvif(rowId);url="3".equals(device.getPlayType())?device.getEasyNTSUrl():device.getUrl();
                if("2".equals(device.getPlayType()))return Collections.singletonList(CloudAiProtocolSources.choice(null,"已配置推流","PLATFORM")); }
            String type = sourceType(scheme(url), url);
            Map<String, Object> item = new LinkedHashMap<>(); item.put("streamId", null);
            item.put("name", "设备主码流"); item.put("type", type);
            item.put("supported", type != null); result.add(item);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> grant(Selection selection) {
        if (selection == null || selection.deviceRowId == null) throw new ServiceException("请选择设备与视频源");
        requirePermission(selection.protocol);
        LoginUser login = SecurityUtils.getLoginUser();
        if (!login.isFederated() || login.getUser() == null || login.getUser().getPlatformUserId() == null)
            throw new ServiceException("请从VLStream控制台授权后台取流", 403);
        String tenant = tenants.currentTenantId();
        String actor = login.getUser().getPlatformUserId();
        Long department = null;
        if (protocols.supports(selection.protocol)) department=protocols.authorize(selection.protocol,selection.deviceRowId);
        else if ("VLSTREAM".equals(selection.protocol)) tenants.requireDevice(selection.deviceRowId);
        else if ("CUSTOM".equals(selection.protocol)) customAccess.validateDirect(customAccess.requireDevice(selection.deviceRowId));
        else if ("RTSP".equals(selection.protocol)) department = access.rtsp(selection.deviceRowId).getDeptId();
        else department = access.onvif(selection.deviceRowId).getDeptId();
        Map<String, Object> source = resolveSource(selection.protocol, selection.deviceRowId, selection.streamId, tenant, department);
        if("PUSH".equals(source.get("provider"))&&!permissions.hasPermi("wvp:push:play"))throw new ServiceException("无权使用设备配置的推流来源",403);
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random), id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO wvp_cloud_ai_source_grant(id,token_hash,tenant_id,actor_id,protocol,device_row_id,stream_id,department_id,source_identity) VALUES(?,?,?,?,?,?,?,?,?)",
            id, hash(token), tenant, actor, selection.protocol, selection.deviceRowId, selection.streamId, department, source.get("deviceId"));
        Map<String, Object> result = publicSource(source);
        result.put("grantId", id); result.put("grantToken", token); return result;
    }

    public Map<String, Object> resolve(String token, String runId) {
        Map<String, Object> grant = find(token);
        Map<String, Object> source = resolveSource((String) grant.get("protocol"), number(grant.get("device_row_id")),
            number(grant.get("stream_id")), (String) grant.get("tenant_id"), number(grant.get("department_id")));
        if (!Objects.equals(grant.get("source_identity"), source.get("deviceId")))
            throw new ServiceException("原设备身份已变化，请重新选择摄像机", 403);
        jdbc.update("UPDATE wvp_cloud_ai_source_grant SET last_used_time=CURRENT_TIMESTAMP WHERE id=?", grant.get("id"));
        source.put("tenantId", grant.get("tenant_id")); source.put("actorId", grant.get("actor_id"));
        source.put("grantId", grant.get("id"));
        if(runId!=null)protocols.acquire(source,String.valueOf(grant.get("id")),runId);
        return source;
    }
    public void release(String token,String runId) {
        Map<String,Object> grant=find(token,true); protocols.release(String.valueOf(grant.get("id")),runId);
    }
    public Map<String,Object> devices(String protocol,int page,int size,String search) { requirePermission(protocol);return protocols.devices(protocol,page,size,search); }

    public void revoke(String token) {
        Map<String, Object> grant = find(token);
        jdbc.update("UPDATE wvp_cloud_ai_source_grant SET revoked=1 WHERE id=?", grant.get("id"));
    }

    private Map<String, Object> find(String token) {
        return find(token,false);
    }
    private Map<String,Object> find(String token,boolean allowRevoked) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ServiceException("取流授权无效", 403);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM wvp_cloud_ai_source_grant WHERE token_hash=?"+(allowRevoked?"":" AND revoked=0"), hash(token));
        if (rows.size() != 1) throw new ServiceException("取流授权已撤销或不存在", 403);
        return rows.get(0);
    }

    private Map<String, Object> resolveSource(String protocol, Long rowId, Long streamId, String tenant, Long department) {
        if(protocols.supports(protocol)) {
            Map<String,Object> source=protocols.describe(protocol,rowId,streamId,tenant,department);
            if("CAMERA_RTC".equals(source.get("type")))attachRtc(source,scheme(String.valueOf(source.get("url"))),String.valueOf(source.get("url")));
            return source;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String url, streamProtocol, name, identity, address;
        if ("VLSTREAM".equals(protocol)) {
            VlStreamDevice device = devices.selectById(rowId);
            VlStreamDeviceStream stream = streamId == null ? null : streams.selectById(streamId);
            if (device == null || !tenant.equals(tenants.deviceTenant(device)) || stream == null
                    || !rowId.equals(stream.getDeviceRowId()) || !Boolean.TRUE.equals(stream.getAvailable()))
                throw new ServiceException("已授权的设备或视频源不可用", 403);
            url = stream.getSourceUrl(); streamProtocol = stream.getProtocol(); name = device.getDeviceName();
            identity = device.getDeviceId(); address = null; result.put("online", device.getOnline());
            result.put("latitude", device.getLatitude()); result.put("longitude", device.getLongitude());
        } else if ("RTSP".equals(protocol)) {
            RtspDevice device = rtsp.selectRtspDeviceById(rowId);
            if (device == null || !tenant.equals(owner(device.getTenantId())) || !Objects.equals(department, device.getDeptId()))
                throw new ServiceException("已授权的RTSP设备归属或访问范围已变化", 403);
            url = credentials(device.getUrl(),device.getUserName(),device.getPassword()); streamProtocol = scheme(url); name = device.getName(); identity = "RTSP:" + rowId; address = device.getAddressMap();
            if("3".equals(device.getPlayType())){url=device.getEasyNTSUrl();streamProtocol=scheme(url);}
            if("2".equals(device.getPlayType())){result.put("provider","PUSH");result.put("streamKey",device.getStreamId());}
        } else if ("ONVIF".equals(protocol)) {
            OnvifDevice device = onvif.selectOnvifDeviceById(rowId);
            if (device == null || !tenant.equals(owner(device.getTenantId())) || !Objects.equals(department, device.getDeptId()))
                throw new ServiceException("已授权的ONVIF设备归属或访问范围已变化", 403);
            url = credentials(device.getUrl(),device.getUserName(),device.getPassword()); streamProtocol = scheme(url); name = device.getName(); identity = "ONVIF:" + rowId; address = device.getAddressMap();
            if("3".equals(device.getPlayType())){url=device.getEasyNTSUrl();streamProtocol=scheme(url);}
            if("2".equals(device.getPlayType())){result.put("provider","PUSH");result.put("streamKey",device.getStreamId());}
        } else if ("CUSTOM".equals(protocol)) {
            // Custom devices currently belong to the default tenant only, matching their management API.
            CustomDevice device = custom.selectDeviceById(rowId);
            if (device == null || !tenants.defaultTenant().equals(tenant))
                throw new ServiceException("已授权的自定义设备不存在或不属于当前租户", 403);
            url = device.getStreamUrl(); streamProtocol = scheme(url); name = device.getDeviceName();
            identity = "CUSTOM:" + device.getDeviceCode(); address = device.getAddress();
            result.put("latitude", device.getLatitude()); result.put("longitude", device.getLongitude());
        } else throw new ServiceException("当前设备未提供受支持的视频源");
        String type = "PUSH".equals(result.get("provider"))?"PLATFORM":sourceType(streamProtocol, url);
        if (type == null) throw new ServiceException("设备未提供可接收的网络视频流，请核对保存的视频源和播放模式");
        result.put("protocol", protocol); result.put("deviceRowId", String.valueOf(rowId));
        result.put("streamId", streamId == null ? null : String.valueOf(streamId)); result.put("type", type);
        result.put("deviceId", identity); result.put("name", name); result.put("address", address);
        if ("CAMERA_RTC".equals(type)) {
            attachRtc(result,streamProtocol,url);
        } else if(!"PLATFORM".equals(type))result.put("url", url);
        return result;
    }
    private void attachRtc(Map<String,Object> result,String streamProtocol,String url) {
            String signal = rtc.getCameraRtcSignalUrl();
            try {
                URI endpoint = URI.create(signal);
                if (!Arrays.asList("ws", "wss").contains(endpoint.getScheme()) || endpoint.getHost() == null
                        || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException error) { throw new ServiceException("后台CameraRTC信令地址尚未配置", 503); }
            result.put("socketUrl", signal); result.put("cameraId", VlStreamPlaybackUtils.cameraRtcDeviceId(streamProtocol, url));
            result.remove("url");
    }

    static String sourceType(String protocol, String url) {
        if (VlStreamPlaybackUtils.cameraRtcDeviceId(protocol==null?scheme(url):protocol, url) != null) return "CAMERA_RTC";
        try {
            URI value = URI.create(url == null ? "" : url);
            if(value.getHost()==null||value.getFragment()!=null||url.contains("\r")||url.contains("\n"))return null;
            String scheme=value.getScheme()==null?"":value.getScheme().toLowerCase(Locale.ROOT);
            if (Arrays.asList("rtsp", "rtsps").contains(scheme)) return "RTSP";
            if (Arrays.asList("rtmp","rtmps").contains(scheme)) return "RTMP";
            if (Arrays.asList("http","https").contains(scheme)) return "HTTP";
            if (Arrays.asList("udp","tcp","srt").contains(scheme)) return "MEDIA";
        } catch (IllegalArgumentException ignored) { }
        return null;
    }
    private static String scheme(String url) {
        try { return URI.create(url == null ? "" : url.trim()).getScheme(); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    static Map<String, Object> publicSource(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.remove("url"); result.remove("socketUrl"); result.remove("cameraId"); return result;
    }
    static String credentials(String url,String user,String password) {
        if(user==null||user.isEmpty())return url;
        try {
            URI parsed=URI.create(url);if(parsed.getRawUserInfo()!=null||!Arrays.asList("rtsp","rtsps").contains(parsed.getScheme()))return url;
            String identity=java.net.URLEncoder.encode(user,"UTF-8").replace("+","%20")+":"+java.net.URLEncoder.encode(password==null?"":password,"UTF-8").replace("+","%20");
            return parsed.getScheme()+"://"+identity+"@"+parsed.getRawAuthority()+Objects.toString(parsed.getRawPath(),"")+(parsed.getRawQuery()==null?"":"?"+parsed.getRawQuery());
        } catch(Exception invalid) { return url; }
    }
    private String owner(String tenant) { return tenant == null || tenant.trim().isEmpty() ? protocolDefaultTenant : tenant; }
    private static Long number(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : bytes) result.append(String.format("%02x", item & 255)); return result.toString();
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException(error); }
    }
}
