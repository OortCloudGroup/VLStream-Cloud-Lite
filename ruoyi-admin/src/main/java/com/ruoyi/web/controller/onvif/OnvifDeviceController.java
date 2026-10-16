package com.ruoyi.web.controller.onvif;

import com.ruoyi.onvif.domain.bo.WSDeviceBo;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.deviceclassification.service.DeviceClassificationService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.service.IOnvifDeviceService;
import com.ruoyi.onvif.service.OnvifSubnetDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * onvif 设备Controller
 *
 * @author 陈江灿
 * @date 2025-04-10
 */
@RestController
@RequestMapping("/onvif/device")
public class OnvifDeviceController extends BaseController {
    @Autowired
    private IOnvifDeviceService onvifDeviceService;

    @Autowired
    private OnvifSubnetDiscovery subnetDiscovery;

    @Autowired
    private DeviceClassificationService classificationService;

    @Autowired
    private com.ruoyi.wvp.media.service.RtspPreviewSessionService previewSessions;

    @Autowired
    private com.ruoyi.web.service.ProtocolDeviceAccess previewAccess;

    @PreAuthorize("@ss.hasPermi('onvif:device:play')")
    @PostMapping("/{id}/preview")
    public AjaxResult preview(@PathVariable Long id) {
        OnvifDevice device = previewAccess.onvif(id);
        if (!"1".equals(device.getPlayType())) return error("此接口仅用于ONVIF本地预览");
        return success(previewSessions.start("ONVIF", id, getUserId(), device.getUrl()));
    }

    @PreAuthorize("@ss.hasPermi('onvif:device:play')")
    @PutMapping("/{id}/preview/{sessionId}")
    public AjaxResult renewPreview(@PathVariable Long id, @PathVariable String sessionId) {
        previewAccess.onvif(id);
        previewSessions.renew("ONVIF", id, getUserId(), sessionId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('onvif:device:play')")
    @DeleteMapping("/{id}/preview/{sessionId}")
    public AjaxResult stopPreview(@PathVariable Long id, @PathVariable String sessionId) {
        previewAccess.onvif(id);
        previewSessions.stop("ONVIF", id, getUserId(), sessionId);
        return success();
    }


    /**
     * Web Services Dynamic Discovery
     * @return
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:WSDiscovery')")
    @GetMapping("/WSDiscovery")
    public AjaxResult WSDiscovery(@RequestParam(value = "subnet", required = false) String subnet) throws Exception {
        return success(subnet == null || subnet.trim().isEmpty()
                ? onvifDeviceService.WSDiscovery() : subnetDiscovery.discover(subnet));
    }

    /**
     * 查询onvif 设备列表
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:list')")
    @GetMapping("/list")
    public TableDataInfo list(OnvifDevice onvifDevice) {
        startPage();
        List<OnvifDevice> list = onvifDeviceService.selectOnvifDeviceList(onvifDevice);
        return getDataTable(list);
    }

    /**
     * 查询onvif 设备列表
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:list')")
    @GetMapping("/deviceList")
    public AjaxResult deviceList(OnvifDevice onvifDevice, String mapType) {
        List<OnvifDevice> list = new ArrayList<>();
        if ("map".equals(mapType)) {
            list = onvifDeviceService.selectOnvifDeviceListMap(onvifDevice);
        } else {
            list = onvifDeviceService.selectOnvifDeviceList(onvifDevice);
        }
        return success(list);
    }

    /**
     * 导出onvif 设备列表
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:export')")
    @Log(title = "onvif 设备", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, OnvifDevice onvifDevice) {
        List<OnvifDevice> list = onvifDeviceService.selectOnvifDeviceList(onvifDevice);
        ExcelUtil<OnvifDevice> util = new ExcelUtil<OnvifDevice>(OnvifDevice.class);
        util.exportExcel(response, list, "onvif 设备数据");
    }

    /**
     * 获取onvif 设备详细信息
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(onvifDeviceService.selectOnvifDeviceById(id));
    }

    /**
     * 新增onvif 设备
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:add')")
    @Log(title = "onvif 设备", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody OnvifDevice onvifDevice) {
        return toAjax(onvifDeviceService.insertOnvifDevice(onvifDevice));
    }

//    @PreAuthorize("@ss.hasPermi('onvif:device:auth')")
//    @Log(title = "onvif 设备", businessType = BusinessType.INSERT)
//    @PostMapping
//    public AjaxResult auth(@RequestBody OnvifDevice onvifDevice) {
//        return success(onvifDeviceService.auth(onvifDevice));
//    }


    @PreAuthorize("@ss.hasPermi('onvif:device:add')")
    @Log(title = "onvif 设备", businessType = BusinessType.INSERT)
    @PostMapping("/addOnvif")
    public AjaxResult addOnvif(@RequestBody WSDeviceBo bo) {
        return success(onvifDeviceService.addOnvif(bo));
    }

    /**
     * 修改onvif 设备
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:edit')")
    @Log(title = "onvif 设备", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody OnvifDevice onvifDevice) {
        return toAjax(onvifDeviceService.updateOnvifDevice(onvifDevice));
    }

    /**
     * 删除onvif 设备
     */
    @PreAuthorize("@ss.hasPermi('onvif:device:remove')")
    @Log(title = "onvif 设备", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        int rows = onvifDeviceService.deleteOnvifDeviceByIds(ids);
        if (rows > 0) classificationService.removeDeviceRelations("ONVIF", Arrays.stream(ids).map(String::valueOf).collect(Collectors.toList()));
        return toAjax(rows);
    }
}
