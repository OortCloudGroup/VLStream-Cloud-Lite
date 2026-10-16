package com.ruoyi.custom.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.deviceclassification.service.DeviceClassificationService;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.domain.CustomRecordingPlan;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.custom.service.CustomMediaService;
import com.ruoyi.custom.service.CustomPreviewSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/custom/device")
public class CustomDeviceController extends BaseController {
    private final CustomDeviceMapper mapper;
    private final CustomMediaService mediaService;
    private final DeviceClassificationService classificationService;
    @Autowired private CustomPreviewSessionService previewSessions;
    @Autowired private com.ruoyi.custom.service.CustomDeviceStateService deviceStates;

    public CustomDeviceController(CustomDeviceMapper mapper, CustomMediaService mediaService,
                                  DeviceClassificationService classificationService) {
        this.mapper = mapper;
        this.mediaService = mediaService;
        this.classificationService = classificationService;
    }

    @PreAuthorize("@ss.hasPermi('custom:device:list')")
    @GetMapping("/list")
    public TableDataInfo list(CustomDevice query) {
        java.util.List<CustomDevice> rows = deviceStates.list(query);
        com.ruoyi.common.core.page.PageDomain page = com.ruoyi.common.core.page.TableSupport.buildPageRequest();
        int size = Math.max(1, Math.min(500, page.getPageSize() == null ? 10 : page.getPageSize()));
        long offset = (long) (Math.max(1, page.getPageNum() == null ? 1 : page.getPageNum()) - 1) * size;
        int from = (int) Math.min(rows.size(), offset);
        TableDataInfo result = new TableDataInfo(rows.subList(from, Math.min(rows.size(), from + size)), rows.size());
        result.setCode(200); result.setMsg("查询成功"); return result;
    }

