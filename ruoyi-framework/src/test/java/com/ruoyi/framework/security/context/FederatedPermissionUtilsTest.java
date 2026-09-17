package com.ruoyi.framework.security.context;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FederatedPermissionUtilsTest
{
    @Test
    public void allowsVlstreamAndCustomDevicePermissions()
    {
        assertTrue(FederatedPermissionUtils.isProtocolPermission("vlstream:device:list"));
        assertTrue(FederatedPermissionUtils.isProtocolPermission("custom:device:add"));
        assertTrue(FederatedPermissionUtils.isProtocolPermission("ehome:device:list"));
        assertTrue(FederatedPermissionUtils.isProtocolPermission("ehome:device:preview"));
    }

    @Test
    public void keepsSystemPermissionsOutsideFederationBoundary()
    {
        assertFalse(FederatedPermissionUtils.isProtocolPermission("system:user:list"));
        assertFalse(FederatedPermissionUtils.isProtocolPermission("monitor:server:list"));
        assertFalse(FederatedPermissionUtils.isProtocolPermission(null));
    }
}
