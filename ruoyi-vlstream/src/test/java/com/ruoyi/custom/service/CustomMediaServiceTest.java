package com.ruoyi.custom.service;

import org.junit.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomMediaServiceTest {
    private CustomMediaService service;
    private IMediaServerService media;
    private MediaServer server;

    private void setupPreview() {
        CustomDeviceMapper mapper = mock(CustomDeviceMapper.class);
        media = mock(IMediaServerService.class);
        service = new CustomMediaService(mapper, media);
        CustomDevice device = new CustomDevice();
        device.setStreamUrl("rtsp://camera/live");
        when(mapper.selectDeviceById(2L)).thenReturn(device);
        server = new MediaServer(); server.setId("local"); server.setStatus(true);
        when(media.getDefaultMediaServer()).thenReturn(server);
        when(media.getOne("local")).thenReturn(server);
        when(media.getStreamInfoByAppAndStream(server, "custom", "device_2", null, null)).thenReturn(new StreamInfo());
        when(media.addStreamProxy(server, "custom", "device_2", "rtsp://camera/live", true, false, "0", 15))
                .thenReturn(WVPResult.success("custom-key"));
    }

    @Test
    public void formattedUrlDoesNotBypassCreatingMissingStream() {
        setupPreview();
        when(media.isStreamReady(server, "custom", "device_2")).thenReturn(false, true);
        service.preview(2L);
        verify(media).addStreamProxy(server, "custom", "device_2", "rtsp://camera/live", true, false, "0", 15);
    }

    @Test
    public void readyStreamIsReusedWithoutStartingAnotherProxy() {
        setupPreview();
        when(media.isStreamReady(server, "custom", "device_2")).thenReturn(true);
        service.preview(2L);
        verify(media, never()).addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());
    }

    @Test
    public void waitsForMediaBeforeReturningUrl() {
        setupPreview();
        when(media.isStreamReady(server, "custom", "device_2")).thenReturn(false, false, true);
        service.preview(2L);
        verify(media, times(3)).isStreamReady(server, "custom", "device_2");
    }

    @Test
    public void failedPreviewReleasesItsCreatedProxy() {
        setupPreview();
        when(media.isStreamReady(server, "custom", "device_2")).thenReturn(false, true);
        when(media.getStreamInfoByAppAndStream(server, "custom", "device_2", null, null)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.preview(2L));
        verify(media).delStreamProxy(server, "custom-key");
    }

    @Test
    public void acceptsOnlyStreamsThatNeedZlmProxy() {
        assertTrue(CustomMediaService.supportsZlmPreview("rtsp://camera/live"));
        assertTrue(CustomMediaService.supportsZlmPreview(" RTMP://camera/live "));
        assertFalse(CustomMediaService.supportsZlmPreview("http://camera/videocall/AETY-00-NJ"));
        assertFalse(CustomMediaService.supportsZlmPreview(null));
    }
}
