package com.ruoyi.wvp.media.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.vmanager.bean.StreamContent;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary, independently owned RTSP previews; no device or proxy rows are created. */
@Service
public class RtspPreviewSessionService {
    private static final long LEASE_MILLIS = 120_000;
    private final IMediaServerService mediaService;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public RtspPreviewSessionService(IMediaServerService mediaService) {
        this.mediaService = mediaService;
    }

    public Map<String, Object> start(Long deviceId, Long userId, String url) {
        return start("RTSP", deviceId, userId, url);
    }

    public Map<String, Object> start(String protocol, Long deviceId, Long userId, String url) {
        if (!("RTSP".equals(protocol) || "ONVIF".equals(protocol))) throw new ServiceException("无效预览协议");
        if (url == null || !url.toLowerCase(java.util.Locale.ROOT).startsWith("rtsp://")) {
            throw new ServiceException("设备未配置有效的RTSP地址");
        }
        MediaServer configured = mediaService.getDefaultMediaServer();
        MediaServer server = configured == null ? null : mediaService.getOne(configured.getId());
        if (server == null || !server.isStatus()) throw new ServiceException("没有可用的ZLM媒体服务器");
        String id = UUID.randomUUID().toString().replace("-", "");
        String app = "rtsp_preview";
        String stream = protocol.toLowerCase(java.util.Locale.ROOT) + "_device_" + deviceId + "_" + id;
        // ZLM defines 0 as TCP, 1 as UDP. TCP also avoids Docker RTP return-port mapping.
        WVPResult<String> proxy = mediaService.addStreamProxy(server, app, stream, url, true, false, "0", 15);
        if (proxy == null || proxy.getCode() != 0 || proxy.getData() == null) {
            throw new ServiceException("ZLM拉流失败，请检查设备地址、账号和网络");
        }
        Session session = new Session(protocol, deviceId, userId, server, proxy.getData());
        sessions.put(id, session);
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            while (!mediaService.isStreamReady(server, app, stream)) {
                if (System.currentTimeMillis() >= deadline) throw new ServiceException("ZLM未收到可播放媒体，请检查摄像机出流");
                try { Thread.sleep(250); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("创建预览已取消");
                }
            }
            StreamInfo info = mediaService.getStreamInfoByAppAndStream(server, app, stream, null, null);
            if (info == null) throw new ServiceException("ZLM未返回可用播放地址");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", id);
            result.put("streamInfo", new StreamContent(info));
            return result;
        } catch (RuntimeException error) {
            synchronized (session) {
                session.terminating = true;
                session.expiresAt = 0;
                try { release(id, session); } catch (RuntimeException ignored) { /* retry expired cleanup */ }
            }
            throw error;
        }
    }

    public void renew(Long deviceId, Long userId, String id) { renew("RTSP", deviceId, userId, id); }

    public void renew(String protocol, Long deviceId, Long userId, String id) {
        Session session = sessions.get(id);
        if (session == null) throw new ServiceException("预览会话已结束，请重新播放");
        synchronized (session) {
            requireOwner(session, protocol, deviceId, userId);
            if (sessions.get(id) != session) throw new ServiceException("预览会话已结束，请重新播放");
            long now = System.currentTimeMillis();
            if (session.terminating || session.expiresAt <= now) {
                session.terminating = true;
                session.expiresAt = 0;
                throw new ServiceException("预览会话已结束，请重新播放");
            }
            session.expiresAt = now + LEASE_MILLIS;
        }
    }

    public void stop(Long deviceId, Long userId, String id) { stop("RTSP", deviceId, userId, id); }

    public void stop(String protocol, Long deviceId, Long userId, String id) {
        Session session = sessions.get(id);
        if (session == null) return;
        synchronized (session) {
            requireOwner(session, protocol, deviceId, userId);
            if (sessions.get(id) != session) return;
            session.terminating = true;
            session.expiresAt = 0;
            release(id, session);
        }
    }

    private void requireOwner(Session session, String protocol, Long deviceId, Long userId) {
        if (!session.protocol.equals(protocol) || !session.deviceId.equals(deviceId) || !session.userId.equals(userId)) {
            throw new ServiceException("无权操作此预览会话");
        }
    }

    private void release(String id, Session session) {
        // Keep failed cleanup for retry; never stop a different viewer's stream.
        if (Boolean.TRUE.equals(mediaService.delStreamProxy(session.server, session.proxyKey))) {
            sessions.remove(id, session);
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void expireSessions() {
        sessions.forEach((id, session) -> {
            synchronized (session) {
                if (sessions.get(id) == session && (session.terminating || session.expiresAt <= System.currentTimeMillis())) {
                    session.terminating = true;
                    session.expiresAt = 0;
                    try { release(id, session); } catch (RuntimeException ignored) { /* retry next sweep */ }
                }
            }
        });
    }

    @PreDestroy
    public void close() {
        sessions.forEach((id, session) -> {
            synchronized (session) {
                if (sessions.get(id) == session) {
                    session.terminating = true;
                    session.expiresAt = 0;
                    try { release(id, session); } catch (RuntimeException ignored) { /* ZLM no-reader hook remains */ }
                }
            }
        });
    }

    private static class Session {
        final String protocol;
        final Long deviceId;
        final Long userId;
        final MediaServer server;
        final String proxyKey;
        long expiresAt = System.currentTimeMillis() + LEASE_MILLIS;
        // Guarded by this session's monitor; failed proxy deletion cannot reopen a lease.
        boolean terminating;

        Session(String protocol, Long deviceId, Long userId, MediaServer server, String proxyKey) {
            this.protocol = protocol;
            this.deviceId = deviceId;
            this.userId = userId;
            this.server = server;
            this.proxyKey = proxyKey;
        }
    }
}
