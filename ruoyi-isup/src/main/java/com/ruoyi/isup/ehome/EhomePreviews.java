package com.ruoyi.isup.ehome;

import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Viewers share one upstream per channel/stream, with individually owned expiring leases. */
@Service
public class EhomePreviews {
    private final EhomeSdk sdk;
    private final ConcurrentMap<String, Stream> streams = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Lease> leases = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Stream> links = new ConcurrentHashMap<>();
    @Value("${media.ip}") private String mediaHost;
    @Value("${ehome.media-public-host:${media.ip}}") private String mediaPublicHost;
    @Value("${ehome.media-rtmp-port:1935}") private int rtmpPort;
    @Value("${media.http-port}") private int httpPort;
    @Value("${ehome.media-http-scheme:http}") private String httpScheme;
    @Value("${ehome.media-push-sign:}") private String pushSign;
    @Value("${media.secret}") private String mediaSecret;

    public EhomePreviews(EhomeSdk sdk) { this.sdk = sdk; }
    private static String key(String deviceId, int channel, int type) { return deviceId + ":" + channel + ":" + type; }

    public Map<String, Object> start(EhomeDevice device, int channel, int streamType, Long userId) {
        if (channel < 1 || channel > 65535 || streamType < 0 || streamType > 1) throw new ServiceException("通道或码流参数无效");
        if (!EhomeNative.legacy(device.getDevProtocolVersion())) throw new ServiceException("该设备不是已识别的旧版 EHome 设备");
        if (!"ON".equals(device.getStatus()) || device.getLuserId() == null) throw new ServiceException("设备已离线，请等待重新注册");
        if (sdk.channels(device.getLuserId()).stream().noneMatch(c -> c.getId() == channel)) throw new ServiceException("设备未返回该通道，请刷新通道列表");
        String key = key(device.getDeviceId(), channel, streamType);
        Stream candidate = new Stream(key, device.getLuserId());
        Stream previous = streams.putIfAbsent(key, candidate);
        Stream stream = previous == null ? candidate : previous;
        Lease lease = new Lease(UUID.randomUUID().toString(), userId, stream);
        synchronized (stream) {
            if (stream.closed) throw new ServiceException("通道正在释放，请稍后重试");
            leases.put(lease.id, lease);
        }
        try {
            if (previous == null) {
                String sign = pushSign.isEmpty() ? DigestUtils.md5DigestAsHex(mediaSecret.getBytes(StandardCharsets.UTF_8)) : pushSign;
                synchronized (stream) {
                    stream.pipe = createPipe("rtmp://" + mediaHost + ":" + rtmpPort + "/haikang/" + stream.mediaId + "?sign=" + sign, stream.ready);
                }
                int nativeId = sdk.open(stream.loginId, channel, streamType);
                synchronized (stream) {
                    if (stream.closed) { sdk.close(stream.loginId, nativeId); throw new ServiceException("设备已离线"); }
                    stream.nativeId = nativeId;
                }
                sdk.push(stream.loginId, nativeId, device.getDevProtocolVersion());
            }
            if (!"true".equals(stream.ready.get(15, TimeUnit.SECONDS))) throw new ServiceException("视频流建立失败，请检查设备编码和流媒体服务");
            synchronized (stream) {
                if (stream.closed || !stream.pipe.running()) throw new ServiceException("视频流已断开，请重新播放");
                lease.lastSeen = System.currentTimeMillis();
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sessionId", lease.id);
            response.put("url", httpScheme + "://" + mediaPublicHost + ":" + httpPort + "/haikang/" + stream.mediaId + ".live.flv");
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); stop(lease.id, userId);
            throw new ServiceException("预览请求已中断");
        } catch (Exception e) {
            if (previous == null) close(stream); else stop(lease.id, userId);
            if (e instanceof ServiceException) throw (ServiceException) e;
            throw new ServiceException("等待视频流超时，请检查设备到取流端口的网络连接");
        }
    }

    EhomeMediaPipe createPipe(String url, CompletableFuture<String> ready) { return new EhomeMediaPipe(url, ready); }

    public boolean link(int handle, String deviceId, int channel, int type) {
        Stream stream = streams.get(key(deviceId, channel, type));
        if (stream == null) return false;
        synchronized (stream) {
            if (stream.closed || stream.pipe == null || stream.handle >= 0) return false;
            stream.handle = handle;
            links.put(handle, stream);
            return true;
        }
    }
    public void data(int handle, byte[] bytes) {
        Stream stream = links.get(handle);
        if (stream != null && stream.pipe != null) stream.pipe.accept(bytes);
    }
    public boolean hasLink(int handle) { return links.containsKey(handle); }
    public void touch(String id, Long userId) {
        Lease lease = owned(id, userId);
        if (lease == null || lease.stream.closed || lease.stream.pipe == null || !lease.stream.pipe.running()) throw new ServiceException("预览会话已结束，请重新播放");
        lease.lastSeen = System.currentTimeMillis();
    }
    public void stop(String id, Long userId) {
        Lease lease = owned(id, userId);
        if (lease == null) return;
        leases.remove(id, lease);
        close(lease.stream, true);
    }
    private Lease owned(String id, Long userId) {
        Lease lease = leases.get(id);
        if (lease != null && !Objects.equals(lease.userId, userId)) throw new ServiceException("无权操作此预览会话");
        return lease;
    }
    private void close(Stream stream) {
        close(stream, false);
    }
    private void close(Stream stream, boolean onlyUnused) {
        synchronized (stream) {
            if (stream.closed) return;
            if (onlyUnused && leases.values().stream().anyMatch(lease -> lease.stream == stream)) return;
            stream.closed = true;
            streams.remove(stream.key, stream);
            leases.values().removeIf(lease -> lease.stream == stream);
            stream.ready.complete("false");
            if (stream.pipe != null) stream.pipe.close();
            if (stream.handle >= 0) links.remove(stream.handle, stream);
        }
        try { if (stream.nativeId >= 0) sdk.close(stream.loginId, stream.nativeId); }
        finally { if (stream.handle >= 0) sdk.closeLink(stream.handle); }
    }
    public void offline(int loginId) {
        for (Stream stream : streams.values()) if (stream.loginId == loginId) close(stream);
    }
    @Scheduled(fixedDelay = 15000)
    public void expire() {
        long now = System.currentTimeMillis();
        for (Lease lease : leases.values()) if (now - lease.lastSeen > 60000) stop(lease.id, lease.userId);
        for (Stream stream : streams.values()) if (stream.pipe != null && !stream.pipe.running()) close(stream);
    }
    @PreDestroy public void destroy() { for (Stream stream : streams.values()) close(stream); }
    private static final class Stream {
        final String key, mediaId = "EHOME_" + UUID.randomUUID().toString().replace("-", "");
        final int loginId;
        final CompletableFuture<String> ready = new CompletableFuture<>();
        volatile int nativeId = -1, handle = -1;
        volatile boolean closed;
        volatile EhomeMediaPipe pipe;
        Stream(String key, int loginId) { this.key = key; this.loginId = loginId; }
    }
    private static final class Lease {
        final String id;
        final Long userId;
        final Stream stream;
        volatile long lastSeen = System.currentTimeMillis();
        Lease(String id, Long userId, Stream stream) { this.id = id; this.userId = userId; this.stream = stream; }
    }
}
