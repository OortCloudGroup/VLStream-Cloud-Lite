package com.ruoyi.framework.security.context;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class FederatedMenuPermissionsTest {
    private Set<String> grants(String... values) { return new HashSet<>(Arrays.asList(values)); }

    @Test public void pagePermissionsAuthorizeOnlyExplicitKnownOperations() {
        Set<String> rtsp = grants("vls:menu:rtsp:rtspDevice");
        assertTrue(FederatedMenuPermissions.allows(rtsp, "rtsp:RtspDevice:list"));
        assertTrue(FederatedMenuPermissions.allows(rtsp, "rtsp:RtspDevice:edit"));
        assertFalse(FederatedMenuPermissions.allows(rtsp, "onvif:device:list"));
        assertFalse(FederatedMenuPermissions.allows(rtsp, "rtsp:RtspDevice:futureDangerousOperation"));
        assertFalse(FederatedMenuPermissions.allows(Collections.emptySet(), "rtsp:RtspDevice:list"));
    }

    @Test public void adminWildcardDoesNotGrantSharing() {
        Set<String> admin = grants("*:*:*");
        assertTrue(FederatedMenuPermissions.allows(admin, "onvif:device:list"));
        assertFalse(FederatedMenuPermissions.allows(admin, "vlstream:device:share:approve"));
        assertTrue(FederatedMenuPermissions.allows(admin, "vlstream:firmware:deploy"));
        assertTrue(FederatedMenuPermissions.allows(grants("vlstream:device:share:approve"), "vlstream:device:share:approve"));
    }

    @Test public void firmwareActionsFollowTheirActualPageAndAreRevokedWithIt() {
        Set<String> catalogue = grants("vls:firmware:list");
        Set<String> device = grants("vls:menu:vlstream:device");
        for (String action : Arrays.asList("list", "upload", "download", "remove")) {
            String permission = "vlstream:firmware:" + action;
            assertTrue(FederatedMenuPermissions.allows(catalogue, permission));
            assertFalse(FederatedMenuPermissions.allows(device, permission));
            assertFalse(FederatedMenuPermissions.allows(Collections.emptySet(), permission));
        }
        assertFalse(FederatedMenuPermissions.allows(catalogue, "vlstream:firmware:deploy"));
        assertTrue(FederatedMenuPermissions.allows(device, "vlstream:firmware:deploy"));
        assertFalse(FederatedMenuPermissions.allows(grants("vls:firmware:deploy"), "vlstream:firmware:deploy"));
        assertFalse(FederatedMenuPermissions.allows(grants("vls:firmware:upload"), "vlstream:firmware:upload"));
        assertFalse(FederatedMenuPermissions.allows(grants(FederatedMenuPermissions.SQUARE), "vlstream:firmware:list"));
    }

    @Test public void squareGrantDoesNotBecomeProtocolManagementPermission() {
        Set<String> square = grants(FederatedMenuPermissions.SQUARE);
        assertFalse(FederatedMenuPermissions.allows(square, "rtsp:RtspDevice:list"));
        assertTrue(FederatedMenuPermissions.allowsSquareRequest(square, "rtsp:RtspDevice:list", "GET", "/video/device/list", null));
        assertFalse(FederatedMenuPermissions.allowsSquareRequest(square, "rtsp:RtspDevice:list", "GET", "/rtsp/RtspDevice/list", null));
        assertTrue(FederatedMenuPermissions.allowsSquareRequest(square, "rtsp:RtspDevice:view", "POST", "/rtsp/RtspDevice/12/preview", null));
        assertTrue(FederatedMenuPermissions.allowsSquareRequest(square, "onvif:device:play", "DELETE", "/onvif/device/12/preview/session-1", null));
        assertFalse(FederatedMenuPermissions.allowsSquareRequest(square, "onvif:device:play", "POST", "/onvif/device/12/square-playback", null));
        assertFalse(FederatedMenuPermissions.allowsSquareRequest(square, "vlstream:device:list", "GET", "/vlstream/device/12/streams", null));
        assertFalse(FederatedMenuPermissions.allowsSquareRequest(square, "custom:device:play", "POST", "/custom/device/12/preview", "false"));
        assertTrue(FederatedMenuPermissions.allowsSquareRequest(square, "custom:device:play", "POST", "/custom/device/12/preview", "true"));
        assertFalse(FederatedMenuPermissions.allowsSquareRequest(square, "custom:device:play", "POST", "/custom/device/12/ptz/right", null));
    }

    @Test public void dahuaActionsRequireAnExactVerifiedPermission() {
        Set<String> list = grants("dahua:device:list");
        assertTrue(FederatedMenuPermissions.isTrustedPermission("dahua:device:list"));
        assertTrue(FederatedMenuPermissions.allows(list, "dahua:device:list"));
        assertFalse(FederatedMenuPermissions.allows(list, "dahua:device:remove"));
        assertTrue(FederatedMenuPermissions.allows(grants("dahua:zlmApi:play"), "dahua:zlmApi:play"));
        assertFalse(FederatedMenuPermissions.isTrustedPermission("dahua:device:futureOperation"));
        assertFalse(FederatedMenuPermissions.allows(Collections.emptySet(), "dahua:device:list"));
    }
}
