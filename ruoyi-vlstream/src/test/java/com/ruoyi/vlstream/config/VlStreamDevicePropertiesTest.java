package com.ruoyi.vlstream.config;

import org.junit.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.assertEquals;

public class VlStreamDevicePropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    public void missingModeKeepsSingleTenantCompatibility() {
        contextRunner.run(context -> assertEquals("000000",
            context.getBean(VlStreamDeviceProperties.class).getEffectiveDefaultTenantId()));
    }

    @Test
    public void vlsModePropertyAndSeparateMultiDefaultBindInSpring() {
        contextRunner.withPropertyValues("token.tenantType=multi",
            "vlstream.device.default-tenant-id=single-owner",
            "vlstream.device.multi-tenant-default-tenant-id=multi-owner")
            .run(context -> assertEquals("multi-owner",
                context.getBean(VlStreamDeviceProperties.class).getEffectiveDefaultTenantId()));
        contextRunner.withPropertyValues("token.tenant-type=multi")
            .run(context -> assertEquals("0e391fd7-1033-4f09-88c0-187582fee462",
                context.getBean(VlStreamDeviceProperties.class).getEffectiveDefaultTenantId()));
    }

    @Test
    public void localOrSsoAuthenticationDoesNotDecideTheBusinessTenantMode() {
        contextRunner.withPropertyValues("token.tenantType=multi", "wvp.auth.mode=local")
            .run(context -> assertEquals("0e391fd7-1033-4f09-88c0-187582fee462",
                context.getBean(VlStreamDeviceProperties.class).getEffectiveDefaultTenantId()));
        contextRunner.withPropertyValues("token.tenantType=single", "wvp.auth.mode=sso")
            .run(context -> assertEquals("000000",
                context.getBean(VlStreamDeviceProperties.class).getEffectiveDefaultTenantId()));
    }

    @Configuration
    @EnableConfigurationProperties(VlStreamDeviceProperties.class)
    static class PropertiesConfiguration { }
}
