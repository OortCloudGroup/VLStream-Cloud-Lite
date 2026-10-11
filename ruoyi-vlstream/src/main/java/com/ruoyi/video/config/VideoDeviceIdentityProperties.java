package com.ruoyi.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

/** Operator-verified physical device links. Never bound from a device-list HTTP request. */
@Component
@ConfigurationProperties(prefix = "video.device-catalog")
public class VideoDeviceIdentityProperties {
    private Map<String, List<String>> identityGroups = new LinkedHashMap<>();

    public Map<String, List<String>> getIdentityGroups() { return identityGroups; }
    public void setIdentityGroups(Map<String, List<String>> value) { identityGroups = value; }

    /** Empty by default: do not invent cross-protocol identity from an ID, serial or IP. */
    public Map<String, String> sourceIdentities() {
        Map<String, String> links = new LinkedHashMap<>();
        if (identityGroups == null) return links;
        for (Map.Entry<String, List<String>> group : identityGroups.entrySet()) {
            String identity = group.getKey() == null ? "" : group.getKey().trim();
            if (identity.isEmpty() || group.getValue() == null) {
                throw new IllegalArgumentException("Invalid video device identity group");
            }
            for (String member : group.getValue()) {
                String source = member == null ? "" : member.trim();
                if (!source.matches("(VLSTREAM|ISUP|EHOME|RTSP|ONVIF|GB28181|DAHUA|CUSTOM):[0-9]+")) {
                    throw new IllegalArgumentException("Invalid video device identity source");
                }
                String previous = links.putIfAbsent(source, identity);
                if (previous != null && !previous.equals(identity)) {
                    throw new IllegalArgumentException("Video device source belongs to conflicting identity groups");
                }
            }
        }
        return links;
    }
}
