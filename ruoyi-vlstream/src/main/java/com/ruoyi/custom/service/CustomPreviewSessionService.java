package com.ruoyi.custom.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.VlStreamTenantContext;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.StreamContent;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Square previews own temporary proxies, independently of legacy previews and recordings. */
@Service
public class CustomPreviewSessionService {
    private final CustomDeviceMapper mapper;
    private final IMediaServerService media;
    private final VlStreamDeviceTenantService tenants;
    @org.springframework.beans.factory.annotation.Autowired
    private CustomDeviceStateService deviceStates;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ruoyi.custom.availability.CustomMediaBindings bindings;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public CustomPreviewSessionService(CustomDeviceMapper mapper, IMediaServerService media,
            VlStreamDeviceTenantService tenants) {
        this.mapper = mapper; this.media = media; this.tenants = tenants;
    }
    private CustomDevice requireDevice(Long id) {
        String tenant = tenants.defaultTenant();
        if (!tenant.equals(VlStreamTenantContext.currentTenant(tenant))) throw new ServiceException("设备不存在或不属于当前租户", 403);
        CustomDevice device = mapper.selectDeviceById(id);
        if (device == null) throw new ServiceException("设备不存在或无权访问", 403);
        return device;
    }
    public Map<String, Object> start(Long deviceId, Long userId) {
        CustomDevice device = requireDevice(deviceId);
        if (deviceStates != null) deviceStates.requirePlayable(device);
        String url = device.getStreamUrl();
        URI uri;
        try { uri = URI.create(url == null ? "" : url.trim()); }
        catch (IllegalArgumentException error) { throw new ServiceException("设备未配置有效播放地址"); }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        Map<String, Object> result = new LinkedHashMap<>();
        if (Arrays.asList("http", "https").contains(scheme)) {
            if(deviceStates!=null)deviceStates.validateDirect(device);
            if (uri.getHost() == null || uri.getUserInfo() != null) throw new ServiceException("直接播放地址无效或包含认证信息");
            result.put("playMode", "DIRECT"); result.put("url", url.trim()); return result;
        }
        if (!Arrays.asList("rtsp", "rtmp").contains(scheme) || uri.getHost() == null)
            throw new ServiceException("设备未配置支持的播放地址");
        String sourceRevision=new com.ruoyi.custom.availability.CustomStreamSpec(device).fingerprint;
        if(deviceStates!=null)url=deviceStates.validatedProxyUrl(device);
        MediaServer configured = media.getDefaultMediaServer();
        MediaServer server = configured == null ? null : media.getOne(configured.getId());
        if (server == null || !server.isStatus()) throw new ServiceException("没有可用的ZLM媒体服务器");
        String id = UUID.randomUUID().toString().replace("-", "");
        String app = "custom_preview", stream = "device_" + deviceId + "_" + id;
        WVPResult<String> proxy = media.addStreamProxy(server, app, stream, url.trim(), true, false, "0", 15);
        if (proxy == null || proxy.getCode() != 0 || proxy.getData() == null) throw new ServiceException("ZLM拉流失败，请检查设备地址和网络");
        Session session = new Session(deviceId, userId, server, proxy.getData());
        session.stream=stream;
        session.sourceRevision=sourceRevision;
        sessions.put(id, session);
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            while (!media.isStreamReady(server, app, stream)) {
                if (System.currentTimeMillis() >= deadline) throw new ServiceException("ZLM未收到可播放媒体，请检查摄像机出流");
                try { Thread.sleep(250); } catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new ServiceException("创建预览已取消");
                }
            }
            StreamInfo info = media.getStreamInfoByAppAndStream(server, app, stream, null, null);
            if (info == null) throw new ServiceException("ZLM未返回可用播放地址");
            if(bindings!=null)bindings.add(new com.ruoyi.custom.availability.CustomStreamSpec(device),server.getId(),app,stream,session.expiresAt);
            result.put("playMode", "ZLM"); result.put("sessionId", id); result.put("streamInfo", new StreamContent(info));
            return result;
        } catch (RuntimeException error) {
            synchronized (session) {
                session.terminating = true; session.expiresAt = 0;
                try { release(id, session); } catch (RuntimeException ignored) { /* expiry retries */ }
            }
            throw error;
        }
    }
    public void renew(Long deviceId, Long userId, String id) {
        CustomDevice currentDevice=requireDevice(deviceId);
        Session session = sessions.get(id);
        if (session == null) throw new ServiceException("预览会话已结束，请重新播放");
        synchronized (session) {
            requireOwner(session, deviceId, userId);
            if (sessions.get(id) != session) throw new ServiceException("预览会话已结束，请重新播放");
            long now = System.currentTimeMillis();
            if (session.terminating || session.expiresAt <= now) {
                session.terminating = true; session.expiresAt = 0;
                throw new ServiceException("预览会话已结束，请重新播放");
            }
            String revision=null;
            try{revision=new com.ruoyi.custom.availability.CustomStreamSpec(currentDevice).fingerprint;}catch(com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure invalid){}
            if(session.sourceRevision!=null&&!session.sourceRevision.equals(revision)){
                session.terminating=true;session.expiresAt=0;
                try{release(id,session);}catch(RuntimeException retryOnExpiry){}
                throw new ServiceException("视频源地址已变更，请重新播放");
            }
            session.expiresAt = now + 120_000;
            if(bindings!=null)bindings.renew("custom_preview",session.stream,session.expiresAt);
        }
    }
    public void stop(Long deviceId, Long userId, String id) {
        requireDevice(deviceId);
        Session session = sessions.get(id);
        if (session == null) return;
        synchronized (session) {
            requireOwner(session, deviceId, userId);
            if (sessions.get(id) != session) return;
            session.terminating = true; session.expiresAt = 0; release(id, session);
        }
    }
    private void requireOwner(Session session, Long deviceId, Long userId) {
        if (!session.deviceId.equals(deviceId) || !session.userId.equals(userId)) throw new ServiceException("无权操作此预览会话", 403);
    }
    private void release(String id, Session session) {
        if(bindings!=null)bindings.remove("custom_preview",session.stream);
        if (Boolean.TRUE.equals(media.delStreamProxy(session.server, session.proxyKey))) sessions.remove(id, session);
    }
    @Scheduled(fixedDelay = 30_000)
    public void expireSessions() {
        sessions.forEach((id, session) -> { synchronized (session) {
            if (sessions.get(id) == session && (session.terminating || session.expiresAt <= System.currentTimeMillis())) {
                session.terminating = true; session.expiresAt = 0;
                try { release(id, session); } catch (RuntimeException ignored) { }
            }
        } });
    }
    @PreDestroy public void close() {
        sessions.forEach((id, session) -> { synchronized (session) {
            if (sessions.get(id) == session) {
                session.terminating = true; session.expiresAt = 0;
                try { release(id, session); } catch (RuntimeException ignored) { }
            }
        } });
    }
    private static class Session {
        final Long deviceId, userId; final MediaServer server; final String proxyKey;
        long expiresAt = System.currentTimeMillis() + 120_000;
        String stream,sourceRevision;
        // Guarded by this session's monitor; terminal even while proxy deletion is retried.
        boolean terminating;
        Session(Long deviceId, Long userId, MediaServer server, String proxyKey) {
            this.deviceId = deviceId; this.userId = userId; this.server = server; this.proxyKey = proxyKey;
        }
    }
}
