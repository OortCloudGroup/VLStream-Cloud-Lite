package com.ruoyi.vlstream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime limits for explicitly approved, read-only device shares. */
@Component
@ConfigurationProperties(prefix = "vlstream.device-access")
public class VlStreamDeviceAccessProperties {
    private int maxGrantDays = 30;
    private int defaultGrantDays = 7;
    private long previewLeaseSeconds = 120L;
    private int cameraRtcTicketTtlSeconds = 30;

    public int getMaxGrantDays() { return maxGrantDays; }
    public void setMaxGrantDays(int value) { maxGrantDays = value; }
    public int getDefaultGrantDays() { return defaultGrantDays; }
    public void setDefaultGrantDays(int value) { defaultGrantDays = value; }
    public long getPreviewLeaseSeconds() { return previewLeaseSeconds; }
    public void setPreviewLeaseSeconds(long value) { previewLeaseSeconds = value; }
    public int getCameraRtcTicketTtlSeconds() { return cameraRtcTicketTtlSeconds; }
    public void setCameraRtcTicketTtlSeconds(int value) { cameraRtcTicketTtlSeconds = value; }
}
