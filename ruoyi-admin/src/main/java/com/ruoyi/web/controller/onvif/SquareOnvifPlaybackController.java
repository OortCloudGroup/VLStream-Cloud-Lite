package com.ruoyi.web.controller.onvif;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.web.service.ProtocolDeviceAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authorized non-local playback settings, never included in the aggregate device list. */
@RestController
@RequestMapping("/onvif/device")
public class SquareOnvifPlaybackController extends BaseController {
    private final ProtocolDeviceAccess access;
    public SquareOnvifPlaybackController(ProtocolDeviceAccess access) { this.access = access; }
    @PreAuthorize("@ss.hasPermi('onvif:device:play')")
    @PostMapping("/{id}/square-playback")
    public AjaxResult playback(@PathVariable Long id) {
        OnvifDevice device = access.onvif(id);
        Map<String, Object> result = new LinkedHashMap<>();
        if ("2".equals(device.getPlayType())) {
            if (device.getStreamId() == null || device.getStreamId().trim().isEmpty()) throw new ServiceException("设备未配置推流ID");
            result.put("playMode", "PUSH"); result.put("streamId", device.getStreamId());
        } else if ("3".equals(device.getPlayType())) {
            if (device.getEasyNTSUrl() == null || device.getEasyNTSUrl().trim().isEmpty()) throw new ServiceException("设备未配置EasyNTS播放地址");
            result.put("playMode", "DIRECT"); result.put("url", device.getEasyNTSUrl());
        } else throw new ServiceException("播放模式已变化，请刷新设备目录");
        return success(result);
    }
}
