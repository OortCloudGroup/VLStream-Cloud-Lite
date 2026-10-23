package com.ruoyi.vlstream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.vlstream.service.VlStreamDeviceCameraRtcTicketService.RelayAuthorization;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ticket-gated, server-side relay for one authorized CameraRTC signaling session. */
@Component
public class VlStreamCameraRtcRelayHandler extends TextWebSocketHandler {
    private static final int MAX_MESSAGE_LENGTH = 65536;
    private static final int MAX_QUEUED_MESSAGES = 20;
    private final ObjectMapper mapper;
    private final VlStreamDeviceCameraRtcTicketService tickets;
    private final OkHttpClient upstreamClient;
    private final ScheduledExecutorService checks = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vlstream-camera-rtc-access-check");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentMap<String, Bridge> bridges = new ConcurrentHashMap<>();

    public VlStreamCameraRtcRelayHandler(ObjectMapper mapper, VlStreamDeviceCameraRtcTicketService tickets) {
        this.mapper = mapper;
        this.tickets = tickets;
        this.upstreamClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession client) throws Exception {
        Object value = client.getAttributes().get(VlStreamCameraRtcHandshakeInterceptor.AUTHORIZATION_ATTRIBUTE);
        if (!(value instanceof RelayAuthorization)) {
            client.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        RelayAuthorization authorization = (RelayAuthorization) value;
        Bridge bridge = new Bridge(client, authorization);
        bridges.put(client.getId(), bridge);
        bridge.accessCheck = checks.scheduleWithFixedDelay(() -> {
            if (!tickets.stillAuthorized(authorization)) terminate(bridge, CloseStatus.POLICY_VIOLATION, "access_changed");
        }, 2, 2, TimeUnit.SECONDS);
        try {
            Request request = new Request.Builder().url(tickets.upstreamWebSocketUrl()).header("Cache-Control", "no-store").build();
            bridge.upstream = upstreamClient.newWebSocket(request, listener(bridge));
        } catch (RuntimeException invalidConfiguration) {
            terminate(bridge, CloseStatus.SERVER_ERROR, "relay_unavailable");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession client, TextMessage message) {
        Bridge bridge = bridges.get(client.getId());
        if (bridge == null || bridge.terminated.get()) return;
        if (message.getPayloadLength() > MAX_MESSAGE_LENGTH || !tickets.stillAuthorized(bridge.authorization)) {
            terminate(bridge, CloseStatus.POLICY_VIOLATION, "access_changed_or_invalid_message");
            return;
        }
        try {
            JsonNode root = mapper.readTree(message.getPayload());
            String event = root.path("eventName").asText("");
            String requestedSessionId = root.path("data").path("sessionId").asText("");
            if (!VlStreamCameraRtcMessagePolicy.isClientEvent(event)) {
                terminate(bridge, CloseStatus.POLICY_VIOLATION, "unsupported_event");
                return;
            }
            if ("__connectto".equals(event)) {
                if (!VlStreamCameraRtcMessagePolicy.validSessionId(requestedSessionId)) {
                    terminate(bridge, CloseStatus.POLICY_VIOLATION, "invalid_session");
                    return;
                }
                String previousSessionId = bridge.sessionId;
                if (previousSessionId != null && previousSessionId.equals(requestedSessionId)) return;
                if (previousSessionId != null) {
                    JsonNode disconnect = disconnectMessage(previousSessionId);
                    ObjectNode safeDisconnect = VlStreamCameraRtcMessagePolicy.clientMessage(mapper, disconnect,
                        bridge.authorization.getCameraId(), bridge.relayClientId, previousSessionId);
                    forwardOrQueue(bridge, mapper.writeValueAsString(safeDisconnect));
                }
                bridge.sessionId = requestedSessionId;
            } else if (bridge.sessionId == null || !bridge.sessionId.equals(requestedSessionId)) {
                return;
            }
            JsonNode safeNode = VlStreamCameraRtcMessagePolicy.clientMessage(mapper, root,
                bridge.authorization.getCameraId(), bridge.relayClientId, bridge.sessionId);
            if (safeNode == null && "__ice_candidate".equals(event)) return;
            if (safeNode == null) {
                terminate(bridge, CloseStatus.POLICY_VIOLATION, "invalid_message");
                return;
            }
            forwardOrQueue(bridge, mapper.writeValueAsString(safeNode));
        } catch (IOException | RuntimeException invalidMessage) {
            terminate(bridge, CloseStatus.POLICY_VIOLATION, "invalid_message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession client, CloseStatus status) {
        Bridge bridge = bridges.get(client.getId());
        if (bridge != null) terminate(bridge, CloseStatus.NORMAL, "client_closed");
    }

    @Override
    public void handleTransportError(WebSocketSession client, Throwable error) {
        Bridge bridge = bridges.get(client.getId());
        if (bridge != null) terminate(bridge, CloseStatus.SERVER_ERROR, "client_transport_error");
    }

    private WebSocketListener listener(Bridge bridge) {
        return new WebSocketListener() {
            @Override public void onOpen(WebSocket socket, Response response) {
                bridge.upstream = socket;
                if (bridge.terminated.get()) {
                    socket.cancel();
                    return;
                }
                bridge.upstreamOpen = true;
                flushQueue(bridge);
            }

            @Override public void onMessage(WebSocket socket, String text) {
                if (bridge.terminated.get()) return;
                if (text == null || text.length() > MAX_MESSAGE_LENGTH || !tickets.stillAuthorized(bridge.authorization)) {
                    terminate(bridge, CloseStatus.POLICY_VIOLATION, "access_changed_or_invalid_upstream");
                    return;
                }
                String sessionId = bridge.sessionId;
                if (sessionId == null) return;
                try {
                    JsonNode message = mapper.readTree(text);
                    JsonNode safe = VlStreamCameraRtcMessagePolicy.serverMessage(mapper, message, sessionId);
                    if (safe != null) sendToClient(bridge, mapper.writeValueAsString(safe));
                    else if ("_offer".equals(message.path("eventName").asText())
                            && sessionId.equals(message.path("data").path("sessionId").asText())) {
                        terminate(bridge, CloseStatus.POLICY_VIOLATION, "unsupported_upstream_media");
                    }
                } catch (IOException | RuntimeException invalidMessage) {
                    terminate(bridge, CloseStatus.SERVER_ERROR, "invalid_upstream_message");
                }
            }

            @Override public void onFailure(WebSocket socket, Throwable failure, Response response) {
                terminate(bridge, CloseStatus.SERVER_ERROR, "relay_unavailable");
            }

            @Override public void onClosing(WebSocket socket, int code, String reason) {
                // OkHttp waits for our close reply before onClosed; end the lease at the first close frame.
                terminate(bridge, CloseStatus.GOING_AWAY, "upstream_closing");
            }

            @Override public void onClosed(WebSocket socket, int code, String reason) {
                terminate(bridge, CloseStatus.GOING_AWAY, "upstream_closed");
            }
        };
    }

    private void forwardOrQueue(Bridge bridge, String payload) {
        WebSocket upstream = bridge.upstream;
        if (upstream == null) {
            terminate(bridge, CloseStatus.SERVER_ERROR, "relay_unavailable");
        } else if (bridge.upstreamOpen) {
            if (!upstream.send(payload)) terminate(bridge, CloseStatus.SERVER_ERROR, "relay_unavailable");
        } else if (bridge.queued.size() >= MAX_QUEUED_MESSAGES) {
            terminate(bridge, CloseStatus.POLICY_VIOLATION, "too_many_pending_messages");
        } else {
            bridge.queued.add(payload);
        }
    }

    private void flushQueue(Bridge bridge) {
        String payload;
        while (!bridge.terminated.get() && (payload = bridge.queued.poll()) != null) {
            if (!tickets.stillAuthorized(bridge.authorization) || !bridge.upstream.send(payload)) {
                terminate(bridge, CloseStatus.POLICY_VIOLATION, "access_changed");
                return;
            }
        }
    }

    private void sendToClient(Bridge bridge, String payload) {
        if (!bridge.client.isOpen()) return;
        try {
            synchronized (bridge.client) {
                if (bridge.client.isOpen()) bridge.client.sendMessage(new TextMessage(payload));
            }
        } catch (IOException failure) {
            terminate(bridge, CloseStatus.SERVER_ERROR, "client_transport_error");
        }
    }

    private void terminate(Bridge bridge, CloseStatus status, String reason) {
        if (bridge == null || !bridge.terminated.compareAndSet(false, true)) return;
        bridges.remove(bridge.client.getId(), bridge);
        if (bridge.accessCheck != null) bridge.accessCheck.cancel(false);
        WebSocket upstream = bridge.upstream;
        if (upstream != null) {
            if (bridge.upstreamOpen && bridge.sessionId != null) {
                try {
                    upstream.send(mapper.writeValueAsString(VlStreamCameraRtcMessagePolicy.clientMessage(mapper,
                        disconnectMessage(bridge.sessionId), bridge.authorization.getCameraId(), bridge.relayClientId, bridge.sessionId)));
                } catch (IOException ignored) { }
            }
            if (!upstream.close(1000, "relay closed")) upstream.cancel();
        }
        if (bridge.client.isOpen()) {
            try { bridge.client.close(new CloseStatus(status.getCode(), reason)); } catch (IOException ignored) { }
        }
        try { tickets.recordSessionEnd(bridge.authorization, reason); } catch (RuntimeException ignored) { }
    }

    private JsonNode disconnectMessage(String sessionId) {
        ObjectNode message = mapper.createObjectNode();
        message.put("eventName", "__disconnected");
        message.putObject("data").put("sessionId", sessionId);
        return message;
    }

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, Bridge> entry : bridges.entrySet()) terminate(entry.getValue(), CloseStatus.GOING_AWAY, "server_shutdown");
        checks.shutdownNow();
        upstreamClient.dispatcher().executorService().shutdownNow();
        upstreamClient.connectionPool().evictAll();
    }

    private static final class Bridge {
        private final WebSocketSession client;
        private final RelayAuthorization authorization;
        private final String relayClientId = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final ConcurrentLinkedQueue<String> queued = new ConcurrentLinkedQueue<>();
        private volatile String sessionId;
        private volatile WebSocket upstream;
        private volatile boolean upstreamOpen;
        private volatile ScheduledFuture<?> accessCheck;

        private Bridge(WebSocketSession client, RelayAuthorization authorization) {
            this.client = client;
            this.authorization = authorization;
        }
    }
}
