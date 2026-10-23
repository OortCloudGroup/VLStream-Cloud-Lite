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
import com.ruoyi.vlstream.service.VlStreamDeviceAccessService;
import com.ruoyi.vlstream.service.VlStreamDevicePreviewLeaseService;
import com.ruoyi.vlstream.util.VlStreamPlaybackUtils;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.StreamContent;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private VlStreamDeviceAccessService accessService;
    @Autowired
    private VlStreamDevicePreviewLeaseService previewLeases;

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
        if (device == null || !Boolean.TRUE.equals(device.getOnline())) {
            return error("设备离线，不允许播放");
        }
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

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/shared/{deviceRef}/access")
    public AjaxResult sharedAccess(@PathVariable String deviceRef) {
        return success(accessService.access(deviceRef));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/shared/{deviceRef}/access-requests")
    public AjaxResult requestAccess(@PathVariable String deviceRef, @RequestBody(required = false) AccessRequestBody request) {
        return success(accessService.request(deviceRef, request == null ? null : request.getReason()));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:share:manage')")
    @PutMapping("/{deviceRowId}/visibility")
    public AjaxResult visibility(@PathVariable Long deviceRowId, @RequestBody VisibilityRequest request) {
        return success(accessService.setVisibility(deviceRowId, request == null ? null : request.getVisibility()));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    @GetMapping("/{deviceRowId}/access-requests")
    public AjaxResult ownerRequests(@PathVariable Long deviceRowId) {
        return success(accessService.ownerRequests(deviceRowId));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    @GetMapping("/{deviceRowId}/access-grants")
    public AjaxResult ownerGrants(@PathVariable Long deviceRowId,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status) {
        return success(accessService.ownerGrants(deviceRowId, pageNum, pageSize, status));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/access-requests/mine")
    public AjaxResult myRequests() {
        return success(accessService.myRequests());
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    @PostMapping("/access-requests/{requestId}/approve")
    public AjaxResult approveRequest(@PathVariable Long requestId, @RequestBody(required = false) ApprovalRequest request) {
        return success(accessService.approve(requestId, request == null ? null : request.getExpiresInDays()));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    @PostMapping("/access-requests/{requestId}/reject")
    public AjaxResult rejectRequest(@PathVariable Long requestId, @RequestBody(required = false) AccessRequestBody request) {
        return success(accessService.reject(requestId, request == null ? null : request.getReason()));
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    @PostMapping("/access-grants/{grantId}/revoke")
    public AjaxResult revokeGrant(@PathVariable Long grantId) {
        return success(accessService.revoke(grantId));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/shared/{deviceRef}/preview-sessions")
    public AjaxResult startSharedPreview(@PathVariable String deviceRef, @RequestBody PreviewRequest request) {
        return success(previewLeases.start(deviceRef, request == null ? null : request.getStreamId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/shared/{deviceRef}/streams")
    public AjaxResult sharedStreams(@PathVariable String deviceRef) {
        return success(previewLeases.streams(deviceRef));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/shared/{deviceRef}/preview-sessions/{leaseId}")
    public AjaxResult renewSharedPreview(@PathVariable String deviceRef, @PathVariable String leaseId) {
        return success(previewLeases.renew(deviceRef, leaseId));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/shared/{deviceRef}/preview-sessions/{leaseId}")
    public AjaxResult closeSharedPreview(@PathVariable String deviceRef, @PathVariable String leaseId) {
        previewLeases.close(deviceRef, leaseId);
        return success();
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

    public static class VisibilityRequest {
        private String visibility;
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
    }

    public static class AccessRequestBody {
        private String reason;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ApprovalRequest {
        private Integer expiresInDays;
        public Integer getExpiresInDays() { return expiresInDays; }
        public void setExpiresInDays(Integer expiresInDays) { this.expiresInDays = expiresInDays; }
    }
}
