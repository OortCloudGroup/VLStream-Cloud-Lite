package com.ruoyi.video.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.video.service.VideoDeviceService;
import com.ruoyi.video.service.VideoDeviceSharingCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/video/device")
public class VideoDeviceController {
    private final VideoDeviceService service;
    @Autowired
    private VideoDeviceSharingCatalogService sharingCatalog;
    public VideoDeviceController(VideoDeviceService service) { this.service = service; }

    @PreAuthorize("isAuthenticated() and @ss.hasAnyPermi(T(com.ruoyi.video.service.VideoDeviceService).LIST_PERMISSIONS)")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "100") int pageSize,
                           @RequestParam(required = false) String keyword) {
        // The service's scoped readers enforce each original protocol permission through @ss.
        return AjaxResult.success(service.list(pageNum, pageSize, keyword));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/shared-list")
    public AjaxResult sharedList(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "100") int pageSize,
                                 @RequestParam(required = false) String keyword) {
        return AjaxResult.success(sharingCatalog.list(pageNum, pageSize, keyword));
    }
}

