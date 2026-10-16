package com.ruoyi.custom.domain;

import java.util.Date;

/** Internal scoped projection; no credentials, capabilities or model data are selected. */
public class CustomTargetHeartbeat {
    private Boolean online;
    private String onlineReason;
    private Date lastHeartbeatTime;
    private Date updateTime;
    public Boolean getOnline() { return online; }
    public void setOnline(Boolean value) { online = value; }
    public String getOnlineReason() { return onlineReason; }
    public void setOnlineReason(String value) { onlineReason = value; }
    public Date getLastHeartbeatTime() { return lastHeartbeatTime; }
    public void setLastHeartbeatTime(Date value) { lastHeartbeatTime = value; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date value) { updateTime = value; }
}
