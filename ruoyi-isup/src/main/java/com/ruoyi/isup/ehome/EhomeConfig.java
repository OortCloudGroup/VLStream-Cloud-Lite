package com.ruoyi.isup.ehome;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ehome")
public class EhomeConfig {
    private boolean enabled = true;
    /** Native Linux runtime starts by default; unsupported architectures fail before loading. */
    private boolean linuxEnabled = true;
    /** Optional NAT/public address. Empty means choose the local source address per device route. */
    private String publicHost = "";
    private Long defaultDeptId = 100L;
}
