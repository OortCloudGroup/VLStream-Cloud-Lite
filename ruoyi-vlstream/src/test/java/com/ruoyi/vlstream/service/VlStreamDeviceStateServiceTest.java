package com.ruoyi.vlstream.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.vlstream.mapper.VlStreamMessageMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VlStreamDeviceStateServiceTest {
    @Test
    public void persistsLocationAndHandlesOmissionOfflineAndExplicitClear() {
        VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
        VlStreamMessageMapper messages = mock(VlStreamMessageMapper.class);
        when(messages.insertIgnore(any(), any(), any(), any())).thenReturn(1);
        VlStreamDevice device = new VlStreamDevice();
        device.setId(1L);
        when(devices.selectByDeviceIdForUpdate("location-test")).thenReturn(device);
        VlStreamDeviceStateService service = new VlStreamDeviceStateService(devices, mock(VlStreamDeviceStreamMapper.class), messages);
        JSONObject message = JSON.parseObject("{\"deviceId\":\"location-test\",\"messageId\":\"loc-1\",\"payload\":{\"online\":true,\"location\":{\"longitude\":113.123456789,\"latitude\":0}}}");
        service.handle(message);
        assertEquals(new java.math.BigDecimal("113.12345679"), device.getLongitude());
        assertEquals(new java.math.BigDecimal("0.00000000"), device.getLatitude());
        JSONObject payload = message.getJSONObject("payload");
        payload.remove("location");
        service.handle(message);
        assertEquals(new java.math.BigDecimal("113.12345679"), device.getLongitude());
        payload.put("online", false);
        payload.put("location", null);
        service.handle(message);
        org.junit.Assert.assertNotNull(device.getLongitude());
        payload.put("online", true);
        service.handle(message);
        org.junit.Assert.assertNull(device.getLongitude());
        org.junit.Assert.assertNull(device.getLatitude());
        payload.put("location", JSON.parseObject("{\"longitude\":-180,\"latitude\":90}"));
        service.handle(message);
        assertEquals(new java.math.BigDecimal("-180.00000000"), device.getLongitude());
        assertEquals(new java.math.BigDecimal("90.00000000"), device.getLatitude());
    }

    @Test
    public void rejectsInvalidLocationWithoutRecordingMessage() {
        VlStreamMessageMapper messages = mock(VlStreamMessageMapper.class);
        VlStreamDeviceStateService service = new VlStreamDeviceStateService(mock(VlStreamDeviceMapper.class), mock(VlStreamDeviceStreamMapper.class), messages);
        for (String location : new String[]{"{}", "[]", "false", "{\"longitude\":0}",
                "{\"longitude\":\"113\",\"latitude\":22}", "{\"longitude\":180.00001,\"latitude\":0}",
                "{\"longitude\":-180.00001,\"latitude\":0}", "{\"longitude\":0,\"latitude\":90.1}",
                "{\"longitude\":0,\"latitude\":-90.1}", "{\"longitude\":null,\"latitude\":0}"}) {
            JSONObject message = JSON.parseObject("{\"deviceId\":\"location-test\",\"messageId\":\"bad\",\"payload\":{\"online\":true,\"location\":" + location + "}}");
            assertEquals(400, service.handle(message).getJSONObject("payload").getIntValue("code"));
        }
        org.mockito.Mockito.verifyNoInteractions(messages);
    }

    @Test
    public void tracksOnlineTransitionsAndPreservesSnapshotsAcrossLegacyHeartbeatsAndWills() {
        VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
        VlStreamDeviceStreamMapper streams = mock(VlStreamDeviceStreamMapper.class);
        VlStreamMessageMapper messages = mock(VlStreamMessageMapper.class);
        when(messages.insertIgnore(any(), any(), any(), any())).thenReturn(1);
        VlStreamDevice device = new VlStreamDevice();
        device.setId(1L);
        device.setOnline(false);
        when(devices.selectByDeviceIdForUpdate("test")).thenReturn(device);
        VlStreamDeviceStateService service = new VlStreamDeviceStateService(devices, streams, messages);
        JSONObject message = JSON.parseObject("{\"deviceId\":\"test\",\"messageId\":\"one\",\"payload\":{\"online\":true,\"capabilities\":[\"aiInfer\"],\"models\":[{\"modelId\":\"2096927699258966018\",\"status\":\"running\"}]}}");
        service.handle(message);
        org.junit.Assert.assertNotNull(device.getLastOnlineTime());
        assertEquals("2096927699258966018", JSON.parseArray(device.getModelsJson()).getJSONObject(0).getString("modelId"));
        java.util.Date original = new java.util.Date(1L);
        device.setLastOnlineTime(original);
        message.getJSONObject("payload").remove("models");
        message.getJSONObject("payload").remove("capabilities");
        service.handle(message);
        assertEquals(original, device.getLastOnlineTime());
        org.junit.Assert.assertNotNull(device.getModelsJson());
        java.util.Date heartbeat = device.getLastHeartbeatTime();
        message.getJSONObject("payload").put("online", false);
        message.getJSONObject("payload").put("models", new com.alibaba.fastjson2.JSONArray());
        service.handle(message);
        assertEquals(original, device.getLastOnlineTime());
        assertEquals(heartbeat, device.getLastHeartbeatTime());
        org.junit.Assert.assertNotEquals("[]", device.getModelsJson());
        message.getJSONObject("payload").put("online", true);
        service.handle(message);
        org.junit.Assert.assertNotEquals(original, device.getLastOnlineTime());
        assertEquals("[]", device.getModelsJson());
    }

    @Test
    public void rejectsMalformedSnapshotsBeforeRecordingMessage() {
        VlStreamMessageMapper messages = mock(VlStreamMessageMapper.class);
        VlStreamDeviceStateService service = new VlStreamDeviceStateService(
                mock(VlStreamDeviceMapper.class), mock(VlStreamDeviceStreamMapper.class), messages);
        for (String payload : new String[]{"{\"online\":true,\"models\":{}}", "{\"online\":true,\"models\":[{\"modelId\":123}]}",
                "{\"online\":true,\"capabilities\":[false]}", "{\"online\":\"false\"}"}) {
            JSONObject message = JSON.parseObject("{\"deviceId\":\"test\",\"messageId\":\"bad\"}");
            message.put("payload", JSON.parseObject(payload));
            assertEquals(400, service.handle(message).getJSONObject("payload").getIntValue("code"));
        }
        org.mockito.Mockito.verifyNoInteractions(messages);
    }

    @Test
    public void ignoresDuplicateAndOlderSnapshots() {
        VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
        VlStreamMessageMapper messages = mock(VlStreamMessageMapper.class);
        VlStreamDeviceStateService service = new VlStreamDeviceStateService(devices, mock(VlStreamDeviceStreamMapper.class), messages);
        JSONObject message = JSON.parseObject("{\"deviceId\":\"test\",\"messageId\":\"old\",\"sentAt\":\"2026-01-01T00:00:00Z\",\"payload\":{\"online\":true,\"models\":[]}}");
        service.handle(message);
        org.mockito.Mockito.verify(devices, org.mockito.Mockito.never()).insert(any());
        org.mockito.Mockito.verify(devices, org.mockito.Mockito.never()).update(any());
        when(messages.insertIgnore(any(), any(), any(), any())).thenReturn(1);
        VlStreamDevice device = new VlStreamDevice();
        device.setLastReportedAt(java.util.Date.from(java.time.Instant.parse("2026-09-08T00:00:00Z")));
        when(devices.selectByDeviceIdForUpdate("test")).thenReturn(device);
        service.handle(message);
        org.mockito.Mockito.verify(devices, org.mockito.Mockito.never()).update(any());
        org.junit.Assert.assertNull(device.getLastOnlineTime());
    }

    @Test
    public void persistsReportedHttpCameraRtcStream() {
        VlStreamDeviceMapper deviceMapper = mock(VlStreamDeviceMapper.class);
        VlStreamDeviceStreamMapper streamMapper = mock(VlStreamDeviceStreamMapper.class);
        VlStreamMessageMapper messageMapper = mock(VlStreamMessageMapper.class);
        when(messageMapper.insertIgnore(eq("AETY-1"), eq("m1"), any(), any())).thenReturn(1);
        when(deviceMapper.insert(any())).thenAnswer(invocation -> {
            VlStreamDevice device = invocation.getArgument(0);
            device.setId(10L);
            return 1;
        });
        VlStreamDeviceStateService service = new VlStreamDeviceStateService(deviceMapper, streamMapper, messageMapper);
        JSONObject message = JSON.parseObject("{\"protocolVersion\":\"2.2\",\"messageId\":\"m1\","
                + "\"deviceId\":\"AETY-1\",\"sentAt\":\"2026-08-28T09:37:03Z\","
                + "\"payload\":{\"online\":true,\"streams\":[{\"channelId\":\"CH-1\","
                + "\"streamType\":\"main\",\"protocol\":\"http\","
                + "\"url\":\"http://146.56.220.167:8082/videocall/AETY-1\","
                + "\"default\":true,\"available\":true}]}}");

        service.handle(message);

        ArgumentCaptor<VlStreamDeviceStream> captor = ArgumentCaptor.forClass(VlStreamDeviceStream.class);
        verify(streamMapper).insert(captor.capture());
        assertEquals("http", captor.getValue().getProtocol());
        assertEquals("http://146.56.220.167:8082/videocall/AETY-1", captor.getValue().getSourceUrl());
    }
}
