package com.ruoyi.vlstream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.vlstream.config.VlStreamCameraRtcProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedOutputStream;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

/** Real loopback WebSocket normal-close handshake and real isolated H2 ticket expiry. */
public class VlStreamCameraRtcRelayCloseTest {
    private VlStreamDeviceAccessServiceIsolatedTest fixture;

    @Before public void setup() throws Exception {
        fixture = new VlStreamDeviceAccessServiceIsolatedTest();
        fixture.setup();
        LoginUser user = new LoginUser();
        user.setTenantId("requester"); user.setUserId(100L); user.setPermissions(Collections.emptySet());
        user.setToken("fixture-rtc-close-session");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @After public void cleanup() throws Exception { if (fixture != null) fixture.cleanup(); }

    @Test public void upstreamNormalCloseTerminatesBrowserAndTicketLeaseExactlyOnce() throws Exception {
        assertUpstreamTermination(false);
    }

    @Test public void upstreamSctpOfferFailsClosedAndEndsTheTicketLease() throws Exception {
        assertUpstreamTermination(true);
    }

    private void assertUpstreamTermination(boolean sctpOffer) throws Exception {
        JdbcTemplate jdbc = (JdbcTemplate) field("jdbc");
        ((VlStreamDevice) field("device")).setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        VlStreamDeviceStream stream = new VlStreamDeviceStream();
        stream.setId(71L); stream.setDeviceRowId(7L); stream.setProtocol("https");
        stream.setSourceUrl("https://gateway.example/videocall/camera-a"); stream.setAvailable(true);
        when(((VlStreamDeviceStreamMapper) field("streamMapper")).selectById(71L)).thenReturn(stream);

        Class<?> gatewayType = Class.forName(VlStreamDeviceAccessServiceIsolatedTest.class.getName() + "$LocalWebSocketServer");
        Constructor<?> constructor = gatewayType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object gateway = constructor.newInstance();
        ((VlStreamCameraRtcProperties) field("cameraRtcProperties")).setCameraRtcSignalUrl(ReflectionTestUtils.invokeMethod(gateway, "url"));
        VlStreamDeviceCameraRtcTicketService tickets = (VlStreamDeviceCameraRtcTicketService) field("cameraRtcTickets");
        Map<String, Object> issued = tickets.issue(String.valueOf(field("accessRef")), 71L);
        VlStreamDeviceCameraRtcTicketService.RelayAuthorization authorization = tickets.consume(String.valueOf(issued.get("ticket")));
        WebSocketSession browser = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(VlStreamCameraRtcHandshakeInterceptor.AUTHORIZATION_ATTRIBUTE, authorization);
        when(browser.getAttributes()).thenReturn(attributes);
        when(browser.getId()).thenReturn("normal-close-test");
        AtomicBoolean open = new AtomicBoolean(true);
        CountDownLatch browserClosed = new CountDownLatch(1);
        when(browser.isOpen()).thenAnswer(invocation -> open.get());
        doAnswer(invocation -> { open.set(false); browserClosed.countDown(); return null; })
            .when(browser).close(any(CloseStatus.class));
        CountDownLatch leaseEnded = new CountDownLatch(1);
        VlStreamDeviceCameraRtcTicketService observedTickets = mock(VlStreamDeviceCameraRtcTicketService.class, delegatesTo(tickets));
        doAnswer(invocation -> {
            tickets.recordSessionEnd(authorization, invocation.getArgument(1));
            leaseEnded.countDown();
            return null;
        }).when(observedTickets).recordSessionEnd(same(authorization), anyString());
        VlStreamCameraRtcRelayHandler handler = new VlStreamCameraRtcRelayHandler(new ObjectMapper(), observedTickets);
        try {
            handler.afterConnectionEstablished(browser);
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(gateway, "awaitConnected"));
            if (sctpOffer) {
                handler.handleTextMessage(browser, new TextMessage("{\"eventName\":\"__connectto\",\"data\":{\"sessionId\":\"S\"}}"));
                assertNotNull(ReflectionTestUtils.invokeMethod(gateway, "nextMessage"));
                ReflectionTestUtils.invokeMethod(gateway, "sendText", "{\"eventName\":\"_offer\",\"data\":{\"sessionId\":\"S\",\"type\":\"offer\",\"sdp\":\"v=0\\r\\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\\r\\na=sctp-port:5000\\r\\n\"}}");
            } else {
                BufferedOutputStream output = (BufferedOutputStream) ReflectionTestUtils.getField(gateway, "output");
                synchronized (output) { output.write(new byte[]{(byte) 0x88, 2, 3, (byte) 0xe8}); output.flush(); }
            }
            assertTrue("Upstream termination must close the browser", browserClosed.await(5, TimeUnit.SECONDS));
            assertTrue("Upstream termination must finish its lease", leaseEnded.await(2, TimeUnit.SECONDS));
            assertFalse(tickets.stillAuthorized(authorization));
            assertThrows(ServiceException.class, () -> tickets.renewSession(7L, authorization.getSessionId()));
            handler.afterConnectionClosed(browser, CloseStatus.NORMAL);
            handler.shutdown();
            verify(browser, times(1)).close(any(CloseStatus.class));
            verify(observedTickets, times(1)).recordSessionEnd(same(authorization), anyString());
            assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "select count(*) from wvp_vlstream_access_audit where action='CAMERA_RTC_SESSION_ENDED'", Integer.class));
            assertEquals(sctpOffer ? "unsupported_upstream_media" : "upstream_closing", jdbc.queryForObject(
                "select detail from wvp_vlstream_access_audit where action='CAMERA_RTC_SESSION_ENDED'", String.class));
        } finally {
            handler.shutdown();
            ((AutoCloseable) gateway).close();
        }
    }

    private Object field(String name) { return ReflectionTestUtils.getField(fixture, name); }
}
