package com.ruoyi.video.domain;


/** Read-only, credential-free device source owned by its protocol directory. */
public class VideoDeviceSource {
    private String protocol;
    private String rowId;
    private String deviceId;
    private String serial;
    private String deviceName;
    private String ipAddress;
    private String state;
    private String latitude;
    private String longitude;
    private String deviceType;
    private String address;

    public String getProtocol() { return protocol; }
    public void setProtocol(String value) { this.protocol = value; }
    public String getRowId() { return rowId; }
    public void setRowId(String value) { this.rowId = value; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value) { this.deviceId = value; }
    public String getSerial() { return serial; }
    public void setSerial(String value) { this.serial = value; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String value) { this.deviceName = value; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String value) { this.ipAddress = value; }
    public String getState() { return state; }
    public void setState(String value) { this.state = value; }
    public String getLatitude() { return latitude; }
    public void setLatitude(String value) { this.latitude = value; }
    public String getLongitude() { return longitude; }
    public void setLongitude(String value) { this.longitude = value; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String value) { this.deviceType = value; }
    public String getAddress() { return address; }
    public void setAddress(String value) { this.address = value; }
}

