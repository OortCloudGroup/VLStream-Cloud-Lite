package com.ruoyi.web.controller.onvif;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.web.service.ProtocolDeviceAccess;
import org.junit.Test;
import org.junit.After;
import org.junit.Before;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SquareOnvifPlaybackControllerTest {
    @Before public void login() {
        LoginUser user = new LoginUser();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }
    @After public void logout() { SecurityContextHolder.clearContext(); }
    @Test public void returnsSavedPushAndEasyNtsModeAfterScopedAccess() {
        ProtocolDeviceAccess access = mock(ProtocolDeviceAccess.class); OnvifDevice device = new OnvifDevice();
        when(access.onvif(7L)).thenReturn(device);
        SquareOnvifPlaybackController controller = new SquareOnvifPlaybackController(access);
        device.setPlayType("2"); device.setStreamId("fixture-push");
        Map<?, ?> result = (Map<?, ?>) controller.playback(7L).get("data");
        assertEquals("PUSH", result.get("playMode")); assertEquals("fixture-push", result.get("streamId"));
        device.setPlayType("3"); device.setEasyNTSUrl("https://fixture.invalid/live.m3u8");
        result = (Map<?, ?>) controller.playback(7L).get("data");
        assertEquals("DIRECT", result.get("playMode")); assertEquals(device.getEasyNTSUrl(), result.get("url"));
        verify(access, times(2)).onvif(7L);
    }
    @Test public void deniedSourceAndEmptyModeCannotStartPlayback() {
        ProtocolDeviceAccess access = mock(ProtocolDeviceAccess.class); SquareOnvifPlaybackController controller = new SquareOnvifPlaybackController(access);
        when(access.onvif(7L)).thenThrow(new ServiceException("denied", 403));
        assertThrows(ServiceException.class, () -> controller.playback(7L));
        OnvifDevice device = new OnvifDevice(); when(access.onvif(8L)).thenReturn(device);
        for (String mode : new String[]{"1", "2", "3", null}) { device.setPlayType(mode); assertThrows(ServiceException.class, () -> controller.playback(8L)); }
    }
    @Test public void keepsNativePlayPermission() throws Exception {
        assertEquals("@ss.hasPermi('onvif:device:play') or @ss.canUseFederatedVideoSquare()", SquareOnvifPlaybackController.class.getMethod("playback", Long.class)
            .getAnnotation(PreAuthorize.class).value());
    }
    @Test public void squareOnlyDirectPlaybackPreservesOriginalAddressAfterScopedAccess() {
        LoginUser user = new LoginUser(); user.setFederated(true);
        user.setPermissions(Collections.singleton("vls:menu:video-square"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
        ProtocolDeviceAccess access = mock(ProtocolDeviceAccess.class); OnvifDevice device = new OnvifDevice();
        device.setPlayType("3"); when(access.onvif(7L)).thenReturn(device);
        SquareOnvifPlaybackController controller = new SquareOnvifPlaybackController(access);
        for (String url : new String[] {"https://fixture.invalid/live.m3u8?token=fixture#part",
                "rtsp://user:fixture@fixture.invalid/live"}) {
            device.setEasyNTSUrl(url);
            Map<?, ?> result = (Map<?, ?>) controller.playback(7L).get("data");
            assertEquals("DIRECT", result.get("playMode")); assertEquals(url, result.get("url"));
        }
        verify(access, times(2)).onvif(7L);
    }
}
