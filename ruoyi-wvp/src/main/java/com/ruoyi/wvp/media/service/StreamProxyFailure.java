package com.ruoyi.wvp.media.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Fixed public messages: ZLM errors can contain source URLs and credentials. */
public enum StreamProxyFailure {
    AUTHENTICATION("摄像机RTSP认证失败（401），请检查设备账号、密码和RTSP访问权限"),
    FORBIDDEN("摄像机拒绝RTSP访问（403），请检查账号的取流权限"),
    SOURCE_CAPACITY("摄像机取流资源不足（453），请减少同时取流连接或降低码率后重试"),
    SOURCE_NOT_FOUND("摄像机未找到指定码流（404），请检查通道和播放地址"),
    SOURCE_ERROR("摄像机建立取流会话失败（500），请检查设备取流状态后重试"),
    UNKNOWN("ZLM拉流失败，请检查设备地址、账号和网络");

    private final String message;

    StreamProxyFailure(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public static StreamProxyFailure fromZlmMessage(String message) {
        if (message == null) return UNKNOWN;
        String normalized = message.toUpperCase(Locale.ROOT);
        // Match an upstream RTSP response, not a ZLM API authentication failure.
        if (Pattern.compile("\\b(?:DESCRIBE|SETUP|PLAY)\\s*:\\s*401\\b").matcher(normalized).find()) {
            return AUTHENTICATION;
        }
        if (Pattern.compile("\\b(?:DESCRIBE|SETUP|PLAY)\\s*:\\s*403\\b").matcher(normalized).find()) {
            return FORBIDDEN;
        }
        if (Pattern.compile("\\b(?:DESCRIBE|SETUP|PLAY)\\s*:\\s*453\\b").matcher(normalized).find()) {
            return SOURCE_CAPACITY;
        }
        if (Pattern.compile("\\b(?:DESCRIBE|SETUP|PLAY)\\s*:\\s*404\\b").matcher(normalized).find()) {
            return SOURCE_NOT_FOUND;
        }
        if (Pattern.compile("\\b(?:DESCRIBE|SETUP|PLAY)\\s*:\\s*500\\b").matcher(normalized).find()) {
            return SOURCE_ERROR;
        }
        return UNKNOWN;
    }

    public static String publicMessage(String message) {
        for (StreamProxyFailure failure : values()) {
            if (failure.message.equals(message)) return failure.message;
        }
        return UNKNOWN.message;
    }
}
