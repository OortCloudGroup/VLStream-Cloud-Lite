package com.ruoyi.custom.domain;

import java.util.Date;

/** Credential-free, read-only evidence for the explicitly configured playback target. */
public class CustomDeviceState {
    private String sourceRevision;
    public String getSourceRevision(){return sourceRevision;}
    public void setSourceRevision(String value){sourceRevision=value;}
    private String state;
    private String source;
    private String reason;
    private String targetDeviceId;
    private long checkedAt;
    private Long expiresAt;
    private Date lastHeartbeatTime;
    private Date sourceUpdateTime;
    private String detailState;
    private String deviceState, deviceReason;
    public String getDeviceState() { return deviceState; }
    public void setDeviceState(String value) { deviceState = value; }
    public String getDeviceReason() { return deviceReason; }
    public void setDeviceReason(String value) { deviceReason = value; }
    private long observedAt;
    private long lastAvailableAt;
    private int failureCount, successCount;
    public String getDetailState() { return detailState; }
    public void setDetailState(String value) { detailState = value; }
    public long getObservedAt() { return observedAt; }
    public void setObservedAt(long value) { observedAt = value; }
    public long getLastAvailableAt() { return lastAvailableAt; }
    public void setLastAvailableAt(long value) { lastAvailableAt = value; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int value) { failureCount = value; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int value) { successCount = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getTargetDeviceId() { return targetDeviceId; }
    public void setTargetDeviceId(String value) { targetDeviceId = value; }
    public long getCheckedAt() { return checkedAt; }
    public void setCheckedAt(long value) { checkedAt = value; }
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long value) { expiresAt = value; }
    public Date getLastHeartbeatTime() { return lastHeartbeatTime; }
    public void setLastHeartbeatTime(Date value) { lastHeartbeatTime = value; }
    public Date getSourceUpdateTime() { return sourceUpdateTime; }
    public void setSourceUpdateTime(Date value) { sourceUpdateTime = value; }
}
