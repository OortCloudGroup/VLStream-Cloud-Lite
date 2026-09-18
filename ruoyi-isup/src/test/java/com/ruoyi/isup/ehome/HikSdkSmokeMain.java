package com.ruoyi.isup.ehome;

import com.ruoyi.isup.config.IsupConfig;
import com.ruoyi.isup.mapper.EhomeDeviceMapper;
import com.ruoyi.isup.service.cmsService.CMS;
import com.ruoyi.isup.service.smsService.SMS;
import java.lang.reflect.Field;
import java.net.Socket;

/** Explicit native integration harness, never run by ordinary unit tests. No database or real device. */
public final class HikSdkSmokeMain {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "with-ffmpeg".equals(args[0])) {
            org.bytedeco.ffmpeg.global.avutil.av_version_info();
            System.out.println("FFmpeg loaded before Hikvision SDK");
        }
        IsupConfig config = new IsupConfig();
        config.setIp("127.0.0.1"); config.setCmsServerPort(18760); config.setSmsServerPort(18765);
        CMS cms = new CMS(); SMS sms = new SMS();
        set(cms, "isupConfig", config); set(sms, "isupConfig", config);
        EhomeDeviceMapper mapper = (EhomeDeviceMapper) java.lang.reflect.Proxy.newProxyInstance(
                EhomeDeviceMapper.class.getClassLoader(), new Class[]{EhomeDeviceMapper.class},
                (proxy, method, values) -> method.getReturnType() == int.class ? 0 : null);
        set(cms, "ehomeRegistration", new EhomeRegistration(mapper, new EhomeConfig()));
        try {
            sms.SMS_Init(); cms.cMS_Init(); cms.startCmsListen();
            System.out.println("CMS_VERSION=" + HikSdkLibraries.version(CMS.hCEhomeCMS.NET_ECMS_GetBuildVersion()));
            System.out.println("STREAM_VERSION=" + HikSdkLibraries.version(SMS.hcISUPSMS.NET_ESTREAM_GetBuildVersion()));
            try (Socket a = new Socket("127.0.0.1", 18760); Socket b = new Socket("127.0.0.1", 18765)) {
                System.out.println("CMS_TCP=true STREAM_TCP=true");
            }
        } finally { cms.stopCms(); sms.stopSms(); }
        if (CMS.CmsHandle != -1 || SMS.listenHandle != -1) throw new AssertionError("Handles not cleared");
        System.out.println("SDK_SMOKE_OK");
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
