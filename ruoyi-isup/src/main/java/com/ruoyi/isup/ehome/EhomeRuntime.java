package com.ruoyi.isup.ehome;

import com.ruoyi.isup.service.cmsService.CMS;
import com.ruoyi.isup.service.smsService.SMS;
import org.springframework.stereotype.Component;

/** The vendor SDK exposes one CMS listener; callbacks dispatch legacy EHome independently. */
@Component
public class EhomeRuntime {
    private final EhomeConfig config;
    public EhomeRuntime(EhomeConfig config) { this.config = config; }
    public boolean ready() { return config.isEnabled() && CMS.hCEhomeCMS != null && CMS.CmsHandle >= 0; }
    public boolean streamReady() { return ready() && SMS.hcISUPSMS != null && SMS.listenHandle >= 0; }
    public String message() {
        if (!config.isEnabled()) return "EHome 接入服务已关闭";
        if (!ready()) return "海康注册服务未启动，请检查监听地址和 SDK 运行环境";
        if (!streamReady()) return "注册服务已就绪，取流服务未启动";
        return "注册与取流服务就绪";
    }
}
