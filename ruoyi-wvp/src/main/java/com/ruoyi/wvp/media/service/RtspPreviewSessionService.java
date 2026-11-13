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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;

/** Independently owned preview leases over a shared per-device, per-source proxy. */
@Service
public class RtspPreviewSessionService {
    private static final long LEASE_MILLIS = 120_000;
    private final IMediaServerService mediaService;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Upstream> upstreams = new ConcurrentHashMap<>();

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
        // IDs belong to the device's protocol table. Controllers still check the caller's
        // tenant, department and permission on start/renew/stop; sharing grants no access.
        // Exact source credentials are part of the digest, so changing them cannot reuse old proof.
        String sourceKey = protocol + ":" + deviceId + ":" + server.getId() + ":" + fingerprint(url);
        String id = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> result = new LinkedHashMap<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        // Atomic per source, including the initial network pull: simultaneous viewers
        // cannot race into opening duplicate camera connections. Other sources are independent.
        upstreams.compute(sourceKey, (key, existing) -> {
            if (existing != null && existing.viewers == 0) {
                if (!deleteProxy(existing)) throw new ServiceException("旧预览资源正在释放，请稍后重试");
                existing = null;
            }
            Upstream upstream = existing;
            if (upstream == null) {
                String stream = protocol.toLowerCase(java.util.Locale.ROOT) + "_device_" + deviceId + "_" + id;
                WVPResult<String> proxy = mediaService.addStreamProxy(server, "rtsp_preview", stream, url, true, false, "0", 15);
                if (proxy == null || proxy.getCode() != 0 || proxy.getData() == null) {
                    throw new ServiceException(StreamProxyFailure.publicMessage(proxy == null ? null : proxy.getMsg()));
                }
                upstream = new Upstream(server, proxy.getData(), stream);
            }
            try {
                if (existing == null) waitForMedia(upstream);
                else if (!mediaService.isStreamReady(server, "rtsp_preview", upstream.stream)) {
                    throw new ServiceException("摄像机媒体流已中断，请关闭预览后重试");
                }
                StreamInfo info = mediaService.getStreamInfoByAppAndStream(server, "rtsp_preview", upstream.stream, null, null);
                if (info == null) throw new ServiceException("ZLM未返回可用播放地址");
                result.put("sessionId", id);
                result.put("streamInfo", new StreamContent(info));
                sessions.put(id, new Session(protocol, deviceId, userId, sourceKey, upstream));
                upstream.viewers++;
            } catch (RuntimeException error) {
                failure.set(error);
                // A failed join must never delete another viewer's active stream.
                if (upstream.viewers == 0) {
                    if (deleteProxy(upstream)) return null;
                    upstream.orphaned = true;
                }
            }
            return upstream;
        });
        if (failure.get() != null) throw failure.get();
        return result;
    }

    private void waitForMedia(Upstream upstream) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!mediaService.isStreamReady(upstream.server, "rtsp_preview", upstream.stream)) {
            if (System.currentTimeMillis() >= deadline) throw new ServiceException("ZLM未收到可播放媒体，请检查摄像机出流");
            try { Thread.sleep(250); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ServiceException("创建预览已取消");
            }
        }
    }

    private static String fingerprint(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private boolean deleteProxy(Upstream upstream) {
        try { return Boolean.TRUE.equals(mediaService.delStreamProxy(upstream.server, upstream.proxyKey)); }
        catch (RuntimeException ignored) { return false; }
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
        upstreams.computeIfPresent(session.sourceKey, (key, upstream) -> {
            if (upstream != session.upstream) {
                sessions.remove(id, session);
                return upstream;
            }
            if (!session.detached) {
                upstream.viewers--;
                session.detached = true;
            }
            if (upstream.viewers > 0) {
                sessions.remove(id, session);
                return upstream;
            }
            // Retain a terminal session on failed deletion; retries decrement only once.
            if (Boolean.TRUE.equals(mediaService.delStreamProxy(upstream.server, upstream.proxyKey))) {
                sessions.remove(id, session);
                return null;
            }
            return upstream;
        });
        if (session.detached && !upstreams.containsKey(session.sourceKey)) sessions.remove(id, session);
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
        // Includes a newly created proxy whose readiness check failed before a lease existed.
        upstreams.forEach((key, ignored) -> upstreams.computeIfPresent(key,
                (source, upstream) -> upstream.orphaned && deleteProxy(upstream) ? null : upstream));
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
        upstreams.forEach((key, ignored) -> upstreams.computeIfPresent(key,
                (source, upstream) -> upstream.orphaned && deleteProxy(upstream) ? null : upstream));
    }

    private static class Session {
        final String protocol;
        final Long deviceId;
        final Long userId;
        final String sourceKey;
        final Upstream upstream;
        long expiresAt = System.currentTimeMillis() + LEASE_MILLIS;
        // Guarded by this session's monitor; failed proxy deletion cannot reopen a lease.
        boolean terminating;
        boolean detached;

        Session(String protocol, Long deviceId, Long userId, String sourceKey, Upstream upstream) {
            this.protocol = protocol;
            this.deviceId = deviceId;
            this.userId = userId;
            this.sourceKey = sourceKey;
            this.upstream = upstream;
        }
    }

    private static class Upstream {
        final MediaServer server;
        final String proxyKey;
        final String stream;
        // Accessed only inside the per-source compute operation.
        int viewers;
        boolean orphaned;
        Upstream(MediaServer server, String proxyKey, String stream) {
            this.server = server;
            this.proxyKey = proxyKey;
            this.stream = stream;
        }
    }
}
