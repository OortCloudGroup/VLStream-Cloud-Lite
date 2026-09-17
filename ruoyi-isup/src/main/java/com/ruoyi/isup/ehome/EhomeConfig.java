package com.ruoyi.isup.ehome;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ehome")
public class EhomeConfig {
    private boolean enabled = true;
    @org.springframework.beans.factory.annotation.Value("${ehome.public-host:${isup.IP:127.0.0.1}}")
    private String publicHost;
    private Long defaultDeptId = 100L;
}
