package com.ruoyi.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.rtsp.constants.StreamTypeConstants;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.mapper.RtspDeviceMapper;
import com.ruoyi.rtsp.service.RtspDeviceScopedReader;
import com.ruoyi.rtsp.service.impl.RtspDeviceServiceImpl;
import com.ruoyi.rtsp.tool.RtspTool;
import java.net.URI;
import java.util.Collections;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RtspAddressValidationTest {
    private RtspDevice device(String host) {
        RtspDevice row = new RtspDevice();
        row.setId(1L); row.setIp(host); row.setUserName("fixture"); row.setPassword("fixture");
        row.setChannel("101"); row.setFirm(StreamTypeConstants.HIKVISION); row.setPlayType("1");
        return row;
    }

    @Test public void preservesLegacyHostVendorAndChannelBehavior() {
        RtspDevice row = device("192.0.2.11");
        assertEquals("rtsp://fixture:fixture@192.0.2.11:554/Streaming/Channels/101", RtspTool.generateRtspUrl(row));
        row.setFirm(StreamTypeConstants.DAHUA); row.setChannel("1");
        assertTrue(RtspTool.generateRtspUrl(row).endsWith("/cam/realmonitor?channel=1&subtype=0"));
        row.setFirm(StreamTypeConstants.UNIVIEW);
        assertTrue(RtspTool.generateRtspUrl(row).endsWith("/media/video1"));
        row.setFirm(StreamTypeConstants.MERCURY);
        assertTrue(RtspTool.generateRtspUrl(row).endsWith("/stream1"));
        assertEquals("camera.example:554", RtspTool.normalizeHostPort("camera.example", null));
    }

    @Test public void acceptsEmbeddedAndSeparatePortsWithoutDuplication() {
        for (int port : new int[]{1, 554, 8554, 65535}) {
            assertEquals("192.0.2.11:" + port, RtspTool.normalizeHostPort("192.0.2.11:" + port, null));
            assertEquals("camera.example:" + port, RtspTool.normalizeHostPort("camera.example", port));
            assertEquals("camera.example:" + port, RtspTool.normalizeHostPort("camera.example:" + port, port));
        }
        assertEquals("192.0.2.11:554", RtspTool.normalizeHostPort(" 192.0.2.11:0554 ", null));
        assertThrows(ServiceException.class, () -> RtspTool.normalizeHostPort("camera.example:554", 8554));
    }

    @Test public void handlesIpv6WithoutGuessingATrailingPort() {
        assertEquals("[2001:db8::1]:554", RtspTool.normalizeHostPort("2001:db8::1", null));
        assertEquals("[2001:db8::1]:554", RtspTool.normalizeHostPort("[2001:db8::1]", null));
        assertEquals("[2001:db8::1]:8554", RtspTool.normalizeHostPort("[2001:db8::1]:8554", null));
        assertEquals("[2001:db8::1]:8554", RtspTool.normalizeHostPort("2001:db8::1", 8554));
        assertEquals("[2001:db8::1:554]:554", RtspTool.normalizeHostPort("2001:db8::1:554", null));
        assertEquals("[::ffff:192.0.2.11]:554", RtspTool.normalizeHostPort("::ffff:192.0.2.11", null));
    }

    @Test public void rejectsWrongHostTypesMalformedPortsAndUrlInjectionWithoutEchoingInput() {
        for (String host : new String[]{"", " ", "rtsp://user:secret@camera/track", "https://camera", "camera/path",
                "camera?query", "camera#fragment", "user@camera", "camera\\path", "camera name", "camera\r\nname",
                "192.0.2.11:554:554", "camera:", "camera:abc", "camera:-1", "camera:0", "camera:65536",
                "camera:999999999999", "[::1", "[::1]garbage", "[::1]:", "[camera]:554", "999.1.1.1", ":554"}) {
            ServiceException failure = assertThrows(host, ServiceException.class, () -> RtspTool.normalizeHostPort(host, null));
            assertFalse(failure.getMessage().contains("secret"));
        }
        assertThrows(ServiceException.class, () -> RtspTool.normalizeHostPort(null, null));
        for (int port : new int[]{-1, 0, 65536, Integer.MAX_VALUE}) {
            assertThrows(ServiceException.class, () -> RtspTool.normalizeHostPort("camera", port));
        }
    }

    @Test public void reservedCredentialAndChannelCharactersCannotChangeUriStructure() throws Exception {
        RtspDevice row = device("192.0.2.11:8554");
        row.setUserName("fixture@name"); row.setPassword("p@ss:#?%/中文"); row.setChannel("101?extra=1#fragment");
        URI uri = new URI(RtspTool.generateRtspUrl(row));
        assertEquals("192.0.2.11", uri.getHost()); assertEquals(8554, uri.getPort());
        assertNull(uri.getRawQuery()); assertNull(uri.getRawFragment());
        assertEquals("fixture@name:p@ss:#?%/中文", uri.getUserInfo());
        assertTrue(uri.getRawPath().contains("101%3Fextra%3D1%23fragment"));
    }

    private RtspDeviceServiceImpl service(RtspDeviceMapper mapper, RtspDeviceScopedReader reader) {
        RtspDeviceServiceImpl service = new RtspDeviceServiceImpl();
        ReflectionTestUtils.setField(service, "rtspDeviceMapper", mapper);
        ReflectionTestUtils.setField(service, "scopedReader", reader);
        return service;
    }

    @Test public void validatesBeforeInsertAndPersistsSeparatePortInExistingFields() {
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDeviceScopedReader reader = mock(RtspDeviceScopedReader.class);
        when(reader.currentTenant()).thenReturn("fixture-tenant");
        RtspDeviceServiceImpl service = service(mapper, reader);
        assertThrows(ServiceException.class, () -> service.insertRtspDevice(device("192.0.2.11:554:554")));
        verify(mapper, never()).insertRtspDevice(any());
        RtspDevice row = device("192.0.2.11"); row.setPort(8554);
        service.insertRtspDevice(row);
        assertEquals("192.0.2.11:8554", row.getIp());
        assertEquals(8554, URI.create(row.getUrl()).getPort());
        assertEquals("fixture-tenant", row.getTenantId());
        verify(mapper).insertRtspDevice(row);
        row.setPort(null);
        assertEquals(8554, URI.create(RtspTool.generateRtspUrl(row)).getPort());
    }

    @Test public void invalidEditDoesNotReachMapperAndOriginalAccessCheckStillRuns() {
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDeviceScopedReader reader = mock(RtspDeviceScopedReader.class);
        RtspDevice row = device("192.0.2.11:554:554");
        when(reader.forOperation(any(), eq(false))).thenReturn(Collections.singletonList(row));
        assertThrows(ServiceException.class, () -> service(mapper, reader).updateRtspDevice(row));
        verify(reader).forOperation(any(), eq(false));
        verify(reader).scope(row);
        verify(mapper, never()).updateRtspDevice(any());
    }

    @Test public void jsonAcceptsSeparatePortButDoesNotExposeAnUnpersistedPortColumn() throws Exception {
        ObjectMapper json = new ObjectMapper();
        RtspDevice row = json.readValue("{\"ip\":\"camera.example\",\"port\":8554}", RtspDevice.class);
        assertEquals(Integer.valueOf(8554), row.getPort());
        assertFalse(json.readTree(json.writeValueAsString(row)).has("port"));
        assertThrows(Exception.class, () -> json.readValue("{\"ip\":{\"host\":\"camera\"}}", RtspDevice.class));
        assertThrows(Exception.class, () -> json.readValue("{\"port\":\"rtsp://camera\"}", RtspDevice.class));
        assertEquals(Integer.valueOf(8554), json.readValue("{\"port\":\"8554\"}", RtspDevice.class).getPort());
        for (String input : new String[]{"{\"ip\":123}", "{\"ip\":[]}", "{\"port\":true}",
                "{\"port\":8554.5}", "{\"port\":65536}", "{\"port\":999999999999}"}) {
            assertThrows(input, Exception.class, () -> json.readValue(input, RtspDevice.class));
        }
    }

    @Test public void nonLocalPlaybackDoesNotAcquireLocalHostRequirements() {
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDeviceScopedReader reader = mock(RtspDeviceScopedReader.class);
        when(reader.currentTenant()).thenReturn("fixture-tenant");
        for (String mode : new String[]{"2", "3"}) {
            RtspDevice row = device(null); row.setPlayType(mode); row.setStreamId("fixture");
            row.setEasyNTSUrl("https://camera.example/fixture"); row.setUrl("original-source");
            service(mapper, reader).insertRtspDevice(row);
            assertEquals("original-source", row.getUrl());
            assertNull(row.getIp());
        }
        verify(mapper, times(2)).insertRtspDevice(any());
    }
}