    @PreAuthorize("@ss.hasPermi('custom:device:query')")
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) { return success(deviceStates.requireDevice(id)); }

    @PreAuthorize("@ss.hasPermi('custom:device:add')")
    @Log(title = "自定义协议设备", businessType = BusinessType.INSERT, excludeParamNames = {"streamUrl"})
    @PostMapping
    public AjaxResult add(@Validated @RequestBody CustomDevice device) {
        deviceStates.requireScope();
        if (mapper.countByDeviceCode(device.getDeviceCode(), null) > 0) return error("设备ID已存在");
        device.setStatus(device.getStatus() == null ? "UNKNOWN" : device.getStatus());
        device.setCreateBy(getUsername());
        return toAjax(mapper.insertDevice(device));
    }

    @PreAuthorize("@ss.hasPermi('custom:device:edit')")
    @Log(title = "自定义协议设备", businessType = BusinessType.UPDATE, excludeParamNames = {"streamUrl"})
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody CustomDevice device) {
        if (device.getId() == null) return error("设备不存在");
        CustomDevice previous=deviceStates.requireDevice(device.getId());
        if (mapper.countByDeviceCode(device.getDeviceCode(), device.getId()) > 0) return error("设备ID已存在");
        device.setUpdateBy(getUsername());
        int updated=mapper.updateDevice(device);if(updated>0)deviceStates.invalidate(previous);return toAjax(updated);
    }

    @PreAuthorize("@ss.hasPermi('custom:device:remove')")
    @Log(title = "自定义协议设备", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @Transactional
    public AjaxResult remove(@PathVariable Long[] ids) {
        for(Long id:ids)deviceStates.requireDevice(id);
        for (Long id : ids) mediaService.stopRecording(id, "device_deleted");
        classificationService.removeDeviceRelations("CUSTOM", Arrays.stream(ids).map(String::valueOf).collect(Collectors.toList()));
        mapper.deletePlansByDeviceIds(ids);
        return toAjax(mapper.deleteDevices(ids));
    }

    @PreAuthorize("@ss.hasPermi('custom:device:export')")
    @Log(title = "自定义协议设备", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CustomDevice query) {
        new ExcelUtil<>(CustomDevice.class).exportExcel(response, deviceStates.list(query), "自定义协议设备");
    }

    @PreAuthorize("@ss.hasPermi('custom:device:play')")
    @PostMapping("/{id}/preview")
    public AjaxResult preview(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean lease) {
        deviceStates.requirePlayable(deviceStates.requireDevice(id));
        return success(lease ? previewSessions.start(id, getUserId()) : mediaService.preview(id));
    }

    @PreAuthorize("@ss.hasPermi('custom:device:play')")
    @PutMapping("/{id}/preview/{sessionId}")
    public AjaxResult renewPreview(@PathVariable Long id, @PathVariable String sessionId) {
        previewSessions.renew(id, getUserId(), sessionId); return success();
    }

    @PreAuthorize("@ss.hasPermi('custom:device:play')")
    @DeleteMapping("/{id}/preview/{sessionId}")
    public AjaxResult stopPreview(@PathVariable Long id, @PathVariable String sessionId) {
        previewSessions.stop(id, getUserId(), sessionId); return success();
    }

    @PreAuthorize("@ss.hasPermi('custom:device:list')")
    @GetMapping("/media/status")
    public AjaxResult mediaStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", mediaService.isMediaAvailable());
        return success(result);
    }

    @PreAuthorize("@ss.hasPermi('custom:device:record')")
    @PostMapping("/{id}/record/start")
    public AjaxResult startRecord(@PathVariable Long id) { deviceStates.requireDevice(id);return success(mediaService.startRecording(id)); }

    @PreAuthorize("@ss.hasPermi('custom:device:record')")
    @PostMapping("/{id}/record/stop")
    public AjaxResult stopRecord(@PathVariable Long id) { deviceStates.requireDevice(id);mediaService.stopRecording(id, "manual"); return success(); }

    @PreAuthorize("@ss.hasPermi('custom:device:record')")
    @GetMapping("/{id}/record/status")
    public AjaxResult recordStatus(@PathVariable Long id) { deviceStates.requireDevice(id);return success(mediaService.getRecordingStatus(id)); }

    @PreAuthorize("@ss.hasPermi('custom:device:record')")
    @GetMapping("/{id}/record-plan")
    public AjaxResult getRecordPlan(@PathVariable Long id) {
        deviceStates.requireDevice(id);
        CustomRecordingPlan plan = mapper.selectPlanByDeviceId(id);
        if (plan == null) {
            plan = new CustomRecordingPlan();
            plan.setDeviceId(id); plan.setEnabled(false); plan.setWeekDays("1,2,3,4,5,6,7");
            plan.setStartTime("00:00"); plan.setEndTime("23:59");
        }
        return success(plan);
    }

    @PreAuthorize("@ss.hasPermi('custom:device:record')")
    @PutMapping("/{id}/record-plan")
    public AjaxResult saveRecordPlan(@PathVariable Long id, @Validated @RequestBody CustomRecordingPlan plan) {
        deviceStates.requireDevice(id);
        plan.setDeviceId(id);
        CustomRecordingPlan existing = mapper.selectPlanByDeviceId(id);
        if (existing == null) { plan.setCreateBy(getUsername()); return toAjax(mapper.insertPlan(plan)); }
        plan.setUpdateBy(getUsername()); return toAjax(mapper.updatePlan(plan));
    }

    @PreAuthorize("@ss.hasPermi('custom:device:play')")
    @PostMapping("/{id}/ptz/{command}")
    public AjaxResult unsupportedPtz(@PathVariable Long id, @PathVariable String command) {
        return error("自定义协议未配置PTZ适配器，无法执行控制命令");
    }

    @PreAuthorize("@ss.hasPermi('custom:device:add')")
    @PostMapping("/import")
    public AjaxResult unsupportedImport() { return error("自定义协议设备导入尚未实现"); }
}
