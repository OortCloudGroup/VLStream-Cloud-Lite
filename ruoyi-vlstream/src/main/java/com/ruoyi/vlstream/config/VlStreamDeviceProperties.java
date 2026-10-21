package com.ruoyi.vlstream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.ruoyi.common.exception.ServiceException;

@Component
@ConfigurationProperties(prefix = "vlstream.device")
public class VlStreamDeviceProperties {
    private long offlineTimeoutSeconds = 180L;
    private long messageRetentionHours = 24L;
    private String defaultTenantId = "000000";
    private String multiTenantDefaultTenantId = "0e391fd7-1033-4f09-88c0-187582fee462";
    // Same business mode as VLS; WVP's local/SSO authentication mode is independent.
    @Value("${token.tenant-type:${token.tenantType:${SSO_TENANT_TYPE:single}}}")
    private String tenantType = "single";
    private String tenantBindingSecret = "";

    public String getDefaultTenantId() { return defaultTenantId; }
    public void setDefaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId; }
    public String getMultiTenantDefaultTenantId() { return multiTenantDefaultTenantId; }
    public void setMultiTenantDefaultTenantId(String tenantId) { this.multiTenantDefaultTenantId = tenantId; }
    public String getTenantType() { return tenantType; }
    public void setTenantType(String tenantType) { this.tenantType = tenantType; }

    public String getEffectiveDefaultTenantId() {
        String mode = tenantType == null ? "" : tenantType.trim();
        boolean multi = "multi".equalsIgnoreCase(mode);
        if (!multi && !"single".equalsIgnoreCase(mode)) {
            throw new ServiceException("VLStream设备租户模式配置无效", 503);
        }
        String configured = multi ? multiTenantDefaultTenantId : defaultTenantId;
        String tenant = configured == null ? "" : configured.trim();
        if (!tenant.matches("[A-Za-z0-9_-]{1,64}") || (multi && "000000".equals(tenant))) {
            throw new ServiceException("VLStream设备默认租户配置无效", 503);
        }
        return tenant;
    }
    public String getTenantBindingSecret() { return tenantBindingSecret; }
    public void setTenantBindingSecret(String tenantBindingSecret) { this.tenantBindingSecret = tenantBindingSecret; }

    public long getOfflineTimeoutSeconds() { return offlineTimeoutSeconds; }
    public void setOfflineTimeoutSeconds(long offlineTimeoutSeconds) { this.offlineTimeoutSeconds = offlineTimeoutSeconds; }
    public long getMessageRetentionHours() { return messageRetentionHours; }
    public void setMessageRetentionHours(long messageRetentionHours) { this.messageRetentionHours = messageRetentionHours; }
}
