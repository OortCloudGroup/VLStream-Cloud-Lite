package com.ruoyi.isup.runner;

import com.ruoyi.isup.common.osSelect;
import com.ruoyi.isup.linux64.service.AlarmService;
import com.ruoyi.isup.linux64.service.CmsService;
import com.ruoyi.isup.linux64.service.SsService;
import com.ruoyi.isup.config.IsupLinuxConfig;
import com.ruoyi.isup.service.AlarmService.Alarm;
import com.ruoyi.isup.service.cmsService.CMS;
import com.ruoyi.isup.service.smsService.SMS;
import com.ruoyi.isup.service.ssService.Ss;
import com.ruoyi.isup.service.streamService.HCNet;
import com.ruoyi.isup.service.streamService.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 开启海康自动监听
 *
 * @FileName HaikangCommandLineRunnerImpl
 * @Description
 * @Author fengcheng
 * @date 2025-06-07
 **/
@Component
@Slf4j
public class HaikangCommandLineRunnerImpl implements CommandLineRunner {

    @Autowired
    private CMS cms;

    @Autowired
    private SMS sms;

    @Autowired
    private Ss ss;

    @Autowired
    private Alarm alarm;

    @Autowired
    private Stream stream;

    @Autowired
    private HCNet hcNet;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private CmsService cmsService;

    @Autowired
    private SsService ssService;

    @Autowired
    private IsupLinuxConfig isupLinuxConfig;

    @Autowired
    private com.ruoyi.isup.ehome.EhomeConfig ehomeConfig;

    @Override
    public void run(String... args) throws Exception {
        if (osSelect.isLinux() && ehomeConfig.isEnabled() && ehomeConfig.isLinuxEnabled()) {
            // Reuse the same CMS/Stream adapter and version dispatch as Windows.
            // Do not also start the historical Linux CMS listener on the same port.
            sms.SMS_Init();
            cms.cMS_Init();
            cms.startCmsListen();
        } else if(osSelect.isLinux() && isupLinuxConfig.isEnabled()){
            //初始化报警服务
            alarmService.init();
            alarmService.startAlarmListen();
            //初始化存储服务
            ssService.init();
            ssService.startSsListen();
            //初始化注册服务
            cmsService.init();
            cmsService.startCmsListen();
        } else if (osSelect.isWindows()) {
            //初始化报警服务
//            alarm.eAlarm_Init();
//            alarm.startAlarmListen();
            //初始化存储服务
            ss.eSS_Init();
            ss.startSsListen();
            //初始化流媒体服务(需要预览取流时使用)
//        stream.eStream_Init();
            //初始化海康SDK
//        hcNet.hCNetSDK_Init();
            //初始化海康SDK
            sms.SMS_Init();
            //初始化注册服务
            cms.cMS_Init();
            cms.startCmsListen();
        } else if (osSelect.isLinux()) {
            log.warn("Linux 海康原生服务未启用；EHome 基础接入设置 ehome.linux-enabled=true，历史报警/存储分支设置 isup-linux64.enabled=true");
        }


    }

}
