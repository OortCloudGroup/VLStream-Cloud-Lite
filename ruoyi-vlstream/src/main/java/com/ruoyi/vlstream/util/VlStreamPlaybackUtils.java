package com.ruoyi.vlstream.util;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

public final class VlStreamPlaybackUtils {
    private static final String CAMERA_RTC_PATH = "/videocall/";

    private VlStreamPlaybackUtils() {
    }

    public static boolean isCameraRtc(String protocol, String sourceUrl) {
        if (!("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                || StringUtils.isBlank(sourceUrl)) {
            return false;
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            if (!protocol.equalsIgnoreCase(uri.getScheme()) || StringUtils.isBlank(uri.getHost())) {
                return false;
            }
            String path = StringUtils.defaultString(uri.getPath());
            int marker = path.toLowerCase().lastIndexOf(CAMERA_RTC_PATH);
            return marker >= 0 && path.length() > marker + CAMERA_RTC_PATH.length();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** Extracts only the opaque target identifier from the saved CameraRTC path. */
    public static String cameraRtcDeviceId(String protocol, String sourceUrl) {
        if (!isCameraRtc(protocol, sourceUrl)) return null;
        try {
            URI uri = URI.create(sourceUrl.trim());
            // Use the raw path so percent-encoded separators cannot smuggle an extra target segment.
            String[] segments = StringUtils.defaultString(uri.getRawPath()).split("/");
            for (int i = segments.length - 2; i >= 0; i--) {
                if ("videocall".equalsIgnoreCase(segments[i])) {
                    String target = segments[i + 1];
                    return target.matches("[A-Za-z0-9_-]{1,100}") ? target : null;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }
}
