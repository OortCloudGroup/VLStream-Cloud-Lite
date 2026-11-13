package com.ruoyi.wvp.media.zlm;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.StreamProxyFailure;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ZLMStreamProxyFailureTest {
    @Test
    public void cameraCapacityMissingStreamAndServerErrorsAreDistinctAndSafe() {
        for (String method : new String[]{"DESCRIBE", "SETUP", "PLAY"}) {
            for (String status : new String[]{"453", "404", "500"}) {
                JSONObject response = new JSONObject();
                response.put("code", -1);
                response.put("msg", method + ":" + status + " rtsp://user:fixture-secret@camera/profile");
                StreamProxyFailure expected = "453".equals(status) ? StreamProxyFailure.SOURCE_CAPACITY
                        : "404".equals(status) ? StreamProxyFailure.SOURCE_NOT_FOUND : StreamProxyFailure.SOURCE_ERROR;
                String message = pull(response).getMsg();
                assertEquals(expected.getMessage(), message);
                assertEquals(message, StreamProxyFailure.publicMessage(message));
                assertFalse(message.contains("fixture-secret"));
            }
        }
        assertEquals(StreamProxyFailure.UNKNOWN, StreamProxyFailure.fromZlmMessage("HTTP 500 api failure"));
        assertEquals(StreamProxyFailure.UNKNOWN, StreamProxyFailure.fromZlmMessage("SETUP:4530"));
    }

    private WVPResult<String> pull(JSONObject response) {
        ZLMRESTfulUtils rest = mock(ZLMRESTfulUtils.class);
        when(rest.addStreamProxy(any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt()))
                .thenReturn(response);
        ZLMMediaNodeServerService service = new ZLMMediaNodeServerService();
        ReflectionTestUtils.setField(service, "zlmresTfulUtils", rest);
        return service.addStreamProxy(new MediaServer(), "preview", "stream", "rtsp://fixture/profile", true, false, "0", 15);
    }

    @Test
    public void upstreamAuthenticationFailuresKeepCauseWithoutCredentials() {
        JSONObject response = new JSONObject();
        response.put("code", -1);
        response.put("msg", "DESCRIBE:401 Unauthorized rtsp://fixture:fixture-secret@camera/profile");
        WVPResult<String> result = pull(response);
        assertNotEquals(0, result.getCode());
        assertEquals(StreamProxyFailure.AUTHENTICATION.getMessage(), result.getMsg());
        assertFalse(result.getMsg().contains("fixture-secret"));
        response.put("msg", "SETUP:403 Forbidden");
        assertEquals(StreamProxyFailure.FORBIDDEN.getMessage(), pull(response).getMsg());
    }

    @Test
    public void malformedOrApiFailuresDoNotMasqueradeAsCameraAuthentication() {
        assertEquals(StreamProxyFailure.UNKNOWN.getMessage(), pull(null).getMsg());
        JSONObject response = new JSONObject();
        response.put("msg", "401 Unauthorized secret=fixture-secret");
        assertEquals(StreamProxyFailure.UNKNOWN.getMessage(), pull(response).getMsg());
        response.put("code", -100);
        assertEquals(StreamProxyFailure.UNKNOWN.getMessage(), pull(response).getMsg());
        response.put("code", 0);
        assertEquals(StreamProxyFailure.UNKNOWN.getMessage(), pull(response).getMsg());
        JSONObject data = new JSONObject();
        response.put("data", data);
        data.put("key", " ");
        assertEquals(StreamProxyFailure.UNKNOWN.getMessage(), pull(response).getMsg());
        data.put("key", "preview/stream");
        assertEquals("preview/stream", pull(response).getData());
    }
}
