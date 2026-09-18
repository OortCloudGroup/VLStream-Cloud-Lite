package com.ruoyi.isup.ehome;

import com.ruoyi.isup.config.IsupLinuxConfig;
import com.ruoyi.isup.runner.HaikangCommandLineRunnerImpl;
import com.ruoyi.isup.service.cmsService.CMS;
import com.ruoyi.isup.service.smsService.SMS;
import com.ruoyi.isup.linux64.service.CmsService;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.mockito.Mockito.*;

public class EhomeLinuxStartupTest {
    @Test public void linuxEhomeStartsTheUnifiedStreamAndCmsOnly() throws Exception {
        String os = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            HaikangCommandLineRunnerImpl runner = new HaikangCommandLineRunnerImpl();
            CMS cms = mock(CMS.class); SMS sms = mock(SMS.class); CmsService legacy = mock(CmsService.class);
            EhomeConfig config = new EhomeConfig(); config.setLinuxEnabled(true);
            IsupLinuxConfig old = new IsupLinuxConfig(); old.setEnabled(true);
            set(runner,"cms",cms); set(runner,"sms",sms); set(runner,"cmsService",legacy);
            set(runner,"ehomeConfig",config); set(runner,"isupLinuxConfig",old);
            runner.run();
            org.mockito.InOrder order = inOrder(sms,cms);
            order.verify(sms).SMS_Init(); order.verify(cms).cMS_Init(); order.verify(cms).startCmsListen();
            verifyNoInteractions(legacy);
        } finally { System.setProperty("os.name", os); }
    }
    private void set(Object target, String name, Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
}
