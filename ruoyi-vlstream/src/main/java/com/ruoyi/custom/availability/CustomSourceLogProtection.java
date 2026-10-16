package com.ruoyi.custom.availability;

import javax.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** MyBatis DEBUG/TRACE bound values can contain source-URL credentials; custom SQL remains private. */
@Component
public class CustomSourceLogProtection {
    @PostConstruct public void protectSourceParameters() {
        for(String name:new String[]{"com.ruoyi.custom.mapper.CustomDeviceMapper","com.ruoyi.wvp.media.zlm.ZLMRESTfulUtils"}){
            org.slf4j.Logger logger=LoggerFactory.getLogger(name);
            if(logger instanceof ch.qos.logback.classic.Logger)
                ((ch.qos.logback.classic.Logger)logger).setLevel(ch.qos.logback.classic.Level.INFO);
        }
    }
}
