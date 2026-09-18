package com.ruoyi.isup.ehome;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ehome")
public class EhomeConfig {
    private boolean enabled = true;
    /** Explicit opt-in for native Linux runtime; unsupported architectures fail before loading. */
    private boolean linuxEnabled = false;
    /** Optional NAT/public address. Empty means choose the local source address per device route. */
    private String publicHost = "";
    private Long defaultDeptId = 100L;
}
