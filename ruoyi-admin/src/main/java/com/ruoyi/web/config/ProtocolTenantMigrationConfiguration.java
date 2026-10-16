package com.ruoyi.web.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Only supplies the approved legacy owner for the two protocol-device tables. */
@Configuration(proxyBeanMethods = false)
public class ProtocolTenantMigrationConfiguration {
    private final Environment environment;

    public ProtocolTenantMigrationConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public FlywayConfigurationCustomizer protocolLegacyTenantPlaceholder() {
        String legacyTenant = environment.getProperty("protocol-device.legacy-tenant-id",
                environment.getProperty("vlstream.device.default-tenant-id", "000000"));
        if (legacyTenant == null || !legacyTenant.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalStateException("Invalid protocol-device.legacy-tenant-id");
        }
        return configuration -> {
            Map<String, String> placeholders = new HashMap<>(configuration.getPlaceholders());
            placeholders.put("protocol_legacy_tenant_id", legacyTenant);
            configuration.placeholders(placeholders);
        };
    }
}
