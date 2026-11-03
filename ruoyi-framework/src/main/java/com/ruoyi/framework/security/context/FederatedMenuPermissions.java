package com.ruoyi.framework.security.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Explicit bridge from trusted VLS grants to existing WVP method permissions. */
public final class FederatedMenuPermissions {
    public static final String SQUARE = "vls:menu:video-square";
    private static final String EVENTS = "active-safety:events:list";
    private static final Map<String, Set<String>> PAGES = new HashMap<>();
    private static final Set<String> SHARING = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "vlstream:device:share:manage", "vlstream:device:share:approve")));
    private static final Set<String> FIRMWARE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "vlstream:firmware:list", "vlstream:firmware:upload", "vlstream:firmware:download",
        "vlstream:firmware:remove", "vlstream:firmware:deploy")));
    private static final Set<String> DAHUA = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "dahua:device:add", "dahua:device:control", "dahua:device:edit", "dahua:device:export",
        "dahua:device:list", "dahua:device:listScreenshot", "dahua:device:login", "dahua:device:ptzCtrl",
        "dahua:device:query", "dahua:device:remove", "dahua:device:removeScreenshot",
        "dahua:device:snapPicture", "dahua:device:stopCapturePicture", "dahua:device:timerCapturePicture",
        "dahua:zlmApi:play")));

    static {
        add("vls:menu:vlstream:device", "vlstream:device:list", "vlstream:device:play", "vlstream:device:edit");
        // Federated firmware operations were historically available from these pages.
        add("vls:firmware:list", "vlstream:firmware:list", "vlstream:firmware:upload",
            "vlstream:firmware:download", "vlstream:firmware:remove");
        add("vls:menu:vlstream:device", "vlstream:firmware:deploy");
        add("vls:menu:isup:isupDevice", "isup:lsupDevice:edit", "isup:lsupDevice:list",
            "isup:lsupDevice:ptzCtrl", "isup:lsupDevice:query", "isup:lsupDevice:remove", "isup:lsupDevice:start");
        add("vls:menu:ehome:device", "ehome:device:edit", "ehome:device:list", "ehome:device:preview", "ehome:device:query");
        add("vls:menu:rtsp:rtspDevice", "rtsp:RtspDevice:AlarmClock", "rtsp:RtspDevice:add", "rtsp:RtspDevice:addDetection",
            "rtsp:RtspDevice:edit", "rtsp:RtspDevice:export", "rtsp:RtspDevice:list", "rtsp:RtspDevice:query",
            "rtsp:RtspDevice:remove", "rtsp:RtspDevice:stopDetection", "rtsp:RtspDevice:view");
        add("vls:menu:onvif:cameraManage", "onvif:device:WSDiscovery", "onvif:device:add", "onvif:device:auth",
            "onvif:device:edit", "onvif:device:export", "onvif:device:list", "onvif:device:play", "onvif:device:query",
            "onvif:device:remove", "onvif:service:addPreset", "onvif:service:continuousMove",
            "onvif:service:continuousMoveStop", "onvif:service:getChannelToken", "onvif:service:getInfo",
            "onvif:service:getPresets", "onvif:service:gotoPreset", "onvif:service:removePreset");
        add("vls:menu:custom:device", "custom:device:add", "custom:device:edit", "custom:device:export",
            "custom:device:list", "custom:device:play", "custom:device:query", "custom:device:record", "custom:device:remove");
        add("vls:menu:gbmanger:device", "wvp:device:channelStreamIdentification", "wvp:device:channels",
            "wvp:device:edit", "wvp:device:list", "wvp:device:query", "wvp:device:remove", "wvp:device:subscribeCatalog",
            "wvp:device:subscribeMobilePosition", "wvp:device:sync", "wvp:device:updateTransport",
            "wvp:control:guardApi", "wvp:control:recordApi", "wvp:config:cdownloadApi", "wvp:play:start", "wvp:play:stop",
            "gb:record:download", "gb:record:query");
        add("vls:menu:gbmanger:thoroughfare", "wvp:channel:addGroupChannel", "wvp:channel:addRegionChannel",
            "wvp:channel:civilcodeList", "wvp:channel:deleteGroupChannel", "wvp:channel:deleteRegionChannel",
            "wvp:channel:edit", "wvp:channel:query");
        add("vls:menu:gbmanger:platform", "wvp:platform:add", "wvp:platform:channelAdd", "wvp:platform:channelList",
            "wvp:platform:channelRemove", "wvp:platform:delete", "wvp:platform:edit", "wvp:platform:list", "wvp:platform:push");
        add("vls:menu:gbmanger:streamPush", "wvp:push:add", "wvp:push:edit", "wvp:push:list", "wvp:push:play", "wvp:push:remove");
        add("vls:menu:gbmanger:streamProxy", "wvp:proxy:add", "wvp:proxy:delete", "wvp:proxy:edit", "wvp:proxy:list", "wvp:proxy:play", "wvp:proxy:stop");
        add("vls:menu:gbmanger:recordPlan", "wvp:record:add", "wvp:record:channelAdd", "wvp:record:channelDelete",
            "wvp:record:channelList", "wvp:record:delete", "wvp:record:edit", "wvp:record:list");
        add("vls:menu:gbmanger:cloudRecord", "wvp:record:list", "wvp:record:play", "wvp:record:download");
        add("vls:menu:gbmanger:administrativeGrouping", "wvp:group:add", "wvp:group:delete", "wvp:group:edit", "wvp:group:list",
            "wvp:region:add", "wvp:region:delete", "wvp:region:edit", "wvp:region:list");
        add("vls:menu:gbmanger:alarm", "wvp:alarm:delete", "wvp:alarm:list");
        add("vls:menu:gbmanger:node", "wvp:server:add", "wvp:server:check", "wvp:server:configInfo",
            "wvp:server:delete", "wvp:server:edit", "wvp:server:list", "wvp:server:query");
        add("vls:menu:video-playback", "gb:playback:start");
    }

    private FederatedMenuPermissions() { }
    private static void add(String page, String... permissions) {
        for (String permission : permissions) PAGES.computeIfAbsent(permission, key -> new HashSet<>()).add(page);
    }

    public static boolean isTrustedPermission(String permission) {
        return "*:*:*".equals(permission) || SQUARE.equals(permission) || EVENTS.equals(permission) || SHARING.contains(permission)
            || DAHUA.contains(permission)
            || PAGES.values().stream().anyMatch(pages -> pages.contains(permission))
            || FIRMWARE.stream().anyMatch(wvp -> ("vls:" + wvp.substring("vlstream:".length())).equals(permission));
    }

    public static boolean allows(Set<String> trusted, String requested) {
        if (trusted == null || requested == null) return false;
        if (SHARING.contains(requested)) return trusted.contains(requested);
        if (DAHUA.contains(requested)) return trusted.contains("*:*:*") || trusted.contains(requested);
        Set<String> pages = PAGES.get(requested);
        return pages != null && (trusted.contains("*:*:*") || pages.stream().anyMatch(trusted::contains));
    }

    /** Square-only grants never become general protocol list/play permissions. */
    public static boolean allowsSquareRequest(Set<String> trusted, String requested, String method, String path, String lease) {
        if (trusted == null || !(trusted.contains(SQUARE) || trusted.contains("*:*:*")) || path == null) return false;
        if ("GET".equals(method) && ("/video/device/list".equals(path)
            || "/device/classification/tree".equals(path) || "/device/classification/device-ids".equals(path))) {
            return Arrays.asList("vlstream:device:list", "isup:lsupDevice:list", "ehome:device:list",
                "rtsp:RtspDevice:list", "onvif:device:list", "wvp:device:list", "dahua:device:list", "custom:device:list")
                .contains(requested);
        }
        if ("rtsp:RtspDevice:view".equals(requested))
            return previewPath(method, path, "/rtsp/RtspDevice/");
        if ("onvif:device:play".equals(requested))
            return previewPath(method, path, "/onvif/device/");
        if ("custom:device:play".equals(requested)) {
            if ("POST".equals(method) && !"true".equalsIgnoreCase(lease)) return false;
            return previewPath(method, path, "/custom/device/");
        }
        return false;
    }

    /** Event location is a bounded read with its own trusted-tenant checks. */
    public static boolean allowsEventLocation(Set<String> trusted, String requested, String method, String path) {
        return trusted != null && (trusted.contains(EVENTS) || trusted.contains("*:*:*"))
            && "vlstream:device:list".equals(requested) && "POST".equals(method)
            && "/internal/vlstream/device/locations".equals(path);
    }

    private static boolean previewPath(String method, String path, String prefix) {
        String base = java.util.regex.Pattern.quote(prefix) + "[1-9][0-9]*/preview";
        if ("POST".equals(method)) return path.matches(base);
        return ("PUT".equals(method) || "DELETE".equals(method))
            && path.matches(base + "/[A-Za-z0-9_-]{1,128}");
    }
}
