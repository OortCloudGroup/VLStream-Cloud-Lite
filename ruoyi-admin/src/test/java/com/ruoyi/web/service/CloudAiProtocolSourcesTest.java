package com.ruoyi.web.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.isup.ehome.*;
import com.ruoyi.isup.mapper.EhomeDeviceMapper;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.video.service.VideoDeviceScopedReader;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import com.ruoyi.wvp.gb28181.bean.DeviceChannel;
import com.ruoyi.wvp.gb28181.service.*;
import com.ruoyi.wvp.streamPush.service.*;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CloudAiProtocolSourcesTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VideoDeviceScopedReader reader = mock(VideoDeviceScopedReader.class);
    private final VlStreamDeviceTenantService tenants = mock(VlStreamDeviceTenantService.class);
    private final IDeviceChannelService channels = mock(IDeviceChannelService.class);
    private final IPlayService gbPlay = mock(IPlayService.class);
    private final CloudAiNativeStreams nativeStreams = mock(CloudAiNativeStreams.class);
    private final CloudAiMediaSessions leases = mock(CloudAiMediaSessions.class);
    private final EhomeDeviceMapper ehomeDevices = mock(EhomeDeviceMapper.class);
    private final EhomeSdk ehomeSdk = mock(EhomeSdk.class);
    private CloudAiProtocolSources subject;
    private Map<String,Object> device;

    @Before public void setup() {
        when(tenants.currentTenantId()).thenReturn("owner");
        when(tenants.defaultTenant()).thenReturn("owner");
        subject = new CloudAiProtocolSources(jdbc, reader, tenants, mock(IDeviceService.class), channels, gbPlay,
            ehomeDevices, ehomeSdk, mock(EhomePreviews.class), nativeStreams, leases,
            mock(IStreamPushService.class), mock(IStreamPushPlayService.class));
        device = new HashMap<>(); device.put("dept_id", 8L); device.put("device_id", "34020000001320000001");
        device.put("name", "fixture"); device.put("play_type", "1");
        when(jdbc.queryForList(anyString(), eq(27L))).thenReturn(Collections.singletonList(device));
        VideoDeviceSource visible = new VideoDeviceSource(); visible.setRowId("27"); visible.setDeviceName("fixture");
        when(reader.gb(any())).thenReturn(Collections.singletonList(visible));
        when(reader.isup(any())).thenReturn(Collections.singletonList(visible));
        when(reader.ehome(any())).thenReturn(Collections.singletonList(visible));
        when(reader.dahua(any())).thenReturn(Collections.singletonList(visible));
    }

    @Test public void legacyProtocolDirectoriesStayInTheirAuthorizedTenant() {
        when(tenants.currentTenantId()).thenReturn("foreign");
        for (String protocol : Arrays.asList("ISUP", "EHOME", "GB28181", "DAHUA")) {
            assertEquals(0, subject.devices(protocol, 1, 10, null).get("total"));
            assertThrows(ServiceException.class, () -> subject.authorize(protocol, 27L));
            assertThrows(ServiceException.class, () -> subject.describe(protocol, 27L, 1L, "foreign", 8L));
        }
        verifyNoInteractions(reader, jdbc, nativeStreams, gbPlay);
    }

    @Test public void movedDepartmentInvalidatesEveryProtocolGrantBeforeOpeningMedia() {
        for (String protocol : Arrays.asList("ISUP", "EHOME", "GB28181", "DAHUA"))
            assertThrows(ServiceException.class, () -> subject.describe(protocol, 27L, 1L, "owner", 9L));
        verifyNoInteractions(nativeStreams, leases, gbPlay, ehomeSdk);
    }

    @Test public void gbChannelMustBelongToSelectedDeviceAndKeepsNativeIdentity() {
        DeviceChannel channel = new DeviceChannel(); channel.setId(42); channel.setParental(0);
        channel.setName("camera"); channel.setDeviceId("34020000001320000002");
        DeviceChannel directory = new DeviceChannel(); directory.setId(43); directory.setParental(1);
        when(channels.queryChaneListByDeviceDbId(27)).thenReturn(Arrays.asList(channel, directory));
        assertEquals(1, subject.choices("GB28181", 27L).size());
        Map<String,Object> source = subject.describe("GB28181", 27L, 42L, "owner", 8L);
        assertEquals("GB28181:34020000001320000001/34020000001320000002", source.get("deviceId"));
        assertEquals("PLATFORM", source.get("type"));
        assertThrows(ServiceException.class, () -> subject.describe("GB28181", 27L, 99L, "owner", 8L));
        verifyNoInteractions(gbPlay, leases);
    }

    @Test public void isupAndDahuaUseSavedPushOrNetworkSourceWithoutBrowserAddresses() {
        for (String protocol : Arrays.asList("ISUP", "DAHUA")) {
            device.put("play_type", "2"); device.put("stream_id", "owned-stream");
            Map<String,Object> push = subject.describe(protocol, 27L, null, "owner", 8L);
            assertEquals("PUSH", push.get("provider")); assertEquals("owned-stream", push.get("streamKey"));
            device.put("play_type", "3"); device.put("easy_NTS_url", "rtsp://192.0.2.1/live");
            Map<String,Object> direct = subject.describe(protocol, 27L, null, "owner", 8L);
            assertEquals("RTSP", direct.get("type")); subject.acquire(direct, "grant", UUID.randomUUID().toString());
            device.put("easy_NTS_url", "file:///etc/passwd");
            assertThrows(ServiceException.class, () -> subject.describe(protocol, 27L, null, "owner", 8L));
        }
        verifyNoInteractions(nativeStreams, leases);
    }

    @Test public void nativeChannelValidationAndOfflineEhomeNeverStartPreview() {
        for (String protocol : Arrays.asList("ISUP", "EHOME")) {
            assertThrows(ServiceException.class, () -> subject.describe(protocol, 27L, null, "owner", 8L));
            assertThrows(ServiceException.class, () -> subject.describe(protocol, 27L, 65536L, "owner", 8L));
        }
        EhomeDevice offline = new EhomeDevice(); offline.setId(27L); offline.setStatus("OFF");
        when(ehomeDevices.selectDevices(any())).thenReturn(Collections.singletonList(offline));
        assertThrows(ServiceException.class, () -> subject.choices("EHOME", 27L));
        verifyNoInteractions(ehomeSdk, nativeStreams, leases);
    }

    @Test public void isupGrantMustMatchAChannelReturnedByTheSelectedDevice() {
        device.put("luser_id", 44L);
        when(nativeStreams.isupChannels(44)).thenReturn(Collections.singletonList(CloudAiProtocolSources.choice(5L,"channel 5","PLATFORM")));
        assertThrows(ServiceException.class, () -> subject.describe("ISUP", 27L, 4L, "owner", 8L));
        assertEquals("ISUP:34020000001320000001", subject.describe("ISUP", 27L, 5L, "owner", 8L).get("deviceId"));
        verify(nativeStreams,times(2)).isupChannels(44);
    }

    @Test public void nativeMediaAcquisitionIsBoundToOriginalGrantAndRun() {
        String run = UUID.randomUUID().toString();
        device.put("luser_id", 44L);
        when(nativeStreams.isupChannels(44)).thenReturn(Collections.singletonList(CloudAiProtocolSources.choice(1L,"channel 1","PLATFORM")));
        when(leases.acquire(eq("grant"), eq(run), any())).thenReturn("rtsp://192.0.2.1/owned");
        Map<String,Object> source = subject.describe("ISUP", 27L, 1L, "owner", 8L);
        assertFalse(source.containsKey("url")); subject.acquire(source, "grant", run);
        assertEquals("RTSP", source.get("type"));
        subject.release("grant", run); verify(leases).release("grant", run);
    }
}
