package com.ruoyi.isup.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.isup.ehome.*;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/ehome")
public class EhomeController extends BaseController {
    private final EhomeDevices devices;
    private final EhomeSdk sdk;
    private final EhomePreviews previews;
    public EhomeController(EhomeDevices devices, EhomeSdk sdk, EhomePreviews previews) {
        this.devices = devices; this.sdk = sdk; this.previews = previews;
    }

    @GetMapping("/devices")
    @PreAuthorize("@ss.hasPermi('ehome:device:list')")
    public TableDataInfo list(EhomeDevice query) {
        startPage();
        return getDataTable(devices.list(query));
    }

    @GetMapping("/status")
    @PreAuthorize("@ss.hasPermi('ehome:device:list')")
    public AjaxResult status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registrationReady", sdk.ready());
        result.put("streamReady", sdk.streamReady());
        Set<String> hosts = new LinkedHashSet<>();
        if (sdk.publicHost() != null && !sdk.publicHost().trim().isEmpty()) hosts.add(sdk.publicHost().trim());
        else {
            for (EhomeDevice device : devices.list(new EhomeDevice())) {
                if (!"ON".equals(device.getStatus())) continue;
                try { hosts.add(EhomeAddressResolver.resolve(null, device.getIpAddress())); }
                catch (ServiceException ignored) { }
            }
            if (hosts.isEmpty()) hosts.addAll(EhomeAddressResolver.localAddresses());
        }
        result.put("host", hosts.size() == 1 ? hosts.iterator().next() : "");
        result.put("serverAddresses", hosts);
        result.put("addressMode", sdk.publicHost() == null || sdk.publicHost().trim().isEmpty() ? "auto" : "explicit");
        result.put("localAddresses", EhomeAddressResolver.localAddresses());
        result.put("registrationPort", sdk.registrationPort());
        result.put("streamPort", sdk.streamPort());
        result.put("message", sdk.statusMessage());
        return success(result);
    }

    private EhomeDevice accessible(Long id) {
        EhomeDevice query = new EhomeDevice();
        query.setId(id);
        List<EhomeDevice> rows = devices.list(query);
        if (rows.isEmpty()) throw new ServiceException("设备不存在或无权访问");
        return rows.get(0);
    }

    @GetMapping("/devices/{id}/channels")
    @PreAuthorize("@ss.hasPermi('ehome:device:query')")
    public AjaxResult channels(@PathVariable Long id) {
        EhomeDevice device = accessible(id);
        if (!"ON".equals(device.getStatus()) || device.getLuserId() == null) throw new ServiceException("设备已离线，请等待重新注册");
        return success(sdk.channels(device.getLuserId()));
    }

    @PutMapping("/devices/{id}")
    @PreAuthorize("@ss.hasPermi('ehome:device:edit')")
    public AjaxResult edit(@PathVariable Long id, @RequestBody Metadata input) {
        EhomeDevice device = accessible(id);
        if (input.name == null || input.name.trim().isEmpty() || input.name.length() > 64
                || (input.remark != null && input.remark.length() > 500)) throw new ServiceException("名称限 1–64 字，备注限 500 字");
        device.setName(input.name.trim()); device.setRemark(input.remark);
        devices.updateMetadata(device);
        return success();
    }

    @PostMapping("/devices/{id}/preview")
    @PreAuthorize("@ss.hasPermi('ehome:device:preview')")
    public AjaxResult preview(@PathVariable Long id, @RequestBody Preview input) {
        return success(previews.start(accessible(id), input.channel, input.streamType, getUserId()));
    }
    @DeleteMapping("/previews/{id}")
    @PreAuthorize("@ss.hasPermi('ehome:device:preview')")
    public AjaxResult stop(@PathVariable String id) { previews.stop(id, getUserId()); return success(); }
    @PutMapping("/previews/{id}/heartbeat")
    @PreAuthorize("@ss.hasPermi('ehome:device:preview')")
    public AjaxResult heartbeat(@PathVariable String id) { previews.touch(id, getUserId()); return success(); }

    @Data public static class Preview { private int channel; private int streamType; }
    @Data public static class Metadata { private String name; private String remark; }
}
