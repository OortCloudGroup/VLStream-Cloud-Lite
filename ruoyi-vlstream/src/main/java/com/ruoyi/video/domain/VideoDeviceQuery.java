package com.ruoyi.video.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class VideoDeviceQuery extends BaseEntity {
    private String protocol;
    private String tenantId;
    private String defaultTenantId;

    public String getProtocol() { return protocol; }
    public void setProtocol(String value) { this.protocol = value; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { this.tenantId = value; }
    public String getDefaultTenantId() { return defaultTenantId; }
    public void setDefaultTenantId(String value) { this.defaultTenantId = value; }
}

