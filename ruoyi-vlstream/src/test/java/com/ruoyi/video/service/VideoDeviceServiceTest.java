package com.ruoyi.video.service;

import com.ruoyi.video.domain.VideoDeviceQuery;
import com.ruoyi.video.config.VideoDeviceIdentityProperties;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VideoDeviceServiceTest {
    @Test public void foreignTenantCannotReadAnyUnownedLegacyDirectory() {
        VideoDeviceScopedReader reader = mock(VideoDeviceScopedReader.class);
        when(reader.vlstream(any())).thenReturn(Collections.emptyList());
        VideoDeviceService service = new VideoDeviceService(reader, mock(VlStreamDeviceTenantService.class), new VideoDeviceIdentityProperties());
        service.list(1, 100, null, "tenant-b", "default");
        ArgumentCaptor<VideoDeviceQuery> query = ArgumentCaptor.forClass(VideoDeviceQuery.class);
        verify(reader).vlstream(query.capture());
        assertEquals("tenant-b", query.getValue().getTenantId());
        assertEquals("default", query.getValue().getDefaultTenantId());
        verifyNoMoreInteractions(reader);
    }

    @Test public void defaultTenantStillNeedsEachProtocolPermission() {
        VideoDeviceScopedReader reader = mock(VideoDeviceScopedReader.class);
        when(reader.onvif(any())).thenReturn(Collections.singletonList(
            VideoDeviceCatalogTest.source("ONVIF", "1", null, null, "192.168.1.11", null)));
        when(reader.vlstream(any())).thenThrow(new AccessDeniedException("denied"));
        when(reader.isup(any())).thenThrow(new AccessDeniedException("denied"));
        when(reader.ehome(any())).thenThrow(new AccessDeniedException("denied"));
        when(reader.rtsp(any())).thenThrow(new AccessDeniedException("denied"));
        when(reader.gb(any())).thenThrow(new AccessDeniedException("denied"));
        when(reader.dahua(any())).thenThrow(new AccessDeniedException("denied"));
        when(reader.custom(any())).thenThrow(new AccessDeniedException("denied"));
        VideoDeviceService service = new VideoDeviceService(reader, mock(VlStreamDeviceTenantService.class), new VideoDeviceIdentityProperties());
        java.util.Map<String, Object> result = service.list(1, 100, null, "default", "default");
        assertEquals(1, result.get("total"));
        assertEquals(Collections.singletonList("ONVIF"), result.get("protocols"));
    }

    @Test public void noDevicePermissionsProducesEmptyCatalog() {
        VideoDeviceScopedReader reader = mock(VideoDeviceScopedReader.class, invocation -> {
            throw new AccessDeniedException("denied");
        });
        VideoDeviceService service = new VideoDeviceService(reader, mock(VlStreamDeviceTenantService.class), new VideoDeviceIdentityProperties());
        assertEquals(0, service.list(1, 100, null, "default", "default").get("total"));
    }

    @Test public void eachDepartmentScopedProtocolUsesItsOwnPermission() throws Exception {
        String[][] scopes = {{"isup", "isup:lsupDevice:list"}, {"ehome", "ehome:device:list"},
            {"rtsp", "rtsp:RtspDevice:list"}, {"onvif", "onvif:device:list"},
            {"gb", "wvp:device:list"}, {"dahua", "dahua:device:list"}};
        for (String[] scope : scopes) {
            com.ruoyi.common.annotation.DataScope annotation = VideoDeviceScopedReader.class
                .getMethod(scope[0], VideoDeviceQuery.class).getAnnotation(com.ruoyi.common.annotation.DataScope.class);
            assertEquals(scope[1], annotation.permission());
            assertEquals("d", annotation.deptAlias());
        }
    }
}

