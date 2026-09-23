package com.ruoyi.vlstream.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.dto.VlStreamInternalDeviceView;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service lookup used by VLS event ingestion. */
@RestController
@RequestMapping("/internal/vlstream/device")
public class VlStreamInternalDeviceController extends BaseController {
    private final VlStreamDeviceMapper deviceMapper;
    private final VlStreamDeviceProperties properties;

    public VlStreamInternalDeviceController(VlStreamDeviceMapper deviceMapper) {
        this(deviceMapper, new VlStreamDeviceProperties());
    }

    @Autowired
    public VlStreamInternalDeviceController(VlStreamDeviceMapper deviceMapper, VlStreamDeviceProperties properties) {
        this.deviceMapper = deviceMapper;
        this.properties = properties;
    }

    @Anonymous
    @GetMapping("/{deviceId}")
    public AjaxResult get(@PathVariable String deviceId) {
        VlStreamDevice device = deviceMapper.selectByDeviceId(StringUtils.trimToEmpty(deviceId));
        if (device == null) {
            return AjaxResult.error(HttpStatus.NOT_FOUND, "VLStream设备不存在");
        }
        return success(toView(device));
    }

    private VlStreamInternalDeviceView toView(VlStreamDevice device) {
        VlStreamInternalDeviceView view = new VlStreamInternalDeviceView();
        view.setId(device.getId());
        view.setDeviceId(device.getDeviceId());
        view.setTenantId(StringUtils.defaultIfBlank(device.getTenantId(), properties.getDefaultTenantId()));
        view.setDeviceName(device.getDeviceName());
        view.setDeviceSerial(device.getDeviceSerial());
        view.setDeviceModel(device.getDeviceModel());
        view.setIpAddr(device.getIpAddr());
        view.setOnline(device.getOnline());
        return view;
    }
}
