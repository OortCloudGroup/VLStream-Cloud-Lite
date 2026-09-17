package com.ruoyi.web.controller.device;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.deviceclassification.domain.DeviceCategory;
import com.ruoyi.common.deviceclassification.domain.DeviceClassificationAssignment;
import com.ruoyi.common.deviceclassification.service.DeviceClassificationService;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/classification")
public class DeviceClassificationController extends BaseController {
    private static final String LIST_PERMISSIONS = "isup:lsupDevice:list,ehome:device:list,rtsp:RtspDevice:list,onvif:device:list,dahua:device:list,wvp:device:list,vlstream:device:list,custom:device:list";
    private static final String EDIT_PERMISSIONS = "isup:lsupDevice:edit,ehome:device:edit,rtsp:RtspDevice:edit,onvif:device:edit,dahua:device:edit,wvp:device:edit,vlstream:device:edit,custom:device:edit";

    private final DeviceClassificationService service;

    public DeviceClassificationController(DeviceClassificationService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasAnyPermi('" + LIST_PERMISSIONS + "')")
    @GetMapping("/tree")
    public AjaxResult tree(@RequestParam String categoryType, @RequestParam String protocolType) {
        return success(service.tree(categoryType, protocolType));
    }

    @PreAuthorize("@ss.hasAnyPermi('" + LIST_PERMISSIONS + "')")
    @GetMapping("/device-ids")
    public AjaxResult deviceIds(@RequestParam String categoryType, @RequestParam Long categoryId) {
        return success(service.logicalDeviceIds(categoryType, categoryId));
    }

    @PreAuthorize("@ss.hasAnyPermi('" + EDIT_PERMISSIONS + "')")
    @Log(title = "设备分类", businessType = BusinessType.INSERT)
    @PostMapping("/category")
    public AjaxResult addCategory(@RequestBody DeviceCategory category) {
        return toAjax(service.addCategory(category));
    }

    @PreAuthorize("@ss.hasAnyPermi('" + EDIT_PERMISSIONS + "')")
    @Log(title = "设备分类", businessType = BusinessType.UPDATE)
    @PutMapping("/category")
    public AjaxResult updateCategory(@RequestBody DeviceCategory category) {
        return toAjax(service.updateCategory(category));
    }

    @PreAuthorize("@ss.hasAnyPermi('" + EDIT_PERMISSIONS + "')")
    @Log(title = "设备分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/category/{id}")
    public AjaxResult deleteCategory(@PathVariable Long id) {
        return toAjax(service.deleteCategory(id));
    }

    @PreAuthorize("@ss.hasAnyPermi('" + LIST_PERMISSIONS + "')")
    @GetMapping("/assignments")
    public AjaxResult assignments(@RequestParam String protocolType, @RequestParam String deviceKey) {
        return success(service.getAssignment(protocolType, deviceKey));
    }

    @PreAuthorize("@ss.hasAnyPermi('" + EDIT_PERMISSIONS + "')")
    @Log(title = "设备分类", businessType = BusinessType.UPDATE)
    @PutMapping("/assignments")
    public AjaxResult saveAssignments(@RequestBody DeviceClassificationAssignment assignment) {
        service.saveAssignment(assignment);
        return success();
    }
}
