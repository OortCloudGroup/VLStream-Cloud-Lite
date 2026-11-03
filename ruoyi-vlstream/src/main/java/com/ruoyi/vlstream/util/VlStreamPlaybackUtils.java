package com.ruoyi.vlstream.util;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Locale;

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
            int marker = path.toLowerCase(Locale.ROOT).lastIndexOf(CAMERA_RTC_PATH);
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
            if (uri.getRawUserInfo() != null || uri.getRawFragment() != null) return null;
            // Check the unnormalised path, before either browser or URI can interpret escapes or dot segments.
            String path = StringUtils.defaultString(uri.getRawPath());
            if (path.indexOf('\\') >= 0) return null;
            String[] segments = path.split("/");
            String[] decodedSegments = StringUtils.defaultString(uri.getPath()).split("/", -1);
            String[] rawSegments = path.split("/", -1);
            if (decodedSegments.length != rawSegments.length) return null;
            for (int i = 0; i < rawSegments.length; i++) {
                String decoded = decodedSegments[i];
                if (".".equals(decoded) || "..".equals(decoded) || decoded.indexOf('\\') >= 0
                        || decoded.indexOf('%') >= 0 || decoded.indexOf('\ufffd') >= 0
                        || (!rawSegments[i].equalsIgnoreCase(decoded) && "videocall".equalsIgnoreCase(decoded))) return null;
            }
            String target = null;
            int markers = 0;
            for (int i = 0; i < segments.length; i++) {
                if (".".equals(segments[i]) || "..".equals(segments[i])) return null;
                if ("videocall".equalsIgnoreCase(segments[i])) {
                    markers++;
                    int next = i + 1;
                    while (next < segments.length && segments[next].isEmpty()) next++;
                    if (next < segments.length) target = segments[next];
                }
            }
            return markers == 1 && target != null && target.matches("[A-Za-z0-9_-]{1,100}") ? target : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
