package com.ruoyi.wvp.media.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RtspPreviewSessionServiceTest {
    private IMediaServerService media;
    private RtspPreviewSessionService service;
    private MediaServer server;

    @Before
    public void setup() {
        media = mock(IMediaServerService.class);
        service = new RtspPreviewSessionService(media);
        server = new MediaServer();
        server.setId("local");
        server.setStatus(true);
        when(media.getDefaultMediaServer()).thenReturn(server);
        when(media.getOne("local")).thenReturn(server);
        when(media.addStreamProxy(eq(server), eq("rtsp_preview"), anyString(), anyString(), eq(true), eq(false), eq("0"), eq(15)))
                .thenReturn(WVPResult.success("key-one"), WVPResult.success("key-two"));
        when(media.getStreamInfoByAppAndStream(eq(server), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(new StreamInfo());
        when(media.delStreamProxy(eq(server), anyString())).thenReturn(true);
        when(media.isStreamReady(eq(server), anyString(), anyString())).thenReturn(true);
    }

    private String start() {
        return (String) service.start(7L, 1L, "rtsp://camera/Streaming/Channels/101").get("sessionId");
    }

    @Test
    public void failedPullReportsSafeCauseWithoutStartingSession() {
        when(media.addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt()))
                .thenReturn(WVPResult.fail(100, StreamProxyFailure.AUTHENTICATION.getMessage()));
        ServiceException error = assertThrows(ServiceException.class, this::start);
        assertEquals(StreamProxyFailure.AUTHENTICATION.getMessage(), error.getMessage());
        verify(media, never()).isStreamReady(any(), anyString(), anyString());
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(service, "sessions")).isEmpty());
    }

    @Test
    public void unrecognizedPullFailureNeverExposesRawMessage() {
        when(media.addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt()))
                .thenReturn(WVPResult.fail(100, "rtsp://fixture:fixture-secret@camera/profile"));
        assertEquals(StreamProxyFailure.UNKNOWN.getMessage(), assertThrows(ServiceException.class, this::start).getMessage());
    }

    @Test
    public void viewersShareUpstreamButHaveIndependentLeasesAndCleanup() {
        String first = start();
        String second = start();
        assertNotEquals(first, second);
        service.stop(7L, 1L, first);
        verify(media, never()).delStreamProxy(any(), anyString());
        service.renew(7L, 1L, second);
        service.stop(7L, 1L, second);
        verify(media).delStreamProxy(server, "key-one");
        verify(media, times(1)).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
    }

    @Test
    public void differentUsersShareSourceButCannotReleaseEachOthersLease() {
        String first = start();
        String second = (String) service.start(7L, 2L, "rtsp://camera/Streaming/Channels/101").get("sessionId");
        assertThrows(ServiceException.class, () -> service.stop(7L, 2L, first));
        service.stop(7L, 1L, first);
        verify(media, never()).delStreamProxy(any(), anyString());
        service.renew(7L, 2L, second);
        service.stop(7L, 2L, second);
        verify(media).delStreamProxy(server, "key-one");
    }

    @Test
    public void differentDeviceProtocolChannelOrCredentialsDoNotShare() {
        start();
        service.start(8L, 1L, "rtsp://camera/Streaming/Channels/101");
        service.start("ONVIF", 7L, 1L, "rtsp://camera/Streaming/Channels/101");
        service.start(7L, 1L, "rtsp://camera/Streaming/Channels/102");
        service.start(7L, 1L, "rtsp://user:changed@camera/Streaming/Channels/101");
        verify(media, times(5)).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
    }

    @Test
    public void expiredViewerDoesNotStopRemainingViewer() {
        String first = start(), second = start();
        ReflectionTestUtils.setField(session(first), "expiresAt", System.currentTimeMillis() - 1);
        service.expireSessions();
        assertNull(session(first));
        verify(media, never()).delStreamProxy(any(), anyString());
        service.renew(7L, 1L, second);
        service.stop(7L, 1L, second);
        verify(media).delStreamProxy(server, "key-one");
    }

    @Test
    public void concurrentCreationPullsOnlyOnce() throws Exception {
        CountDownLatch pulling = new CountDownLatch(1), finishPull = new CountDownLatch(1);
        when(media.addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt()))
                .thenAnswer(call -> {
                    pulling.countDown();
                    assertTrue(finishPull.await(5, TimeUnit.SECONDS));
                    return WVPResult.success("shared-key");
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(this::start);
            assertTrue(pulling.await(5, TimeUnit.SECONDS));
            Future<String> second = executor.submit(this::start);
            finishPull.countDown();
            String one = first.get(5, TimeUnit.SECONDS), two = second.get(5, TimeUnit.SECONDS);
            assertNotEquals(one, two);
            verify(media, times(1)).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
            service.stop(7L, 1L, one);
            verify(media, never()).delStreamProxy(any(), anyString());
            service.stop(7L, 1L, two);
            verify(media).delStreamProxy(server, "shared-key");
        } finally { finishPull.countDown(); executor.shutdownNow(); }
    }

    @Test
    public void failedJoinDoesNotDeleteExistingViewersStream() {
        String first = start();
        when(media.getStreamInfoByAppAndStream(any(), anyString(), anyString(), isNull(), isNull())).thenReturn(null);
        assertThrows(ServiceException.class, this::start);
        verify(media, never()).delStreamProxy(any(), anyString());
        service.renew(7L, 1L, first);
        service.stop(7L, 1L, first);
        verify(media).delStreamProxy(server, "key-one");
    }

    @Test
    public void interruptedSourceDoesNotOpenAnotherConnectionWhileViewersRemain() {
        String first = start();
        when(media.isStreamReady(any(), anyString(), anyString())).thenReturn(false);
        assertThrows(ServiceException.class, this::start);
        verify(media, times(1)).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
        verify(media, never()).delStreamProxy(any(), anyString());
        service.stop(7L, 1L, first);
    }

    @Test
    public void failedLastDeletionBlocksDuplicatePullAndCanRecover() {
        String first = start(), second = start();
        when(media.delStreamProxy(server, "key-one")).thenReturn(false, false, true);
        service.stop(7L, 1L, first);
        service.stop(7L, 1L, second);
        assertThrows(ServiceException.class, this::start);
        verify(media, times(1)).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
        String third = start();
        service.expireSessions();
        assertNull(session(second));
        service.renew(7L, 1L, third);
        service.stop(7L, 1L, third);
        verify(media).delStreamProxy(server, "key-two");
    }

    @Test
    public void failedInitialReadinessIsNotReusedAndKeysDoNotContainCredentials() {
        when(media.getStreamInfoByAppAndStream(any(), anyString(), anyString(), isNull(), isNull())).thenReturn(null);
        when(media.delStreamProxy(server, "key-one")).thenReturn(false, true);
        assertThrows(ServiceException.class, () -> service.start(7L, 1L, "rtsp://user:secret@camera/profile"));
        Map<?, ?> upstreams = (Map<?, ?>) ReflectionTestUtils.getField(service, "upstreams");
        assertEquals(1, upstreams.size());
        assertFalse(upstreams.keySet().toString().contains("secret"));
        assertFalse(upstreams.keySet().toString().contains("camera"));
        service.expireSessions();
        assertTrue(upstreams.isEmpty());
    }

    @Test
    public void stopIsIdempotent() {
        String id = start();
        service.stop(7L, 1L, id);
        service.stop(7L, 1L, id);
        verify(media, times(1)).delStreamProxy(server, "key-one");
    }

    @Test
    public void otherUserAndOtherDeviceCannotStopPreview() {
        String id = start();
        assertThrows(ServiceException.class, () -> service.stop(7L, 2L, id));
        assertThrows(ServiceException.class, () -> service.stop(8L, 1L, id));
        verify(media, never()).delStreamProxy(any(), anyString());
    }

    @Test
    public void failedCleanupCanBeRetried() {
        String id = start();
        when(media.delStreamProxy(server, "key-one")).thenReturn(false, true);
        service.stop(7L, 1L, id);
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.stop(7L, 1L, id);
        service.stop(7L, 1L, id);
        verify(media, times(2)).delStreamProxy(server, "key-one");
    }

    @Test
    public void missingStreamInfoReleasesCreatedProxy() {
        when(media.getStreamInfoByAppAndStream(eq(server), anyString(), anyString(), isNull(), isNull())).thenReturn(null);
        assertThrows(ServiceException.class, this::start);
        verify(media).delStreamProxy(server, "key-one");
    }

    @Test
    public void invalidAddressAndOfflineMediaServerNeverPull() {
        assertThrows(ServiceException.class, () -> service.start(7L, 1L, "http://camera"));
        server.setStatus(false);
        assertThrows(ServiceException.class, this::start);
        verify(media, never()).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
    }

    @Test
    public void waitsForMediaBeforeReturningPlaybackAddress() {
        when(media.isStreamReady(eq(server), anyString(), anyString())).thenReturn(false, true);
        start();
        verify(media, times(2)).isStreamReady(eq(server), anyString(), anyString());
    }

    @Test
    public void cleanupFailureAfterStartFailureIsRetriedBySweeper() {
        when(media.getStreamInfoByAppAndStream(eq(server), anyString(), anyString(), isNull(), isNull())).thenReturn(null);
        when(media.delStreamProxy(server, "key-one")).thenReturn(false, true);
        assertThrows(ServiceException.class, this::start);
        service.expireSessions();
        verify(media, times(2)).delStreamProxy(server, "key-one");
    }
    @Test
    public void onvifAndRtspSessionsCannotBeCrossedEvenForSameDeviceAndUser() {
        String id = (String) service.start("ONVIF", 7L, 1L, "rtsp://camera/profile").get("sessionId");
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        assertThrows(ServiceException.class, () -> service.stop(7L, 1L, id));
        verify(media, never()).delStreamProxy(any(), anyString());
        service.renew("ONVIF", 7L, 1L, id);
        service.stop("ONVIF", 7L, 1L, id);
        verify(media).delStreamProxy(server, "key-one");
    }

    private Object session(String id) {
        return ((Map<?, ?>) ReflectionTestUtils.getField(service, "sessions")).get(id);
    }

    @Test
    public void failedStopIsTerminalAndSweeperRetriesBeforeOriginalLeaseDeadline() {
        String id = start();
        when(media.delStreamProxy(server, "key-one")).thenReturn(false, true);
        service.stop(7L, 1L, id);
        assertNotNull(session(id));
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        assertEquals(0L, ReflectionTestUtils.getField(session(id), "expiresAt"));
        service.expireSessions();
        assertNull(session(id));
        service.expireSessions();
        verify(media, times(2)).delStreamProxy(server, "key-one");
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
    }

    @Test
    public void onvifExpiredLeaseCannotRenewBeforeSweepOrAfterFailedCleanup() {
        String id = (String) service.start("ONVIF", 7L, 1L, "rtsp://camera/profile").get("sessionId");
        ReflectionTestUtils.setField(session(id), "expiresAt", System.currentTimeMillis() - 1);
        when(media.delStreamProxy(server, "key-one")).thenReturn(false, true);
        assertThrows(ServiceException.class, () -> service.renew("ONVIF", 7L, 1L, id));
        verify(media, never()).delStreamProxy(any(), anyString());
        service.expireSessions();
        verify(media).delStreamProxy(server, "key-one");
        assertThrows(ServiceException.class, () -> service.renew("ONVIF", 7L, 1L, id));
        service.expireSessions();
        assertNull(session(id));
        service.expireSessions();
        verify(media, times(2)).delStreamProxy(server, "key-one");
    }

    @Test
    public void activeLeaseCanRenewButForeignOwnersCannotChangeItsState() {
        String id = start();
        long before = System.currentTimeMillis() + 5_000;
        ReflectionTestUtils.setField(session(id), "expiresAt", before);
        assertThrows(ServiceException.class, () -> service.renew("ONVIF", 7L, 1L, id));
        assertThrows(ServiceException.class, () -> service.renew(8L, 1L, id));
        assertThrows(ServiceException.class, () -> service.renew(7L, 2L, id));
        assertEquals(before, ReflectionTestUtils.getField(session(id), "expiresAt"));
        assertEquals(false, ReflectionTestUtils.getField(session(id), "terminating"));
        service.renew(7L, 1L, id);
        assertTrue((Long) ReflectionTestUtils.getField(session(id), "expiresAt") > System.currentTimeMillis() + 100_000);
        verify(media, never()).delStreamProxy(any(), anyString());
    }

    @Test
    public void cleanupExceptionLeavesTerminalSessionForRetry() {
        String id = start();
        when(media.delStreamProxy(server, "key-one"))
                .thenThrow(new IllegalStateException("fixture deletion failure")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.stop(7L, 1L, id));
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.expireSessions();
        assertNull(session(id));
        verify(media, times(2)).delStreamProxy(server, "key-one");
    }

    @Test
    public void shutdownFailedDeletionCannotBeUndoneByRenew() {
        String id = start();
        when(media.delStreamProxy(server, "key-one")).thenReturn(false, true);
        service.close();
        assertThrows(ServiceException.class, () -> service.renew(7L, 1L, id));
        service.expireSessions();
        assertNull(session(id));
        verify(media, times(2)).delStreamProxy(server, "key-one");
    }

    private void awaitBlocked(AtomicReference<Thread> worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = worker.get();
            if (thread != null && thread.getState() == Thread.State.BLOCKED) return;
            Thread.sleep(1);
        }
        fail("Second operation did not wait on the session monitor");
    }

    @Test
    public void inFlightOnvifRenewCannotReviveStopWhoseDeletionIsPending() throws Exception {
        String id = (String) service.start("ONVIF", 7L, 1L, "rtsp://camera/profile").get("sessionId");
        CountDownLatch deleting = new CountDownLatch(1), allowDelete = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Thread> renewing = new AtomicReference<>();
        when(media.delStreamProxy(server, "key-one")).thenAnswer(call -> {
            if (attempts.incrementAndGet() == 1) {
                deleting.countDown();
                assertTrue(allowDelete.await(5, TimeUnit.SECONDS));
                return false;
            }
            return true;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> stop = executor.submit(() -> service.stop("ONVIF", 7L, 1L, id));
            assertTrue(deleting.await(5, TimeUnit.SECONDS));
            Future<Boolean> renew = executor.submit(() -> {
                renewing.set(Thread.currentThread());
                try { service.renew("ONVIF", 7L, 1L, id); return true; }
                catch (ServiceException expected) { return false; }
            });
            awaitBlocked(renewing);
            allowDelete.countDown();
            stop.get(5, TimeUnit.SECONDS);
            assertFalse(renew.get(5, TimeUnit.SECONDS));
            service.expireSessions();
            assertEquals(2, attempts.get());
            assertNull(session(id));
        } finally {
            allowDelete.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentStopsCannotDeleteProxyAgainAfterSuccessfulRemoval() throws Exception {
        String id = start();
        CountDownLatch deleting = new CountDownLatch(1), allowDelete = new CountDownLatch(1);
        AtomicReference<Thread> secondStopping = new AtomicReference<>();
        when(media.delStreamProxy(server, "key-one")).thenAnswer(call -> {
            deleting.countDown();
            assertTrue(allowDelete.await(5, TimeUnit.SECONDS));
            return true;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.stop(7L, 1L, id));
            assertTrue(deleting.await(5, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                secondStopping.set(Thread.currentThread());
                service.stop(7L, 1L, id);
            });
            awaitBlocked(secondStopping);
            allowDelete.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            verify(media).delStreamProxy(server, "key-one");
            assertNull(session(id));
        } finally {
            allowDelete.countDown();
            executor.shutdownNow();
        }
    }
}
