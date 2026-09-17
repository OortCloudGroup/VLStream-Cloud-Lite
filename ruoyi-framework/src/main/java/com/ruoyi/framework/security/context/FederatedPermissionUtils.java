package com.ruoyi.framework.security.context;

import java.util.Locale;

/**
 * Permission boundaries for the stateless VLStream federation.
 *
 * Federated requests are allowed to use the protocol device domains only. The
 * WVP system, monitor, and tool domains continue to use the normal permission checks.
 */
public final class FederatedPermissionUtils
{
    private FederatedPermissionUtils()
    {
    }

    public static boolean isProtocolPermission(String permission)
    {
        if (permission == null)
        {
            return false;
        }

        String normalized = permission.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("isup:")
                || normalized.startsWith("ehome:")
                || normalized.startsWith("rtsp:")
                || normalized.startsWith("onvif:")
                || normalized.startsWith("dahua:")
                || normalized.startsWith("wvp:")
                || normalized.startsWith("gb:")
                || normalized.startsWith("vlstream:")
                || normalized.startsWith("custom:");
    }
}
