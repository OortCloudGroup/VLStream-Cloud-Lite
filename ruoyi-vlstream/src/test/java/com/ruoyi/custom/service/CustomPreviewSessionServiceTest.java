package com.ruoyi.custom.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.junit.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class CustomPreviewSessionServiceTest {
    @Test public void sourceChangeStopsOnlyItsOwnOldViewerLease(){
        String id=(String)service.start(7L,1L).get("sessionId");device.setStreamUrl("rtsp://fixture.invalid/new-video");
        assertThrows(ServiceException.class,()->service.renew(7L,1L,id));verify(media).delStreamProxy(server,"owned-1");
        assertThrows(ServiceException.class,()->service.renew(7L,1L,id));
    }
    @Test public void sourceChangeCannotResurrectLeaseWhenCloseMustRetry(){
        String id=(String)service.start(7L,1L).get("sessionId");device.setStreamUrl("rtsp://fixture.invalid/new-video");
        when(media.delStreamProxy(server,"owned-1")).thenReturn(false,true);
        assertThrows(ServiceException.class,()->service.renew(7L,1L,id));assertThrows(ServiceException.class,()->service.renew(7L,1L,id));
        service.expireSessions();verify(media,times(2)).delStreamProxy(server,"owned-1");
    }
    private CustomDeviceMapper mapper;
    private IMediaServerService media;
    private MediaServer server;
    private CustomDevice device;
    private CustomPreviewSessionService service;
    private void login(String tenant) {
        LoginUser user = new LoginUser(); user.setTenantId(tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }
    @Before public void setup() {
        login("default"); mapper = mock(CustomDeviceMapper.class); media = mock(IMediaServerService.class);
        device = new CustomDevice(); device.setStreamUrl("rtsp://fixture.invalid/live");
        when(mapper.selectDeviceById(7L)).thenReturn(device);
        server = new MediaServer(); server.setId("fixture"); server.setStatus(true);
        when(media.getDefaultMediaServer()).thenReturn(server); when(media.getOne("fixture")).thenReturn(server);
        AtomicInteger keys = new AtomicInteger();
        when(media.addStreamProxy(eq(server), eq("custom_preview"), anyString(), anyString(), eq(true), eq(false), eq("0"), eq(15)))
            .thenAnswer(call -> WVPResult.success("owned-" + keys.incrementAndGet()));
        when(media.isStreamReady(eq(server), eq("custom_preview"), anyString())).thenReturn(true);
        when(media.getStreamInfoByAppAndStream(eq(server), eq("custom_preview"), anyString(), isNull(), isNull())).thenReturn(new StreamInfo());
        when(media.delStreamProxy(eq(server), anyString())).thenReturn(true);
        service = new CustomPreviewSessionService(mapper, media, "default");
    }
    @After public void clear() { SecurityContextHolder.clearContext(); Thread.interrupted(); }
    @Test public void independentViewerLeasesReleaseOnlyTheirOwnProxy() {
        String first = (String) service.start(7L, 1L).get("sessionId"), second = (String) service.start(7L, 2L).get("sessionId");
        assertNotEquals(first, second);
        assertThrows(ServiceException.class, () -> service.stop(7L, 2L, first));
        service.renew(7L, 1L, first); service.stop(7L, 1L, first);
        verify(media).delStreamProxy(server, "owned-1"); verify(media, never()).delStreamProxy(server, "owned-2");
        service.stop(7L, 2L, second); verify(media).delStreamProxy(server, "owned-2");
    }
    @Test public void rtmpUsesIndependentNonRecordingProxyAndReturnsLease() {
        device.setStreamUrl("rtmp://fixture.invalid/live");
        Map<String, Object> result = service.start(7L, 1L);
        assertEquals("ZLM", result.get("playMode")); assertNotNull(result.get("streamInfo")); assertNotNull(result.get("sessionId"));
        verify(media).addStreamProxy(eq(server), eq("custom_preview"), anyString(), eq(device.getStreamUrl()), eq(true), eq(false), eq("0"), eq(15));
    }
    @Test public void directHttpSourceDoesNotStartAProxyOrLease() {
        device.setStreamUrl("https://fixture.invalid/videocall/fixture-camera");
        Map<String, Object> result = service.start(7L, 1L);
        assertEquals("DIRECT", result.get("playMode")); assertEquals(device.getStreamUrl(), result.get("url"));
        assertFalse(result.containsKey("sessionId")); verifyNoInteractions(media);
    }
    @Test public void foreignTenantIsDeniedBeforeReadingDeviceOrMedia() {
        login("foreign"); clearInvocations(mapper, media);
        assertThrows(ServiceException.class, () -> service.start(7L, 1L)); verifyNoInteractions(mapper, media);
    }
    @Test public void anonymousAndMissingDeviceAreDenied() {
        assertThrows(ServiceException.class, () -> service.start(8L, 1L));
        SecurityContextHolder.clearContext(); assertThrows(ServiceException.class, () -> service.start(7L, 1L));
        verifyNoInteractions(media);
    }
    @Test public void invalidAddressIsRejectedWithoutMediaAccess() {
        for (String url : Arrays.asList(null, "", "file:///fixture.mp4", "http://user:fixture@fixture.invalid/live")) {
            device.setStreamUrl(url); assertThrows(ServiceException.class, () -> service.start(7L, 1L));
        }
        verifyNoInteractions(media);
    }
    @Test public void unavailableServerCannotReturnFabricatedStream() {
        server.setStatus(false); assertThrows(ServiceException.class, () -> service.start(7L, 1L));
        verify(media, never()).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
    }
    @Test public void failedMetadataReleasesCreatedProxy() {
        when(media.getStreamInfoByAppAndStream(eq(server), eq("custom_preview"), anyString(), isNull(), isNull())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.start(7L, 1L)); verify(media).delStreamProxy(server, "owned-1");
    }
    @Test public void cancelledWaitReleasesCreatedProxyAndPreservesInterrupt() {
        when(media.isStreamReady(eq(server), eq("custom_preview"), anyString())).thenReturn(false);
        Thread.currentThread().interrupt();
        assertThrows(ServiceException.class, () -> service.start(7L, 1L)); assertTrue(Thread.currentThread().isInterrupted());
        verify(media).delStreamProxy(server, "owned-1");
    }
    @Test public void failedCloseIsRetriedByExpiry() {
        String id = (String) service.start(7L, 1L).get("sessionId");
        when(media.delStreamProxy(server, "owned-1")).thenReturn(false, true);
        service.stop(7L, 1L, id);
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.expireSessions(); verify(media, times(2)).delStreamProxy(server, "owned-1");
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
    }

    private Object session(String id) {
        return ((Map<?, ?>) ReflectionTestUtils.getField(service, "sessions")).get(id);
    }
    @Test public void expiredLeaseCannotBeRenewedBeforeOrAfterFailedCleanup() {
        String id = (String) service.start(7L, 1L).get("sessionId");
        ReflectionTestUtils.setField(session(id), "expiresAt", System.currentTimeMillis() - 1);
        when(media.delStreamProxy(server, "owned-1")).thenReturn(false, true);
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.expireSessions(); verify(media).delStreamProxy(server, "owned-1");
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.expireSessions(); verify(media, times(2)).delStreamProxy(server, "owned-1");
        service.expireSessions(); verify(media, times(2)).delStreamProxy(server, "owned-1");
    }
    @Test public void normalRenewalExtendsActiveLeaseAndSuccessfulStopIsIdempotent() {
        String id = (String) service.start(7L, 1L).get("sessionId");
        ReflectionTestUtils.setField(session(id), "expiresAt", System.currentTimeMillis() + 5_000);
        service.renew(7L, 1L, id);
        assertTrue((Long) ReflectionTestUtils.getField(session(id), "expiresAt") > System.currentTimeMillis() + 100_000);
        service.stop(7L, 1L, id); service.stop(7L, 1L, id); service.expireSessions();
        verify(media).delStreamProxy(server, "owned-1");
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
    }
    @Test public void repeatedStopRetriesFailedDeleteWithoutReactivatingSession() {
        String id = (String) service.start(7L, 1L).get("sessionId");
        when(media.delStreamProxy(server, "owned-1")).thenReturn(false, true);
        service.stop(7L, 1L, id); assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.stop(7L, 1L, id); service.stop(7L, 1L, id);
        verify(media, times(2)).delStreamProxy(server, "owned-1");
    }
    @Test public void deleteExceptionStillLeavesTerminalSessionForCleanupRetry() {
        String id = (String) service.start(7L, 1L).get("sessionId");
        when(media.delStreamProxy(server, "owned-1")).thenThrow(new IllegalStateException("fixture delete failure")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.stop(7L, 1L, id));
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.expireSessions(); verify(media, times(2)).delStreamProxy(server, "owned-1");
    }
    @Test public void shutdownCannotBeUndoneByLateRenewalAfterFailedDelete() {
        String id = (String) service.start(7L, 1L).get("sessionId");
        when(media.delStreamProxy(server, "owned-1")).thenReturn(false, true);
        service.close(); assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.expireSessions(); verify(media, times(2)).delStreamProxy(server, "owned-1");
    }
    @Test public void inFlightRenewCannotReviveStopWhoseDeletionIsStillPending() throws Exception {
        String id = (String) service.start(7L, 1L).get("sessionId");
        CountDownLatch deleting = new CountDownLatch(1), allowDelete = new CountDownLatch(1), renewing = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        when(media.delStreamProxy(server, "owned-1")).thenAnswer(call -> {
            if (attempts.incrementAndGet() == 1) {
                deleting.countDown(); assertTrue(allowDelete.await(5, TimeUnit.SECONDS)); return false;
            }
            return true;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> stop = executor.submit(() -> {
                login("default"); try { service.stop(7L, 1L, id); } finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(deleting.await(5, TimeUnit.SECONDS));
            Future<Boolean> renew = executor.submit(() -> {
                login("default"); renewing.countDown();
                try { service.renew(7L, 1L, id); return true; }
                catch (ServiceException expected) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(renewing.await(5, TimeUnit.SECONDS)); allowDelete.countDown();
            stop.get(5, TimeUnit.SECONDS); assertFalse(renew.get(5, TimeUnit.SECONDS));
            service.expireSessions(); assertEquals(2, attempts.get());
        } finally { allowDelete.countDown(); executor.shutdownNow(); }
    }
}
