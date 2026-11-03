package com.ruoyi.framework.web.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the actual permission service, including its federated protocol compatibility branch. */
public class PermissionServiceSharingTest {
    private final PermissionService permissions = new PermissionService();

    @Before public void requestContext() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @After public void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test public void federatedEmptyPermissionsCannotManageOrApproveShares() {
        login(true);
        assertFalse(permissions.hasPermi("vlstream:device:share:manage"));
        assertFalse(permissions.hasPermi("vlstream:device:share:approve"));
    }

    @Test public void federatedEmptyPermissionsCannotPassAnySharingPower() {
        login(true);
        assertFalse(permissions.hasAnyPermi("vlstream:device:share:manage,vlstream:device:share:approve"));
        assertFalse(permissions.hasAnyPermi("  vlstream:device:share:approve  "));
        assertFalse(permissions.hasPermi(" VLSTREAM:DEVICE:SHARE:MANAGE "));
    }

    @Test public void explicitTrustedSharingPermissionsRemainGranularForBothIdentityKinds() {
        for (boolean federated : new boolean[]{false, true}) {
            login(federated, "vlstream:device:share:approve");
            assertTrue(permissions.hasPermi("vlstream:device:share:approve"));
            assertTrue(permissions.hasAnyPermi("vlstream:device:share:manage,vlstream:device:share:approve"));
            assertFalse(permissions.hasPermi("vlstream:device:share:manage"));
        }
    }

    @Test public void federatedProtocolAccessRequiresItsVerifiedPage() {
        login(true, "vls:menu:vlstream:device");
        assertTrue(permissions.hasPermi("vlstream:device:play"));
        assertFalse(permissions.hasPermi("rtsp:device:play"));
        assertTrue(permissions.hasAnyPermi("vlstream:device:share:approve,vlstream:device:list"));
        assertFalse(permissions.hasPermi("system:user:list"));
    }

    @Test public void nativeEmptyAndUnrelatedPermissionsDoNotGainSharingPowers() {
        login(false);
        assertFalse(permissions.hasPermi("vlstream:device:share:approve"));
        assertFalse(permissions.hasAnyPermi("vlstream:device:share:manage,vlstream:device:share:approve"));
        login(true, "vlstream:device:list", "rtsp:device:play");
        assertFalse(permissions.hasPermi("vlstream:device:share:approve"));
        assertFalse(permissions.hasAnyPermi("vlstream:device:share:manage,vlstream:device:share:approve"));
    }

    @Test public void nativeFirmwarePermissionStillRequiresItsOwnAction() {
        login(false, "vlstream:firmware:list");
        assertTrue(permissions.hasPermi("vlstream:firmware:list"));
        assertFalse(permissions.hasPermi("vlstream:firmware:upload"));
        login(false, "vls:firmware:list");
        assertFalse(permissions.hasPermi("vlstream:firmware:list"));
    }

    @Test public void trustedWildcardPermissionSemanticsRemainUnchanged() {
        login(true, "vlstream:device:share:*");
        assertFalse(permissions.hasPermi("vlstream:device:share:manage"));
        assertFalse(permissions.hasPermi("vlstream:device:share:approve"));
        login(false, "*:*:*");
        assertTrue(permissions.hasPermi("vlstream:device:share:manage"));
        assertTrue(permissions.hasAnyPermi("vlstream:device:share:approve"));
    }

    @Test public void squareOnlyGrantsOnlyExactCatalogAndPreviewRequests() {
        login(true, "vls:menu:video-square");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/video/device/list");
        req.setServletPath("/video/device/list");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        assertTrue(permissions.hasPermi("rtsp:RtspDevice:list"));
        req.setServletPath("/rtsp/RtspDevice/list");
        assertFalse(permissions.hasPermi("rtsp:RtspDevice:list"));
        req.setServletPath("/vlstream/device/7/streams");
        assertFalse(permissions.hasPermi("vlstream:device:list"));
        req.setMethod("POST"); req.setServletPath("/rtsp/RtspDevice/7/preview");
        assertTrue(permissions.hasPermi("rtsp:RtspDevice:view"));
        req.setServletPath("/custom/device/7/preview");
        assertFalse(permissions.hasPermi("custom:device:play"));
    }

    @Test public void legacyProtocolsDenyFederatedTenantOutsideConfiguredDefault() {
        login(true, "vls:menu:gbmanger:device");
        assertFalse(permissions.hasPermi("wvp:device:edit"));
        LoginUser user = SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof LoginUser
            ? (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal() : null;
        user.setTenantId("000000");
        assertTrue(permissions.hasPermi("wvp:device:edit"));
    }

    private void login(boolean federated, String... granted) {
        LoginUser user = new LoginUser();
        user.setUserId(100L);
        user.setTenantId("owner-fixture");
        user.setFederated(federated);
        user.setPermissions(new HashSet<>(Arrays.asList(granted)));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }
}
