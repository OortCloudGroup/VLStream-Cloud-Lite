package com.ruoyi.web.controller.onvif;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.onvif.domain.OnvifJavaPZT;
import com.ruoyi.onvif.domain.bo.OnvifPZT;
import com.ruoyi.onvif.service.IOnvifJavaService;
import com.ruoyi.onvif.service.IOnvifService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * onvif-java 控制器
 *
 * @author 陈江灿
 */
@RestController
@RequestMapping("/onvifJava")
public class OnvifJavaServiceController extends BaseController {

    @Autowired
    private IOnvifJavaService service;

    @PreAuthorize("isAuthenticated() and @ss.hasPermi('onvif:service:continuousMove')")
    @GetMapping("/ptz")
    public AjaxResult onvifPZTStart(OnvifJavaPZT onvifJavaPZT) {
        if (SecurityUtils.getLoginUser().isFederated()) {
            throw new ServiceException("请使用已授权设备的ID控制接口", 403);
        }
        service.ptz(onvifJavaPZT);
        return success();
    }
}
