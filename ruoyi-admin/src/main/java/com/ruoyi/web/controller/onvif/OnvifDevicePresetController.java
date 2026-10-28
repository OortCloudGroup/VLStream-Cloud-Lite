package com.ruoyi.web.controller.onvif;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.onvif.domain.bo.DevicePresetRequest;
import com.ruoyi.onvif.service.OnvifDevicePresetService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Credentials and the authoritative Profile never cross this public ID-based boundary. */
@RestController
@RequestMapping("/onvif/service/device")
public class OnvifDevicePresetController {
    private final OnvifDevicePresetService presets;
    public OnvifDevicePresetController(OnvifDevicePresetService presets) { this.presets = presets; }

    @PreAuthorize("@ss.hasPermi('onvif:service:getPresets')")
    @GetMapping("/{id}/presets")
    public AjaxResult list(@PathVariable("id") Long id) { return AjaxResult.success(presets.list(id)); }

    @PreAuthorize("@ss.hasPermi('onvif:service:addPreset')")
    @PostMapping("/{id}/presets")
    public AjaxResult create(@PathVariable("id") Long id, @RequestBody DevicePresetRequest body) {
        presets.create(id, body.getName()); return AjaxResult.success();
    }
    @PreAuthorize("@ss.hasPermi('onvif:service:gotoPreset')")
    @PostMapping("/{id}/presets/goto")
    public AjaxResult goTo(@PathVariable("id") Long id, @RequestBody DevicePresetRequest body) {
        presets.goTo(id, body.getToken()); return AjaxResult.success();
    }
    @PreAuthorize("@ss.hasPermi('onvif:service:removePreset')")
    @DeleteMapping("/{id}/presets")
    public AjaxResult remove(@PathVariable("id") Long id, @RequestBody DevicePresetRequest body) {
        presets.remove(id, body.getToken()); return AjaxResult.success();
    }
}
