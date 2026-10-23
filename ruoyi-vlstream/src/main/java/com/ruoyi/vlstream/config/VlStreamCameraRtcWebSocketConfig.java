package com.ruoyi.vlstream.config;

import com.ruoyi.vlstream.service.VlStreamCameraRtcHandshakeInterceptor;
import com.ruoyi.vlstream.service.VlStreamCameraRtcRelayHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/** Registers the ticket-gated CameraRTC relay on one exact path. */
@Configuration
@EnableWebSocket
public class VlStreamCameraRtcWebSocketConfig implements WebSocketConfigurer {
    private final VlStreamCameraRtcRelayHandler handler;
    private final VlStreamCameraRtcHandshakeInterceptor interceptor;

    public VlStreamCameraRtcWebSocketConfig(VlStreamCameraRtcRelayHandler handler,
            VlStreamCameraRtcHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        DefaultHandshakeHandler handshake = new DefaultHandshakeHandler();
        // Only a public protocol name may be negotiated; credential-bearing protocol values are never echoed.
        handshake.setSupportedProtocols("vlstream-shared-rtc.v1");
        registry.addHandler(handler, "/vlstream/device/shared/camera-rtc")
            .setHandshakeHandler(handshake)
            .addInterceptors(interceptor);
    }
}
