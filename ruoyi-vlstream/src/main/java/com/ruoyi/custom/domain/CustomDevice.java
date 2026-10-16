package com.ruoyi.custom.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

public class CustomDevice extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    @Excel(name = "设备名称")
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称不能超过100个字符")
    private String deviceName;
    @Excel(name = "设备ID")
    @NotBlank(message = "设备ID不能为空")
    @Size(max = 100, message = "设备ID不能超过100个字符")
    private String deviceCode;
    @Excel(name = "视频流路径")
    @NotBlank(message = "视频流路径不能为空")
    @Size(max = 1000, message = "视频流路径不能超过1000个字符")
    private String streamUrl;
    @Excel(name = "设备类型")
    private String deviceType;
    @Excel(name = "状态")
    private String status;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String configuredStatus;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private CustomDeviceState availability;
    @Excel(name = "经度")
    private BigDecimal longitude;
    @Excel(name = "纬度")
    private BigDecimal latitude;
    @Excel(name = "地址")
    private String address;
    private String categoryType;
    private Long categoryId;
    private Boolean unclassified;
    private String regionName;
    private String groupName;
    private String tagNames;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfiguredStatus() { return configuredStatus; }
    public void setConfiguredStatus(String value) { configuredStatus = value; }
    public CustomDeviceState getAvailability() { return availability; }
    public void setAvailability(CustomDeviceState value) { availability = value; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCategoryType() { return categoryType; }
    public void setCategoryType(String categoryType) { this.categoryType = categoryType; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Boolean getUnclassified() { return unclassified; }
    public void setUnclassified(Boolean unclassified) { this.unclassified = unclassified; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getTagNames() { return tagNames; }
    public void setTagNames(String tagNames) { this.tagNames = tagNames; }
}
