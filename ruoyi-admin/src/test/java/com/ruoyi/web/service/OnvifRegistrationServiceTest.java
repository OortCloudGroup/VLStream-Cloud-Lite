package com.ruoyi.web.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.WSDeviceBo;
import com.ruoyi.onvif.service.IOnvifDeviceService;
import com.ruoyi.wvp.media.service.RtspPreviewSessionService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OnvifRegistrationServiceTest {
    private final IOnvifDeviceService devices = mock(IOnvifDeviceService.class);
    private final RtspPreviewSessionService previews = mock(RtspPreviewSessionService.class);
    private final OnvifRegistrationService service = new OnvifRegistrationService(devices, previews);
    private static final String URI = "rtsp://fixture:fixture-password@192.0.2.13/profile";

    @Before public void setup() {
        OnvifDevice verified = new OnvifDevice();
        verified.setStreamUris(Collections.singletonMap("profile", URI));
        when(devices.addOnvif(any())).thenReturn(verified);
        when(previews.start("ONVIF", 0L, 7L, URI)).thenReturn(Collections.singletonMap("sessionId", "temporary"));
        when(devices.insertOnvifDevice(any())).thenReturn(1);
    }

    private OnvifDevice device() {
        OnvifDevice device = new OnvifDevice();
        device.setName("Fixture"); device.setIp("192.0.2.13"); device.setUserName("fixture");
        device.setPassword("fixture-password"); device.setChannel("profile"); device.setPlayType("1");
        device.setUrl("rtsp://forged-host/forged-profile");
        return device;
    }

    @Test public void readableOnvifMetadataCannotBypassFailedMediaAuthentication() {
        when(previews.start(anyString(), anyLong(), anyLong(), anyString()))
                .thenThrow(new ServiceException("摄像机RTSP认证失败（401）"));
        assertThrows(ServiceException.class, () -> service.authenticate(new WSDeviceBo(), 7L));
        assertThrows(ServiceException.class, () -> service.register(device(), "2", 7L));
        verify(devices, never()).insertOnvifDevice(any());
    }

    @Test public void authenticationReleasesItsTemporaryStreamAndDoesNotSaveDevice() {
        assertNotNull(service.authenticate(new WSDeviceBo(), 7L));
        verify(previews).stop("ONVIF", 0L, 7L, "temporary");
        verify(devices, never()).insertOnvifDevice(any());
    }

    @Test public void registrationRechecksCurrentCredentialsAndUsesDeviceReturnedUri() {
        OnvifDevice device = device();
        assertEquals(1, service.register(device, "2", 7L));
        verify(devices).addOnvif(argThat(account -> "2".equals(account.getAuth()) &&
                device.getPassword().equals(account.getPassword()) && device.getUserName().equals(account.getUsername())));
        assertEquals(URI, device.getUrl());
        verify(previews).start("ONVIF", 0L, 7L, URI);
        verify(previews).stop("ONVIF", 0L, 7L, "temporary");
        verify(devices).insertOnvifDevice(device);
    }

    @Test public void invalidProfileOrMissingNameNeverWrites() {
        OnvifDevice device = device(); device.setChannel("forged");
        assertThrows(ServiceException.class, () -> service.register(device, "1", 7L));
        device.setName("");
        assertThrows(ServiceException.class, () -> service.register(device, "1", 7L));
        verifyNoInteractions(previews);
        verify(devices, never()).insertOnvifDevice(any());
    }
}
