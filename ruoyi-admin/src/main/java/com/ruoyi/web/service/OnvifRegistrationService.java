package com.ruoyi.web.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.WSDeviceBo;
import com.ruoyi.onvif.service.IOnvifDeviceService;
import com.ruoyi.wvp.media.service.RtspPreviewSessionService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Verify both ONVIF profiles and actual media access before registration. */
@Service
public class OnvifRegistrationService {
    private final IOnvifDeviceService devices;
    private final RtspPreviewSessionService previews;

    public OnvifRegistrationService(IOnvifDeviceService devices, RtspPreviewSessionService previews) {
        this.devices = devices;
        this.previews = previews;
    }

    public OnvifDevice authenticate(WSDeviceBo account, Long userId) {
        OnvifDevice verified = devices.addOnvif(account);
        Map<String, String> streams = streams(verified);
        verifyMedia(streams.values().iterator().next(), userId);
        return verified;
    }

    public int register(OnvifDevice device, String auth, Long userId) {
        if (device.getName() == null || device.getName().trim().isEmpty()) {
            throw new ServiceException("请输入设备名称");
        }
        if (!("1".equals(device.getPlayType()) || "2".equals(device.getPlayType()) || "3".equals(device.getPlayType()))) {
            throw new ServiceException("请选择有效的播放类型");
        }
        if ("2".equals(device.getPlayType()) && (device.getStreamId() == null || device.getStreamId().trim().isEmpty())) {
            throw new ServiceException("请输入流id");
        }
        if ("3".equals(device.getPlayType()) && (device.getEasyNTSUrl() == null || device.getEasyNTSUrl().trim().isEmpty())) {
            throw new ServiceException("请输入EasyNTS地址");
        }
        WSDeviceBo account = new WSDeviceBo();
        account.setIp(device.getIp());
        account.setUsername(device.getUserName());
        account.setPassword(device.getPassword());
        account.setAuth(auth == null ? "1" : auth);
        OnvifDevice verified = devices.addOnvif(account);
        Map<String, String> streams = streams(verified);
        String selected = streams.get(device.getChannel());
        if (selected == null) throw new ServiceException("请选择设备实际返回的视频通道");
        // Never trust client-supplied URIs or an earlier authentication after credentials changed.
        verifyMedia(selected, userId);
        device.setStreamUris(streams);
        if ("1".equals(device.getPlayType())) device.setUrl(selected);
        return devices.insertOnvifDevice(device);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> streams(OnvifDevice device) {
        Object value = device == null ? null : device.getStreamUris();
        if (!(value instanceof Map) || ((Map<?, ?>) value).isEmpty()) {
            throw new ServiceException("设备未返回可用视频通道，请检查 ONVIF 配置与账号");
        }
        return (Map<String, String>) value;
    }

    private void verifyMedia(String url, Long userId) {
        Map<String, Object> session = previews.start("ONVIF", 0L, userId, url);
        previews.stop("ONVIF", 0L, userId, (String) session.get("sessionId"));
    }
}
