package com.ruoyi.vlstream.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.vlstream.domain.dto.FirmwareDeployRequest;
import com.ruoyi.vlstream.service.VlStreamFirmwareDeploymentService;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import com.ruoyi.vlstream.util.VlStreamPlaybackUtils;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.StreamContent;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vlstream/device")
public class VlStreamDeviceController extends BaseController {
    private final VlStreamDeviceMapper deviceMapper;
    private final VlStreamDeviceStreamMapper streamMapper;
    private final IMediaServerService mediaServerService;
    private final VlStreamFirmwareDeploymentService firmwareDeploymentService;
    private final VlStreamDeviceTenantService tenantService;

    public VlStreamDeviceController(VlStreamDeviceMapper deviceMapper,
                                    VlStreamDeviceStreamMapper streamMapper,
                                    IMediaServerService mediaServerService,
                                    VlStreamFirmwareDeploymentService firmwareDeploymentService,
                                    VlStreamDeviceTenantService tenantService) {
        this.deviceMapper = deviceMapper;
        this.streamMapper = streamMapper;
        this.mediaServerService = mediaServerService;
        this.firmwareDeploymentService = firmwareDeploymentService;
        this.tenantService = tenantService;
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:list')")
    @GetMapping("/list")
    public TableDataInfo list(VlStreamDevice query) {
        tenantService.scopeQuery(query);
        startPage();
        return getDataTable(deviceMapper.selectList(query));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:list')")
    @GetMapping("/{deviceRowId}/streams")
    public AjaxResult streams(@PathVariable Long deviceRowId) {
        tenantService.requireDevice(deviceRowId);
        // 仅设备列表权限保护的流详情接口提供设备上报地址，不改变实体其他接口的脱敏行为。
        List<Map<String, Object>> streams = new ArrayList<>();
        for (VlStreamDeviceStream stream : streamMapper.selectAvailableByDeviceId(deviceRowId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(stream.getId()));
            item.put("deviceRowId", String.valueOf(stream.getDeviceRowId()));
            item.put("channelId", stream.getChannelId());
            item.put("streamName", stream.getStreamName());
            item.put("streamType", stream.getStreamType());
            item.put("protocol", stream.getProtocol());
            item.put("defaultStream", stream.getDefaultStream());
            item.put("available", stream.getAvailable());
            item.put("lastReportTime", stream.getLastReportTime());
            item.put("zlmApp", stream.getZlmApp());
            item.put("zlmStream", stream.getZlmStream());
            item.put("sourceUrl", stream.getSourceUrl());
            streams.add(item);
        }
        return success(streams);
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:list')")
    @GetMapping("/{deviceRowId}/detail")
    public AjaxResult detail(@PathVariable Long deviceRowId) {
        tenantService.requireDevice(deviceRowId);
        return success(firmwareDeploymentService.detail(deviceRowId));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:firmware:deploy')")
    @PostMapping("/{deviceRowId}/firmware-upgrades")
    public AjaxResult deployFirmware(@PathVariable Long deviceRowId, @RequestBody FirmwareDeployRequest request) {
        tenantService.requireDevice(deviceRowId);
        return success(firmwareDeploymentService.deploy(deviceRowId, request == null ? null : request.getFirmwareId()));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:firmware:deploy')")
    @PostMapping("/{deviceRowId}/firmware-upgrades/{requestId}/cancel")
    public AjaxResult cancelFirmware(@PathVariable Long deviceRowId, @PathVariable String requestId) {
        tenantService.requireDevice(deviceRowId);
        return success(firmwareDeploymentService.cancel(deviceRowId, requestId));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:play')")
    @PostMapping("/{deviceRowId}/preview")
    public AjaxResult preview(@PathVariable Long deviceRowId, @RequestBody PreviewRequest request) {
        VlStreamDevice device = tenantService.requireDevice(deviceRowId);
        VlStreamDeviceStream stream = request == null ? null : streamMapper.selectById(request.getStreamId());
        if (device == null || stream == null || !deviceRowId.equals(stream.getDeviceRowId())
                || !Boolean.TRUE.equals(stream.getAvailable())) return error("设备或视频流不存在");

        try {
            if (VlStreamPlaybackUtils.isCameraRtc(stream.getProtocol(), stream.getSourceUrl())) {
                Map<String, Object> playback = new LinkedHashMap<>();
                playback.put("playMode", "cameraRTC");
                playback.put("url", stream.getSourceUrl());
                return success(playback);
            }
            MediaServer mediaServer = getOnlineDefaultMediaServer();
            if (mediaServer == null) return error("没有可用的ZLM媒体服务器");
            String app = "vlstream";
            String streamId = "device_" + deviceRowId + "_stream_" + stream.getId();
            WVPResult<String> proxy = mediaServerService.addStreamProxy(mediaServer, app, streamId,
                    stream.getSourceUrl(), true, false, "0", 15);
            if (proxy == null || proxy.getCode() != 0) return error(proxy == null ? "ZLM拉流失败" : proxy.getMsg());

            StreamInfo info = mediaServerService.getStreamInfoByAppAndStream(mediaServer, app, streamId, null, null);
            if (info == null) return error("ZLM已接受拉流请求，但尚未生成播放地址");
            stream.setZlmApp(app);
            stream.setZlmStream(streamId);
            stream.setZlmProxyKey(proxy.getData());
            stream.setUpdateTime(new Date());
            streamMapper.update(stream);
            return success(new StreamContent(info));
        } catch (RuntimeException ex) {
            logger.warn("VLStream preview failed: deviceRowId={}, streamId={}", deviceRowId, stream.getId(), ex);
            return error("ZLM拉流失败：" + ex.getMessage());
        }
    }

    @GetMapping("/media/status")
    public AjaxResult mediaStatus() {
        MediaServer mediaServer = getOnlineDefaultMediaServer();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("available", mediaServer != null);
        status.put("mediaServerId", mediaServer == null ? null : mediaServer.getId());
        return success(status);
    }

    private MediaServer getOnlineDefaultMediaServer() {
        MediaServer defaultMediaServer = mediaServerService.getDefaultMediaServer();
        if (defaultMediaServer == null) return null;
        MediaServer runtimeMediaServer = mediaServerService.getOne(defaultMediaServer.getId());
        return runtimeMediaServer != null && runtimeMediaServer.isStatus() ? runtimeMediaServer : null;
    }

    public static class PreviewRequest {
        private Long streamId;
        public Long getStreamId() { return streamId; }
        public void setStreamId(Long streamId) { this.streamId = streamId; }
    }
}
