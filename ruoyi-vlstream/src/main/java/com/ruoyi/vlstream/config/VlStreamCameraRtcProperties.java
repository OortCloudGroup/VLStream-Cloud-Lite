package com.ruoyi.vlstream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-side CameraRTC gateway settings; the public browser URL is never used as a relay target. */
@Component
@ConfigurationProperties(prefix = "video.custom-availability")
public class VlStreamCameraRtcProperties {
    private String cameraRtcSignalUrl = "";

    public String getCameraRtcSignalUrl() { return cameraRtcSignalUrl; }
    public void setCameraRtcSignalUrl(String value) { cameraRtcSignalUrl = value == null ? "" : value.trim(); }
}
