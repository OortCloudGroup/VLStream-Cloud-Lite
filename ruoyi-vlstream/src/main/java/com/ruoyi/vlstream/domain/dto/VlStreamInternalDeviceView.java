package com.ruoyi.vlstream.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Minimal WVP-owned VLStream device data exposed to the VLS backend. */
public class VlStreamInternalDeviceView {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String deviceId;
    private String tenantId;
    private String deviceName;
    private String deviceSerial;
    private String deviceModel;
    private String ipAddr;
    private Boolean online;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceSerial() { return deviceSerial; }
    public void setDeviceSerial(String deviceSerial) { this.deviceSerial = deviceSerial; }
    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    public String getIpAddr() { return ipAddr; }
    public void setIpAddr(String ipAddr) { this.ipAddr = ipAddr; }
    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }
}
