package com.ruoyi.vlstream.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.vlstream.service.VlStreamFirmwareDeploymentService;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VlStreamDeviceControllerTest {
    @Test
    public void streamDetailsReturnReportedSourceWithoutProxySecret() throws Exception {
        VlStreamDeviceStreamMapper mapper = mock(VlStreamDeviceStreamMapper.class);
        VlStreamDeviceStream stream = new VlStreamDeviceStream();
        stream.setId(20L);
        stream.setDeviceRowId(10L);
        stream.setSourceUrl("http://camera.example/video/device-1");
        stream.setZlmProxyKey("test-proxy-secret");
        when(mapper.selectAvailableByDeviceId(10L)).thenReturn(java.util.Collections.singletonList(stream));
        VlStreamDeviceController controller = new VlStreamDeviceController(mock(VlStreamDeviceMapper.class), mapper,
                mock(IMediaServerService.class), mock(VlStreamFirmwareDeploymentService.class));
        java.util.List<?> rows = (java.util.List<?>) controller.streams(10L).get(AjaxResult.DATA_TAG);
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(stream.getSourceUrl(), row.get("sourceUrl"));
        assertEquals("20", row.get("id"));
        assertEquals(false, row.containsKey("zlmProxyKey"));
        org.springframework.security.access.prepost.PreAuthorize permission = VlStreamDeviceController.class
                .getMethod("streams", Long.class).getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        assertEquals("@ss.hasPermi('vlstream:device:list')", permission.value());
    }

    @Test
    public void returnsCameraRtcUrlWithoutCreatingZlmProxy() {
        VlStreamDeviceMapper deviceMapper = mock(VlStreamDeviceMapper.class);
        VlStreamDeviceStreamMapper streamMapper = mock(VlStreamDeviceStreamMapper.class);
        IMediaServerService mediaServerService = mock(IMediaServerService.class);
        VlStreamDevice device = new VlStreamDevice();
        device.setId(10L);
        VlStreamDeviceStream stream = new VlStreamDeviceStream();
        stream.setId(20L);
        stream.setDeviceRowId(10L);
        stream.setProtocol("http");
        stream.setSourceUrl("http://146.56.220.167:8082/videocall/AETY-00-XOKU-L1A2-00000002");
        stream.setAvailable(true);
        when(deviceMapper.selectById(10L)).thenReturn(device);
        when(streamMapper.selectById(20L)).thenReturn(stream);
        VlStreamDeviceController controller = new VlStreamDeviceController(deviceMapper, streamMapper,
                mediaServerService, mock(VlStreamFirmwareDeploymentService.class));
        VlStreamDeviceController.PreviewRequest request = new VlStreamDeviceController.PreviewRequest();
        request.setStreamId(20L);

        AjaxResult result = controller.preview(10L, request);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        Map<?, ?> data = (Map<?, ?>) result.get(AjaxResult.DATA_TAG);
        assertEquals("cameraRTC", data.get("playMode"));
        assertEquals(stream.getSourceUrl(), data.get("url"));
        verify(mediaServerService, never()).getDefaultMediaServer();
    }
}
