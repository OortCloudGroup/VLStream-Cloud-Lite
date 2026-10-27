package com.ruoyi.rtsp.tool;

import com.ruoyi.rtsp.constants.StreamTypeConstants;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.common.exception.ServiceException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

/**
 * @Description: rtsp工具类
 * @Author: 陈江灿
 * @CreateTime: 2025-04-22
 */
public class RtspTool {


    /**
     * 生成rtsp地址
     * @return
     */
    public static String generateRtspUrl(RtspDevice rtspDevice) {
        if (rtspDevice == null) throw invalidAddress();
        String username = encode(rtspDevice.getUserName());
        String password = encode(rtspDevice.getPassword());
        String channel = encode(rtspDevice.getChannel());
        String firm = rtspDevice.getFirm();
        String path;
        if (StreamTypeConstants.HIKVISION.equals(firm)) {
            path = "/Streaming/Channels/" + channel;
        } else if (StreamTypeConstants.DAHUA.equals(firm)) {
            path = "/cam/realmonitor?channel=" + channel + "&subtype=0";
        } else if (StreamTypeConstants.UNIVIEW.equals(firm)) {
            path = "/media/video" + channel;
        } else if (StreamTypeConstants.MERCURY.equals(firm)) {
            path = "/stream" + channel;
        } else {
            return null;
        }
        return "rtsp://" + username + ":" + password + "@"
                + normalizeHostPort(rtspDevice.getIp(), rtspDevice.getPort()) + path;
    }

    /** No DNS lookup; bracket bare IPv6 and append the default port only once. */
    public static String normalizeHostPort(String input, Integer separatePort) {
        if (input == null || input.trim().isEmpty()) throw invalidAddress();
        String address = input.trim();
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)
                    || "/\\?#@".indexOf(c) >= 0) throw invalidAddress();
        }
        String host;
        Integer embeddedPort = null;
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 0) throw invalidAddress();
            host = address.substring(0, end + 1);
            String tail = address.substring(end + 1);
            if (!tail.isEmpty()) {
                if (!tail.startsWith(":")) throw invalidAddress();
                embeddedPort = parsePort(tail.substring(1));
            }
        } else {
            int first = address.indexOf(':');
            if (first >= 0 && first != address.lastIndexOf(':')) {
                // Bare IPv6 is a host; do not guess that its last segment is a port.
                host = "[" + address + "]";
            } else if (first >= 0) {
                host = address.substring(0, first);
                embeddedPort = parsePort(address.substring(first + 1));
            } else {
                host = address;
            }
        }
        if (separatePort != null && (separatePort < 1 || separatePort > 65535)) throw invalidAddress();
        if (embeddedPort != null && separatePort != null && !embeddedPort.equals(separatePort)) throw invalidAddress();
        int port = separatePort != null ? separatePort : embeddedPort != null ? embeddedPort : 554;
        String authority = host + ":" + port;
        try {
            URI uri = new URI("rtsp://" + authority);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() != port
                    || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw invalidAddress();
            }
        } catch (URISyntaxException error) {
            throw invalidAddress();
        }
        return authority;
    }

    private static Integer parsePort(String port) {
        if (!port.matches("[0-9]{1,5}")) throw invalidAddress();
        int value = Integer.parseInt(port);
        if (value < 1 || value > 65535) throw invalidAddress();
        return value;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(String.valueOf(value), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 unavailable");
        }
    }

    private static ServiceException invalidAddress() {
        return new ServiceException("RTSP地址无效：请填写主机或主机:端口，端口范围为1–65535");
    }
}
