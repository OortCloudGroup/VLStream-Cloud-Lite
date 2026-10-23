package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VlStreamCameraRtcHandshakeInterceptorTest {
    private final VlStreamDeviceCameraRtcTicketService tickets = mock(VlStreamDeviceCameraRtcTicketService.class);
    private final VlStreamCameraRtcHandshakeInterceptor interceptor = new VlStreamCameraRtcHandshakeInterceptor(tickets);

    @Before public void authenticatedViewer() {
        LoginUser user = new LoginUser(); user.setUserId(100L); user.setTenantId("viewer"); user.setToken("fixture-session");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @After public void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test public void anonymousTicketPossessionCannotReachConsumption() {
        SecurityContextHolder.clearContext();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        assertFalse(interceptor.beforeHandshake(request("https://wvp.example/vlstream/device/shared/camera-rtc?ticket=one",
            "https://wvp.example", "wvp.example"), response, null, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verifyNoInteractions(tickets);
    }

    @Test public void consumesOnlyExactSameOriginSingleTicketHandshake() throws Exception {
        when(tickets.consume("single-use-ticket")).thenReturn(null);
        ServerHttpRequest request = request("https://wvp.example/vlstream/device/shared/camera-rtc?ticket=single-use-ticket",
            "https://wvp.example", "wvp.example");
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(request, response, null, attributes));
        verify(tickets).consume("single-use-ticket");
    }

    @Test public void rejectsCrossOriginDuplicateAndInvalidTicketsBeforeAcceptingSocket() throws Exception {
        ServerHttpRequest foreignOrigin = request("https://wvp.example/vlstream/device/shared/camera-rtc?ticket=one",
            "https://attacker.example", "wvp.example");
        ServerHttpResponse foreignResponse = mock(ServerHttpResponse.class);
        assertFalse(interceptor.beforeHandshake(foreignOrigin, foreignResponse, null, new HashMap<>()));
        verify(foreignResponse).setStatusCode(HttpStatus.FORBIDDEN);

        ServerHttpRequest duplicate = request("https://wvp.example/vlstream/device/shared/camera-rtc?ticket=one&ticket=two",
            "https://wvp.example", "wvp.example");
        ServerHttpResponse duplicateResponse = mock(ServerHttpResponse.class);
        assertFalse(interceptor.beforeHandshake(duplicate, duplicateResponse, null, new HashMap<>()));
        verify(duplicateResponse).setStatusCode(HttpStatus.FORBIDDEN);

        when(tickets.consume("expired")).thenThrow(new ServiceException("expired", 403));
        ServerHttpRequest expired = request("https://wvp.example/vlstream/device/shared/camera-rtc?ticket=expired",
            "https://wvp.example", "wvp.example");
        ServerHttpResponse expiredResponse = mock(ServerHttpResponse.class);
        assertFalse(interceptor.beforeHandshake(expired, expiredResponse, null, new HashMap<>()));
        verify(expiredResponse).setStatusCode(HttpStatus.FORBIDDEN);
        verify(tickets, never()).consume("one");
        verify(tickets, never()).consume("two");
    }

    private static ServerHttpRequest request(String url, String origin, String host) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", origin);
        headers.add("Host", host);
        when(request.getURI()).thenReturn(URI.create(url));
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }
}
