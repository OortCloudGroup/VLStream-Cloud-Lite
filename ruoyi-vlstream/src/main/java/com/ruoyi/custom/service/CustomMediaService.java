package com.ruoyi.custom.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.domain.CustomRecordingSession;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.StreamContent;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class CustomMediaService {
    private final CustomDeviceMapper mapper;
    private final IMediaServerService mediaServerService;

    public CustomMediaService(CustomDeviceMapper mapper, IMediaServerService mediaServerService) {
        this.mapper = mapper;
        this.mediaServerService = mediaServerService;
    }

    public StreamContent preview(Long deviceId) {
        CustomDevice device = requireDevice(deviceId);
        if (!supportsZlmPreview(device.getStreamUrl())) {
            throw new ServiceException("仅RTSP/RTMP流需要通过ZLM预览");
        }
        MediaServer mediaServer = requireMediaServer();
        String app = "custom";
        String stream = "device_" + deviceId;
        // Generating StreamInfo only formats URLs; it does not prove the stream exists in ZLM.
        if (mediaServerService.isStreamReady(mediaServer, app, stream)) {
            StreamInfo existing = mediaServerService.getStreamInfoByAppAndStream(mediaServer, app, stream, null, null);
            if (existing != null) return new StreamContent(existing);
        }
        WVPResult<String> result = mediaServerService.addStreamProxy(mediaServer, app, stream,
                device.getStreamUrl(), true, false, "0", 15);
        if (result == null || result.getCode() != 0) {
            throw new ServiceException(result == null ? "ZLM拉流失败" : result.getMsg());
        }
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            while (!mediaServerService.isStreamReady(mediaServer, app, stream)) {
                if (System.currentTimeMillis() >= deadline) throw new ServiceException("ZLM未收到可播放媒体，请检查摄像机出流");
                try { Thread.sleep(250); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("创建预览已取消");
                }
            }
            StreamInfo info = mediaServerService.getStreamInfoByAppAndStream(mediaServer, app, stream, null, null);
            if (info == null) throw new ServiceException("ZLM未返回可用播放地址");
            return new StreamContent(info);
        } catch (RuntimeException error) {
            if (result.getData() != null) mediaServerService.delStreamProxy(mediaServer, result.getData());
            throw error;
        }
    }

    @Transactional
    public CustomRecordingSession startRecording(Long deviceId) {
        CustomRecordingSession active = mapper.selectActiveSession(deviceId);
        if (active != null) return active;
        CustomDevice device = requireDevice(deviceId);
        MediaServer mediaServer = requireMediaServer();
        String app = "custom_record";
        String stream = "device_" + deviceId + "_" + System.currentTimeMillis();
        WVPResult<String> result = mediaServerService.addStreamProxy(mediaServer, app, stream,
                device.getStreamUrl(), true, true, "0", 15);
        if (result == null || result.getCode() != 0) {
            throw new ServiceException(result == null ? "启动录像失败" : result.getMsg());
        }
        CustomRecordingSession session = new CustomRecordingSession();
        session.setDeviceId(deviceId);
        session.setMediaServerId(mediaServer.getId());
        session.setApp(app);
        session.setStream(stream);
        session.setProxyKey(result.getData());
        session.setStatus("RUNNING");
        session.setStartTime(new Date());
        mapper.insertSession(session);
        return session;
    }

    @Transactional
    public void stopRecording(Long deviceId, String reason) {
        CustomRecordingSession session = mapper.selectActiveSession(deviceId);
        if (session == null) return;
        MediaServer mediaServer = mediaServerService.getOne(session.getMediaServerId());
        if (mediaServer != null) {
            if (session.getProxyKey() != null) mediaServerService.delStreamProxy(mediaServer, session.getProxyKey());
            mediaServerService.closeStreams(mediaServer, session.getApp(), session.getStream());
        }
        mapper.stopSession(session.getId(), reason == null ? "manual" : reason);
    }

    public CustomRecordingSession getRecordingStatus(Long deviceId) {
        return mapper.selectActiveSession(deviceId);
    }

    public boolean isMediaAvailable() {
        return onlineDefaultMediaServer() != null;
    }

    static boolean supportsZlmPreview(String streamUrl) {
        if (streamUrl == null) return false;
        String normalized = streamUrl.trim().toLowerCase();
        return normalized.startsWith("rtsp://") || normalized.startsWith("rtmp://");
    }

    private CustomDevice requireDevice(Long id) {
        CustomDevice device = mapper.selectDeviceById(id);
        if (device == null) throw new ServiceException("设备不存在");
        return device;
    }

    private MediaServer requireMediaServer() {
        MediaServer mediaServer = onlineDefaultMediaServer();
        if (mediaServer == null) throw new ServiceException("没有可用的ZLM媒体服务器");
        return mediaServer;
    }

    private MediaServer onlineDefaultMediaServer() {
        MediaServer defaultServer = mediaServerService.getDefaultMediaServer();
        if (defaultServer == null) return null;
        MediaServer runtime = mediaServerService.getOne(defaultServer.getId());
        return runtime != null && runtime.isStatus() ? runtime : null;
    }
}
