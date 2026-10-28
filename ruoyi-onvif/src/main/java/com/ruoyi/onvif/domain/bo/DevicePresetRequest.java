package com.ruoyi.onvif.domain.bo;

/** Public preset input deliberately contains no device credentials or profile override. */
public class DevicePresetRequest {
    private String name;
    private String token;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
