package com.ruoyi.wvp.media.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.junit.Before;
import org.junit.Test;
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
    public void viewersHaveIndependentStreamsAndCleanup() {
        String first = start();
        String second = start();
        assertNotEquals(first, second);
        service.stop(7L, 1L, first);
        verify(media).delStreamProxy(server, "key-one");
        verify(media, never()).delStreamProxy(server, "key-two");
        service.renew(7L, 1L, second);
        service.stop(7L, 1L, second);
        verify(media).delStreamProxy(server, "key-two");
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
}
