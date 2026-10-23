package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/** Requires the authenticated issuing session before consuming its one-use signaling ticket. */
@Component
public class VlStreamCameraRtcHandshakeInterceptor implements HandshakeInterceptor {
    public static final String AUTHORIZATION_ATTRIBUTE = VlStreamCameraRtcHandshakeInterceptor.class.getName() + ".authorization";

    private final VlStreamDeviceCameraRtcTicketService tickets;

    public VlStreamCameraRtcHandshakeInterceptor(VlStreamDeviceCameraRtcTicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        org.springframework.security.core.Authentication authentication = SecurityUtils.getAuthentication();
        if (!sameOrigin(request) || authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        org.springframework.util.MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        if (query.size() != 1 || query.get("ticket") == null || query.get("ticket").size() != 1) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        String ticket = query.getFirst("ticket");
        try {
            attributes.put(AUTHORIZATION_ATTRIBUTE, tickets.consume(ticket));
            return true;
        } catch (ServiceException denied) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // Never log ticket query parameters or the authentication subprotocol/header payload.
    }

    private boolean sameOrigin(ServerHttpRequest request) {
        String origin = request.getHeaders().getFirst("Origin");
        String host = request.getHeaders().getFirst("Host");
        if (origin == null || host == null) return false;
        try {
            URI originUri = URI.create(origin);
            String scheme = originUri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            URI hostUri = URI.create(scheme + "://" + host);
            return originUri.getHost() != null && originUri.getHost().equalsIgnoreCase(hostUri.getHost())
                && effectivePort(originUri) == effectivePort(hostUri);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
