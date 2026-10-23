package com.ruoyi.vlstream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.vlstream.config.VlStreamDeviceAccessProperties;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.config.VlStreamCameraRtcProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.custom.availability.CustomStreamProbe;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.sql.DataSource;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Transactional sharing workflow integration tests using an optional cached H2 jar.
 * Supply -Dvideo.square.h2Jar=<path> to run; all tables are created in a unique in-memory database.
 */
public class VlStreamDeviceAccessServiceIsolatedTest {
    private URLClassLoader h2Loader;
    private Object h2Pool;
    private JdbcTemplate jdbc;
    private VlStreamDeviceAccessService service;
    private VlStreamDevice device;
    private VlStreamDeviceMapper deviceMapper;
    private VlStreamDeviceTenantService tenants;
    private VlStreamDeviceAccessRefService refs;
    private VlStreamDeviceAccessProperties accessProperties;
    private PlatformTransactionManager transactionManager;
    private VlStreamDeviceStreamMapper streamMapper;
    private IMediaServerService media;
    private CustomStreamProbe sourceProbe;
    private MediaServer mediaServer;
    private VlStreamDevicePreviewLeaseService previewService;
    private VlStreamDeviceCameraRtcTicketService cameraRtcTickets;
    private VlStreamCameraRtcProperties cameraRtcProperties;
    private final String accessRef = "0123456789abcdef0123456789abcdef";

    @Before public void setup() throws Exception {
        String jar = System.getProperty("video.square.h2Jar");
        Assume.assumeTrue("Optional cached H2 jar was not supplied", jar != null && new File(jar).isFile());
        h2Loader = new URLClassLoader(new URL[]{new File(jar).toURI().toURL()}, getClass().getClassLoader());
        Class<?> poolClass = h2Loader.loadClass("org.h2.jdbcx.JdbcConnectionPool");
        h2Pool = poolClass.getMethod("create", String.class, String.class, String.class).invoke(null,
            "jdbc:h2:mem:sharing_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate((DataSource) h2Pool);
        schema();

        device = new VlStreamDevice();
        device.setId(7L); device.setTenantId("owner"); device.setDeviceName("Share fixture");
        device.setVisibility("PRIVATE"); device.setOnline(true); device.setLatitude(new BigDecimal("22.5")); device.setLongitude(new BigDecimal("114.1"));
        deviceMapper = mock(VlStreamDeviceMapper.class);
        when(deviceMapper.selectById(7L)).thenReturn(device);
        when(deviceMapper.selectByIdForUpdate(7L)).thenAnswer(invocation -> {
            jdbc.queryForObject("select id from wvp_vlstream_device where id=? for update", Long.class, 7L);
            return device;
        });

        VlStreamDeviceProperties tenantProperties = new VlStreamDeviceProperties();
        tenantProperties.setDefaultTenantId("default");
        tenants = new VlStreamDeviceTenantService(tenantProperties, deviceMapper);
        accessProperties = new VlStreamDeviceAccessProperties();
        refs = new VlStreamDeviceAccessRefService(jdbc);
        jdbc.update("insert into wvp_vlstream_device_access_ref(device_row_id,access_ref) values (?,?)", 7L, accessRef);
        VlStreamDeviceAccessService target = new VlStreamDeviceAccessService(jdbc, deviceMapper, tenants, refs,
            accessProperties, mock(ApplicationEventPublisher.class));
        transactionManager = new DataSourceTransactionManager((DataSource) h2Pool);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        service = (VlStreamDeviceAccessService) proxy.getProxy();

        streamMapper = mock(VlStreamDeviceStreamMapper.class);
        media = mock(IMediaServerService.class);
        sourceProbe = mock(CustomStreamProbe.class);
        cameraRtcProperties = new VlStreamCameraRtcProperties();
        cameraRtcProperties.setCameraRtcSignalUrl("wss://camera-rtc.example");
        VlStreamDeviceCameraRtcTicketService ticketTarget = new VlStreamDeviceCameraRtcTicketService(jdbc,
            deviceMapper, streamMapper, tenants, service, accessProperties, cameraRtcProperties);
        ProxyFactory ticketProxy = new ProxyFactory(ticketTarget);
        ticketProxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        cameraRtcTickets = (VlStreamDeviceCameraRtcTicketService) ticketProxy.getProxy();
        previewService = new VlStreamDevicePreviewLeaseService(jdbc, deviceMapper, streamMapper, tenants, refs,
            service, cameraRtcTickets, accessProperties, media, sourceProbe, transactionManager);

        authenticate("requester", 100L);
    }

    @After public void cleanup() throws Exception {
        SecurityContextHolder.clearContext();
        if (h2Pool != null) h2Pool.getClass().getMethod("dispose").invoke(h2Pool);
        if (h2Loader != null) h2Loader.close();
    }

    @Test public void concurrentDuplicateApplicationsSerializeOnTheDeviceAndCreateOnePendingRequest() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = pool.submit(() -> submitRequest(ready, start, "first"));
            Future<String> second = pool.submit(() -> submitRequest(ready, start, "second"));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from wvp_vlstream_access_request", Integer.class));
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from wvp_vlstream_access_audit where action='REQUESTED'", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test public void approvalExpiryAndRevocationAreAuditedAndAccessIsTenantScoped() {
        Map<String, Object> requested = service.request(accessRef, "Need read-only review");
        Long requestId = Long.valueOf(String.valueOf(requested.get("requestId")));
        authenticate("owner", 200L);
        Map<String, Object> approved = service.approve(requestId, null);
        Long grantId = Long.valueOf(String.valueOf(approved.get("grantId")));
        assertEquals(Integer.valueOf(7), approved.get("expiresInDays"));
        Timestamp expiration = jdbc.queryForObject("select expires_at from wvp_vlstream_access_grant where grant_id=?", Timestamp.class, grantId);
        assertTrue(expiration.after(new Timestamp(System.currentTimeMillis())));
        assertTrue(expiration.before(new Timestamp(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(8))));

        service.revoke(grantId);
        authenticate("requester", 100L);
        Map<String, Object> state = service.access(accessRef);
        assertEquals("NONE", state.get("viewerAccess"));
        assertEquals("REVOKED", state.get("myAccessRequestStatus"));
        assertFalse(state.containsKey("latitude"));
        assertFalse(state.containsKey("longitude"));
        assertFalse(state.containsKey("ipAddr"));
        assertEquals(Integer.valueOf(3), jdbc.queryForObject("select count(*) from wvp_vlstream_access_audit", Integer.class));
    }

    @Test public void expiredGrantIsDeniedAndMaterializedAsAnAuditedTerminalState() {
        Map<String, Object> requested = service.request(accessRef, null);
        Long requestId = Long.valueOf(String.valueOf(requested.get("requestId")));
        authenticate("owner", 200L);
        Map<String, Object> approved = service.approve(requestId, 1);
        Long grantId = Long.valueOf(String.valueOf(approved.get("grantId")));
        jdbc.update("update wvp_vlstream_access_grant set expires_at=TIMESTAMPADD(DAY,-1,NOW()) where grant_id=?", grantId);

        authenticate("requester", 100L);
        Map<String, Object> state = service.access(accessRef);
        assertEquals("NONE", state.get("viewerAccess"));
        assertEquals("EXPIRED", state.get("myAccessRequestStatus"));
        authenticate("owner", 200L);
        service.markExpiredGrants();
        assertEquals("EXPIRED", jdbc.queryForObject("select status from wvp_vlstream_access_request where request_id=?", String.class, requestId));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from wvp_vlstream_access_audit where action='GRANT_EXPIRED'", Integer.class));
        assertNotNull(jdbc.queryForObject("select expired_at from wvp_vlstream_access_grant where grant_id=?", Timestamp.class, grantId));
    }

    @Test public void overLimitApprovalIsRejectedAndForeignTenantCannotChangeVisibility() {
        Map<String, Object> requested = service.request(accessRef, null);
        Long requestId = Long.valueOf(String.valueOf(requested.get("requestId")));
        authenticate("owner", 200L);
        ServiceException tooLong = assertThrows(ServiceException.class, () -> service.approve(requestId, 31));
        assertEquals(Integer.valueOf(400), tooLong.getCode());
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from wvp_vlstream_access_grant", Integer.class));

        authenticate("requester", 100L);
        ServiceException notOwner = assertThrows(ServiceException.class, () -> service.setVisibility(7L, "PUBLIC"));
        assertEquals(Integer.valueOf(403), notOwner.getCode());
        assertEquals("PRIVATE", jdbc.queryForObject("select visibility from wvp_vlstream_device where id=7", String.class));
        assertTrue(permission("setVisibility").contains("vlstream:device:share:manage"));
        for (String method : Arrays.asList("approve", "reject", "revoke")) {
            assertTrue(permission(method).contains("vlstream:device:share:approve"));
        }
    }

    private String submitRequest(CountDownLatch ready, CountDownLatch start, String reason) throws Exception {
        authenticate("requester", 100L);
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start signal timed out");
        try {
            return String.valueOf(service.request(accessRef, reason).get("requestId"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String tenant, long userId) {
        LoginUser user = new LoginUser();
        user.setTenantId(tenant); user.setUserId(userId);
        user.setToken("fixture-session-" + tenant + "-" + userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @Test public void cameraRtcTicketCannotBeConsumedAnonymouslyAndFailureDoesNotBurnIt() {
        String ticket = issuePublicCameraRtcTicket();
        SecurityContextHolder.clearContext();
        assertCopiedTicketDeniedThenOriginalViewerCanConsume(ticket);
    }

    @Test public void cameraRtcTicketCannotBeConsumedByAnotherUserInTheSameTenant() {
        String ticket = issuePublicCameraRtcTicket();
        authenticate("requester", 101L);
        assertCopiedTicketDeniedThenOriginalViewerCanConsume(ticket);
    }

    @Test public void cameraRtcTicketCannotBeConsumedByAnotherTenant() {
        String ticket = issuePublicCameraRtcTicket();
        authenticate("foreign", 100L);
        assertCopiedTicketDeniedThenOriginalViewerCanConsume(ticket);
    }

    @Test public void cameraRtcTicketCannotBeConsumedByAnotherLoginSessionOfTheSameUser() {
        String ticket = issuePublicCameraRtcTicket();
        ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).setToken("another-session-for-same-user");
        assertCopiedTicketDeniedThenOriginalViewerCanConsume(ticket);
    }

    @Test public void cameraRtcTicketKeepsNativeDefaultTenantNormalization() {
        authenticate(null, 100L);
        LoginUser principal = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        principal.setToken("native-default-session");
        String ticket = issuePublicCameraRtcTicket();
        principal.setTenantId("default");
        assertEquals("default", cameraRtcTickets.consume(ticket).getViewerTenantId());
    }

    private String issuePublicCameraRtcTicket() {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        VlStreamDeviceStream stream = new VlStreamDeviceStream();
        stream.setId(71L); stream.setDeviceRowId(7L); stream.setProtocol("https");
        stream.setSourceUrl("https://gateway.example/videocall/device-a"); stream.setAvailable(true);
        when(streamMapper.selectById(71L)).thenReturn(stream);
        return String.valueOf(cameraRtcTickets.issue(accessRef, 71L).get("ticket"));
    }

    private void assertCopiedTicketDeniedThenOriginalViewerCanConsume(String ticket) {
        assertEquals(Integer.valueOf(403), assertThrows(ServiceException.class, () -> cameraRtcTickets.consume(ticket)).getCode());
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from wvp_vlstream_camera_rtc_ticket where consumed_at is null", Integer.class));
        authenticate("requester", 100L);
        assertEquals("requester", cameraRtcTickets.consume(ticket).getViewerTenantId());
    }

    private String permission(String methodName) {
        try {
            PreAuthorize annotation = null;
            for (java.lang.reflect.Method method : VlStreamDeviceAccessService.class.getMethods()) {
                if (method.getName().equals(methodName)) annotation = method.getAnnotation(PreAuthorize.class);
            }
            return annotation == null ? "" : annotation.value();
        } catch (RuntimeException failure) {
            return "";
        }
    }

    private void schema() {
        jdbc.execute("create table wvp_vlstream_device(id bigint primary key,tenant_id varchar(64),device_name varchar(100),visibility varchar(16),online boolean,latitude varchar(50),longitude varchar(50))");
        jdbc.update("insert into wvp_vlstream_device values(7,'owner','Share fixture','PRIVATE',true,'22.5','114.1')");
        jdbc.execute("create table wvp_vlstream_device_access_ref(device_row_id bigint primary key,access_ref char(32) unique)");
        jdbc.execute("create table wvp_vlstream_access_request(request_id bigint auto_increment primary key,device_row_id bigint,requester_tenant_id varchar(64),requester_user_id bigint,request_reason varchar(1000),status varchar(16),resolved_grant_id bigint,created_at timestamp,updated_at timestamp,decided_at timestamp,decided_by_tenant_id varchar(64),decided_by_user_id bigint,decision_reason varchar(1000))");
        jdbc.execute("create table wvp_vlstream_access_grant(grant_id bigint auto_increment primary key,request_id bigint unique,device_row_id bigint,owner_tenant_id varchar(64),viewer_tenant_id varchar(64),approved_by_user_id bigint,expires_at timestamp,expired_at timestamp,revoked_at timestamp,create_time timestamp)");
        jdbc.execute("create table wvp_vlstream_access_audit(audit_id bigint auto_increment primary key,device_row_id bigint,actor_tenant_id varchar(64),actor_user_id bigint,action varchar(32),request_id bigint,grant_id bigint,detail varchar(1000),created_at timestamp)");
        jdbc.execute("create table wvp_vlstream_preview_lease(lease_id varchar(32) primary key,device_row_id bigint,stream_row_id bigint,viewer_tenant_id varchar(64),viewer_user_id bigint,grant_id bigint,media_server_id varchar(64),proxy_app varchar(64),proxy_stream varchar(128),proxy_key varchar(128),source_fingerprint char(64),state varchar(16),expires_at timestamp,proxy_deleted boolean,create_time timestamp,update_time timestamp)");
        jdbc.execute("create table wvp_vlstream_camera_rtc_ticket(ticket_hash char(64) primary key,device_row_id bigint,stream_row_id bigint,viewer_tenant_id varchar(64),viewer_user_id bigint,grant_id bigint,expires_at timestamp,consumed_at timestamp,create_time timestamp)");
    }

    @Test public void publicPreviewCreatesSafePerViewerProxyThatRenewsAndCloses() throws Exception {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        configureRtspMedia("rtsp://camera.example/live");

        Map<String, Object> started = previewService.start(accessRef, 70L);
        String leaseId = String.valueOf(started.get("sessionId"));
        assertTrue(leaseId.matches("[a-f0-9]{32}"));
        assertEquals("ZLM", started.get("playMode"));
        assertEquals(Long.valueOf(120L), started.get("expiresInSeconds"));
        @SuppressWarnings("unchecked") Map<String, Object> streamInfo = (Map<String, Object>) started.get("streamInfo");
        assertTrue(streamInfo.containsKey("flv"));
        assertFalse(streamInfo.containsKey("ip"));
        assertFalse(streamInfo.toString().contains("192.168."));
        assertEquals("requester", jdbc.queryForObject("select viewer_tenant_id from wvp_vlstream_preview_lease where lease_id=?", String.class, leaseId));
        assertNull(jdbc.queryForObject("select grant_id from wvp_vlstream_preview_lease where lease_id=?", Long.class, leaseId));

        assertEquals(Long.valueOf(120L), previewService.renew(accessRef, leaseId).get("expiresInSeconds"));
        previewService.close(accessRef, leaseId);
        assertEquals("CLOSED", jdbc.queryForObject("select state from wvp_vlstream_preview_lease where lease_id=?", String.class, leaseId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select proxy_deleted from wvp_vlstream_preview_lease where lease_id=?", Boolean.class, leaseId));
        verify(media).delStreamProxy(mediaServer, "proxy-key");
    }

    @Test public void privatePreviewRequiresGrantAndCameraRtcUsesOneUseTenantBoundTicket() throws Exception {
        VlStreamDeviceStream rtsp = configureRtspMedia("rtsp://camera.example/live");
        ServiceException denied = assertThrows(ServiceException.class, () -> previewService.start(accessRef, 70L));
        assertEquals(Integer.valueOf(403), denied.getCode());
        verify(media, never()).addStreamProxy(any(MediaServer.class), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyInt());

        device.setVisibility("PUBLIC");
        VlStreamDeviceStream cameraRtc = new VlStreamDeviceStream();
        cameraRtc.setId(71L); cameraRtc.setDeviceRowId(7L); cameraRtc.setProtocol("https");
        cameraRtc.setSourceUrl("https://gateway.example/videocall/device-a"); cameraRtc.setAvailable(true);
        when(streamMapper.selectById(71L)).thenReturn(cameraRtc);
        Map<String, Object> ticketResponse = previewService.start(accessRef, 71L);
        assertEquals("cameraRTC", ticketResponse.get("playMode"));
        assertTrue(String.valueOf(ticketResponse.get("ticket")).matches("[A-Za-z0-9_-]{43}"));
        String sessionId = String.valueOf(ticketResponse.get("sessionId"));
        assertTrue(sessionId.matches("[a-f0-9]{64}"));
        assertEquals(Integer.valueOf(30), ticketResponse.get("ticketExpiresInSeconds"));
        assertTrue(String.valueOf(ticketResponse.get("webSocketPath")).startsWith("/vlstream/device/shared/camera-rtc?ticket="));
        assertFalse(ticketResponse.toString().contains("gateway.example"));
        assertFalse(ticketResponse.toString().contains("device-a"));
        String ticket = String.valueOf(ticketResponse.get("ticket"));
        VlStreamDeviceCameraRtcTicketService.RelayAuthorization relay = cameraRtcTickets.consume(ticket);
        assertEquals("requester", relay.getViewerTenantId());
        assertEquals("device-a", relay.getCameraId());
        assertEquals(sessionId, relay.getSessionId());
        assertTrue(cameraRtcTickets.stillAuthorized(relay));
        assertEquals(Long.valueOf(120L), previewService.renew(accessRef, sessionId).get("expiresInSeconds"));
        authenticate("owner", 200L);
        ServiceException foreignSession = assertThrows(ServiceException.class, () -> previewService.renew(accessRef, sessionId));
        assertEquals(Integer.valueOf(403), foreignSession.getCode());
        authenticate("requester", 100L);
        ServiceException replay = assertThrows(ServiceException.class, () -> cameraRtcTickets.consume(ticket));
        assertEquals(Integer.valueOf(403), replay.getCode());
        device.setVisibility("PRIVATE");
        jdbc.update("update wvp_vlstream_device set visibility='PRIVATE' where id=7");
        assertFalse(cameraRtcTickets.stillAuthorized(relay));
        previewService.close(accessRef, sessionId);
        assertFalse(cameraRtcTickets.stillAuthorized(relay));
        verify(sourceProbe, never()).proxyUrl(any(), anyInt());
        assertNotNull(rtsp);
    }

    @Test public void cameraRtcRelayPinsTargetFiltersMessagesAndClosesAfterVisibilityChanges() throws Exception {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        VlStreamDeviceStream cameraRtc = new VlStreamDeviceStream();
        cameraRtc.setId(71L); cameraRtc.setDeviceRowId(7L); cameraRtc.setProtocol("https");
        cameraRtc.setSourceUrl("https://gateway.example/videocall/device-a"); cameraRtc.setAvailable(true);
        when(streamMapper.selectById(71L)).thenReturn(cameraRtc);

        try (LocalWebSocketServer gateway = new LocalWebSocketServer()) {
            cameraRtcProperties.setCameraRtcSignalUrl(gateway.url());
            Map<String, Object> issued = cameraRtcTickets.issue(accessRef, 71L);
            VlStreamDeviceCameraRtcTicketService.RelayAuthorization authorization =
                cameraRtcTickets.consume(String.valueOf(issued.get("ticket")));
            assertEquals(120L, cameraRtcTickets.renewSession(7L, authorization.getSessionId()));
            ObjectMapper mapper = new ObjectMapper();
            VlStreamCameraRtcRelayHandler handler = new VlStreamCameraRtcRelayHandler(mapper, cameraRtcTickets);

            WebSocketSession client = mock(WebSocketSession.class);
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(VlStreamCameraRtcHandshakeInterceptor.AUTHORIZATION_ATTRIBUTE, authorization);
            when(client.getAttributes()).thenReturn(attributes);
            when(client.getId()).thenReturn("browser-session-1");
            AtomicBoolean clientOpen = new AtomicBoolean(true);
            when(client.isOpen()).thenAnswer(invocation -> clientOpen.get());
            BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();
            doAnswer(invocation -> {
                TextMessage outgoing = (TextMessage) invocation.getArgument(0);
                clientMessages.add(outgoing.getPayload());
                return null;
            }).when(client).sendMessage(any(WebSocketMessage.class));
            doAnswer(invocation -> { clientOpen.set(false); return null; }).when(client).close(any(CloseStatus.class));

            try {
                handler.afterConnectionEstablished(client);
                assertTrue(gateway.awaitConnected());
                handler.handleTextMessage(client, new TextMessage("{\"eventName\":\"__connectto\",\"data\":{" +
                    "\"sessionId\":\"PCWEB-123\",\"to\":\"attacker-controlled-camera\"}}"));
                JsonNode connected = mapper.readTree(gateway.nextMessage());
                assertEquals("__connectto", connected.path("eventName").asText());
                assertEquals("device-a", connected.path("data").path("to").asText());
                assertNotEquals("attacker-controlled-camera", connected.path("data").path("to").asText());

                gateway.sendText("{\"eventName\":\"_create\",\"data\":{" +
                    "\"sessionId\":\"PCWEB-123\",\"state\":\"online\",\"user\":\"admin\",\"pwd\":\"device-secret\"," +
                    "\"iceServers\":\"{\\\"iceServers\\\":[{\\\"urls\\\":\\\"stun:stun.example.com:3478\\\"},{" +
                    "\\\"urls\\\":\\\"turn:turn.example.com:3478\\\",\\\"credential\\\":\\\"turn-secret\\\"}]}\"}}");
                JsonNode create = mapper.readTree(clientMessages.poll(5, TimeUnit.SECONDS));
                assertEquals("_create", create.path("eventName").asText());
                assertTrue(create.toString().contains("stun:stun.example.com:3478"));
                assertFalse(create.toString().contains("turn:turn.example.com"));
                assertFalse(create.toString().contains("turn-secret"));
                assertFalse(create.toString().contains("device-secret"));

                handler.handleTextMessage(client, new TextMessage("{\"eventName\":\"__ice_candidate\",\"data\":{" +
                    "\"sessionId\":\"PCWEB-123\",\"candidate\":\"{\\\"candidate\\\":\\\"candidate:1 1 udp 100 10.1.2.3 5000 typ host\\\",\\\"sdpMid\\\":\\\"0\\\"}\"}}"));
                assertNull(gateway.pollMessage(250, TimeUnit.MILLISECONDS));
                assertTrue(clientOpen.get());
                handler.handleTextMessage(client, new TextMessage("{\"eventName\":\"__ice_candidate\",\"data\":{" +
                    "\"sessionId\":\"PCWEB-123\",\"candidate\":\"{\\\"candidate\\\":\\\"candidate:2 1 udp 100 8.8.8.8 5001 typ srflx\\\",\\\"sdpMid\\\":\\\"0\\\"}\"}}"));
                JsonNode publicCandidate = mapper.readTree(gateway.nextMessage());
                assertTrue(publicCandidate.path("data").path("candidate").asText().contains("8.8.8.8"));

                handler.handleTextMessage(client, new TextMessage("{\"eventName\":\"__connectto\",\"data\":{" +
                    "\"sessionId\":\"PCWEB-NEW\",\"to\":\"device-a\"}}"));
                JsonNode oldDisconnected = mapper.readTree(gateway.nextMessage());
                assertEquals("__disconnected", oldDisconnected.path("eventName").asText());
                assertEquals("PCWEB-123", oldDisconnected.path("data").path("sessionId").asText());
                JsonNode newConnected = mapper.readTree(gateway.nextMessage());
                assertEquals("__connectto", newConnected.path("eventName").asText());
                assertEquals("PCWEB-NEW", newConnected.path("data").path("sessionId").asText());

                device.setVisibility("PRIVATE");
                jdbc.update("update wvp_vlstream_device set visibility='PRIVATE' where id=7");
                handler.handleTextMessage(client, new TextMessage("{\"eventName\":\"__ping\",\"data\":{" +
                    "\"sessionId\":\"PCWEB-123\",\"to\":\"device-a\"}}"));
                assertFalse(clientOpen.get());
                JsonNode disconnected = mapper.readTree(gateway.nextMessage());
                assertEquals("__disconnected", disconnected.path("eventName").asText());
                assertEquals("device-a", disconnected.path("data").path("to").asText());
                assertEquals("PCWEB-NEW", disconnected.path("data").path("sessionId").asText());
                assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                    "select case when expires_at<=NOW() then 0 else 1 end from wvp_vlstream_camera_rtc_ticket where ticket_hash=?",
                    Integer.class, authorization.getSessionId()));
            } finally {
                handler.shutdown();
            }
        }
    }

    @Test public void cameraRtcSessionLeaseExpiresAndCannotBeRenewedAfterExpiry() {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        VlStreamDeviceStream cameraRtc = new VlStreamDeviceStream();
        cameraRtc.setId(71L); cameraRtc.setDeviceRowId(7L); cameraRtc.setProtocol("https");
        cameraRtc.setSourceUrl("https://gateway.example/videocall/device-a"); cameraRtc.setAvailable(true);
        when(streamMapper.selectById(71L)).thenReturn(cameraRtc);

        Map<String, Object> issued = cameraRtcTickets.issue(accessRef, 71L);
        String sessionId = String.valueOf(issued.get("sessionId"));
        VlStreamDeviceCameraRtcTicketService.RelayAuthorization relay =
            cameraRtcTickets.consume(String.valueOf(issued.get("ticket")));
        assertTrue(cameraRtcTickets.stillAuthorized(relay));
        jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=TIMESTAMPADD(SECOND,-1,NOW()) where ticket_hash=?", sessionId);
        assertFalse(cameraRtcTickets.stillAuthorized(relay));
        ServiceException expired = assertThrows(ServiceException.class, () -> previewService.renew(accessRef, sessionId));
        assertEquals(Integer.valueOf(404), expired.getCode());
    }

    @Test public void sharedPlaybackUrlsRejectPrivateAddressesAndInternalDnsNames() {
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(previewService, "safePlaybackUrl", "http://192.168.1.20:8080/live.flv"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(previewService, "safePlaybackUrl", "http://[::ffff:192.168.1.20]:8080/live.flv"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(previewService, "safePlaybackUrl", "http://apaas-zlmediakit:8080/live.flv"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(previewService, "safePlaybackUrl", "http://zlm.cluster.local:8080/live.flv"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(previewService, "safePlaybackUrl", "http://user:secret@media.example.com:8080/live.flv"));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(previewService, "safePlaybackUrl", "https://media.example.com/live.flv"));
    }

    @Test public void revokingGrantPreventsRenewalAndDeletesItsProxy() throws Exception {
        Long requestId = Long.valueOf(String.valueOf(service.request(accessRef, null).get("requestId")));
        authenticate("owner", 200L);
        Long grantId = Long.valueOf(String.valueOf(service.approve(requestId, 1).get("grantId")));
        authenticate("requester", 100L);
        configureRtspMedia("rtsp://camera.example/live");
        String leaseId = String.valueOf(previewService.start(accessRef, 70L).get("sessionId"));
        assertEquals(grantId, jdbc.queryForObject("select grant_id from wvp_vlstream_preview_lease where lease_id=?", Long.class, leaseId));

        authenticate("owner", 200L);
        service.revoke(grantId);
        authenticate("requester", 100L);
        ServiceException revoked = assertThrows(ServiceException.class, () -> previewService.renew(accessRef, leaseId));
        assertEquals(Integer.valueOf(403), revoked.getCode());
        assertEquals("REVOKED", jdbc.queryForObject("select state from wvp_vlstream_preview_lease where lease_id=?", String.class, leaseId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select proxy_deleted from wvp_vlstream_preview_lease where lease_id=?", Boolean.class, leaseId));
        verify(media).delStreamProxy(mediaServer, "proxy-key");
    }

    private VlStreamDeviceStream configureRtspMedia(String sourceUrl) {
        VlStreamDeviceStream stream = new VlStreamDeviceStream();
        stream.setId(70L); stream.setDeviceRowId(7L); stream.setProtocol("RTSP");
        stream.setSourceUrl(sourceUrl); stream.setAvailable(true);
        when(streamMapper.selectById(70L)).thenReturn(stream);
        when(sourceProbe.proxyUrl(any(com.ruoyi.custom.availability.CustomStreamSpec.class), eq(3000)))
            .thenReturn("rtsp://198.51.100.10/live");

        mediaServer = new MediaServer(); mediaServer.setId("ms1"); mediaServer.setStatus(true);
        when(media.getDefaultMediaServer()).thenReturn(mediaServer);
        when(media.getOne("ms1")).thenReturn(mediaServer);
        when(media.addStreamProxy(eq(mediaServer), eq("vlstream_share"), anyString(), eq("rtsp://198.51.100.10/live"),
                eq(true), eq(false), eq("0"), eq(15))).thenReturn(WVPResult.success("proxy-key"));
        StreamInfo info = new StreamInfo(); info.setFlv("media.example.com", 8080, 0, "vlstream_share/share.live.flv");
        when(media.getStreamInfoByAppAndStream(eq(mediaServer), eq("vlstream_share"), anyString(), isNull(), isNull()))
            .thenReturn(info);
        when(media.delStreamProxy(eq(mediaServer), anyString())).thenReturn(true);
        return stream;
    }

    @Test public void failedPreviewKeepsDurableCleanupUntilMediaDeletionSucceeds() {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        configureRtspMedia("rtsp://camera.example/live");
        when(media.getStreamInfoByAppAndStream(eq(mediaServer), eq("vlstream_share"), anyString(), isNull(), isNull()))
            .thenReturn(null);
        when(media.delStreamProxy(mediaServer, "proxy-key")).thenReturn(false);

        assertThrows(ServiceException.class, () -> previewService.start(accessRef, 70L));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
            "select count(*) from wvp_vlstream_preview_lease where state='FAILED' and proxy_key='proxy-key' and proxy_deleted=false", Integer.class));
        previewService.expireAndRetryCleanup();
        verify(media, times(2)).delStreamProxy(mediaServer, "proxy-key");
        when(media.delStreamProxy(mediaServer, "proxy-key")).thenReturn(true);
        previewService.expireAndRetryCleanup();
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select proxy_deleted from wvp_vlstream_preview_lease", Boolean.class));
    }

    @Test public void proxyCreationIsReservedBeforeCallingMediaAndUnknownResultRemainsRecoverable() {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        configureRtspMedia("rtsp://camera.example/live");
        when(media.addStreamProxy(eq(mediaServer), eq("vlstream_share"), anyString(), anyString(),
                eq(true), eq(false), eq("0"), eq(15))).thenAnswer(invocation -> {
            assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "select count(*) from wvp_vlstream_preview_lease where state='CREATING' and proxy_deleted=false", Integer.class));
            throw new IllegalStateException("media response lost after accepting creation");
        });
        when(media.delStreamProxy(eq(mediaServer), anyString())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> previewService.start(accessRef, 70L));
        Map<String, Object> row = jdbc.queryForMap("select * from wvp_vlstream_preview_lease");
        assertEquals("CANCEL_REQUESTED", row.get("state"));
        assertEquals("__defaultVhost__/vlstream_share/" + row.get("proxy_stream"), row.get("proxy_key"));
        assertEquals(Boolean.FALSE, row.get("proxy_deleted"));
        previewService.expireAndRetryCleanup();
        verify(media, never()).delStreamProxy(any(MediaServer.class), anyString());
        jdbc.update("update wvp_vlstream_preview_lease set expires_at=TIMESTAMPADD(SECOND,-1,NOW())");
        when(media.delStreamProxy(eq(mediaServer), anyString())).thenReturn(true);
        previewService.expireAndRetryCleanup();
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select proxy_deleted from wvp_vlstream_preview_lease", Boolean.class));
    }

    @Test public void revocationDuringProxyCreationCannotActivateOrConfirmPrematureDeletion() {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        configureRtspMedia("rtsp://camera.example/live");
        when(media.addStreamProxy(eq(mediaServer), eq("vlstream_share"), anyString(), anyString(),
                eq(true), eq(false), eq("0"), eq(15))).thenAnswer(invocation -> {
            previewService.onAccessChanged(new VlStreamDeviceAccessChangedEvent(7L, null));
            assertEquals("CANCEL_REQUESTED", jdbc.queryForObject("select state from wvp_vlstream_preview_lease", String.class));
            previewService.expireAndRetryCleanup();
            verify(media, never()).delStreamProxy(any(MediaServer.class), anyString());
            return WVPResult.success("proxy-key");
        });
        when(media.delStreamProxy(mediaServer, "proxy-key")).thenReturn(false);

        ServiceException cancelled = assertThrows(ServiceException.class, () -> previewService.start(accessRef, 70L));
        assertEquals(Integer.valueOf(409), cancelled.getCode());
        assertEquals("FAILED", jdbc.queryForObject("select state from wvp_vlstream_preview_lease", String.class));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("select proxy_deleted from wvp_vlstream_preview_lease", Boolean.class));
        verify(media).delStreamProxy(mediaServer, "proxy-key");
        when(media.delStreamProxy(mediaServer, "proxy-key")).thenReturn(true);
        previewService.expireAndRetryCleanup();
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select proxy_deleted from wvp_vlstream_preview_lease", Boolean.class));
    }

    @Test public void abandonedCreationReservationIsRecoveredByTheCleanupScheduler() {
        configureRtspMedia("rtsp://camera.example/live");
        for (String state : Arrays.asList("CREATING", "CANCEL_REQUESTED")) {
            String lease = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("insert into wvp_vlstream_preview_lease(lease_id,device_row_id,media_server_id,proxy_key,state,expires_at,proxy_deleted) " +
                "values (?,7,'ms1',?,?,TIMESTAMPADD(SECOND,-1,NOW()),false)", lease, "__defaultVhost__/vlstream_share/share_" + lease, state);
        }
        when(media.delStreamProxy(eq(mediaServer), anyString())).thenThrow(new IllegalStateException("temporary media failure"));
        previewService.expireAndRetryCleanup();
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("select count(*) from wvp_vlstream_preview_lease where state='FAILED' and proxy_deleted=false", Integer.class));
        when(media.delStreamProxy(eq(mediaServer), anyString())).thenReturn(true);
        previewService.expireAndRetryCleanup();
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("select count(*) from wvp_vlstream_preview_lease where state='FAILED' and proxy_deleted=true", Integer.class));
    }

    @Test public void afterCommitRevocationPersistsBeforeTheOriginalTransactionResourcesAreReleased() {
        device.setVisibility("PUBLIC");
        jdbc.update("update wvp_vlstream_device set visibility='PUBLIC' where id=7");
        configureRtspMedia("rtsp://camera.example/live");
        String lease = String.valueOf(previewService.start(accessRef, 70L).get("sessionId"));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        previewService.onAccessChanged(new VlStreamDeviceAccessChangedEvent(7L, null));
                        try (java.sql.Connection independent = ((DataSource) h2Pool).getConnection();
                                java.sql.Statement statement = independent.createStatement();
                                java.sql.ResultSet rows = statement.executeQuery(
                                    "select state,proxy_deleted from wvp_vlstream_preview_lease where lease_id='" + lease + "'")) {
                            assertTrue(rows.next());
                            assertEquals("REVOKED", rows.getString(1));
                            assertTrue(rows.getBoolean(2));
                        } catch (java.sql.SQLException failure) {
                            throw new IllegalStateException(failure);
                        }
                    }
                });
            return null;
        });
    }

    @Test public void failedCleanupRecordsRotateSoLaterLeasesAreNotStarved() {
        configureRtspMedia("rtsp://camera.example/live");
        for (int i = 0; i <= 200; i++) {
            jdbc.update("insert into wvp_vlstream_preview_lease(lease_id,device_row_id,media_server_id,proxy_key,state,expires_at,proxy_deleted,update_time) " +
                "values (?,7,'ms1',?,'FAILED',NOW(),false,TIMESTAMPADD(SECOND,?,NOW()))",
                String.format("%032x", i), "cleanup-" + i, i - 1000);
        }
        when(media.delStreamProxy(eq(mediaServer), anyString())).thenReturn(false);
        when(media.delStreamProxy(mediaServer, "cleanup-200")).thenReturn(true);
        previewService.expireAndRetryCleanup();
        previewService.expireAndRetryCleanup();
        verify(media).delStreamProxy(mediaServer, "cleanup-200");
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select proxy_deleted from wvp_vlstream_preview_lease where proxy_key='cleanup-200'", Boolean.class));
    }

    @Test public void defaultTenantOwnerPrivateLeaseSurvivesLegacyBlankOwnership() {
        configureRtspMedia("rtsp://camera.example/live");
        jdbc.update("update wvp_vlstream_device set tenant_id='   ',visibility='PRIVATE' where id=7");
        String lease = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into wvp_vlstream_preview_lease(lease_id,device_row_id,viewer_tenant_id,media_server_id,proxy_key,state,expires_at,proxy_deleted) " +
            "values (?,7,'default','ms1','legacy-owner','ACTIVE',TIMESTAMPADD(SECOND,60,NOW()),false)", lease);
        previewService.expireAndRetryCleanup();
        assertEquals("ACTIVE", jdbc.queryForObject("select state from wvp_vlstream_preview_lease where lease_id=?", String.class, lease));
        verify(media, never()).delStreamProxy(any(MediaServer.class), anyString());
    }

    /** Minimal loopback-only RFC 6455 peer; no external CameraRTC endpoint is contacted by this test. */
    private static final class LocalWebSocketServer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private final CountDownLatch connected = new CountDownLatch(1);
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private volatile Socket socket;
        private volatile BufferedOutputStream output;

        private LocalWebSocketServer() throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            worker.submit(this::serve);
        }

        private String url() { return "ws://127.0.0.1:" + listener.getLocalPort(); }
        private boolean awaitConnected() throws InterruptedException { return connected.await(5, TimeUnit.SECONDS); }
        private String nextMessage() throws InterruptedException { return messages.poll(5, TimeUnit.SECONDS); }
        private String pollMessage(long timeout, TimeUnit unit) throws InterruptedException { return messages.poll(timeout, unit); }

        private void sendText(String text) throws IOException {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            BufferedOutputStream target = output;
            if (target == null) throw new IOException("websocket handshake is not complete");
            synchronized (target) {
                target.write(0x81);
                if (payload.length < 126) {
                    target.write(payload.length);
                } else {
                    target.write(126);
                    target.write((payload.length >>> 8) & 0xff);
                    target.write(payload.length & 0xff);
                }
                target.write(payload);
                target.flush();
            }
        }

        private void serve() {
            try (Socket accepted = listener.accept()) {
                socket = accepted;
                accepted.setSoTimeout(500);
                BufferedInputStream input = new BufferedInputStream(accepted.getInputStream());
                BufferedReader headers = new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1));
                String firstLine = headers.readLine();
                if (firstLine == null || !firstLine.startsWith("GET /wswebclient/")) throw new IOException("invalid websocket path");
                String key = null;
                String line;
                while ((line = headers.readLine()) != null && !line.isEmpty()) {
                    if (line.regionMatches(true, 0, "Sec-WebSocket-Key:", 0, "Sec-WebSocket-Key:".length())) {
                        key = line.substring(line.indexOf(':') + 1).trim();
                    }
                }
                if (key == null) throw new IOException("missing websocket key");
                output = new BufferedOutputStream(accepted.getOutputStream());
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.ISO_8859_1)));
                output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                output.flush();
                connected.countDown();
                DataInputStream frames = new DataInputStream(input);
                while (running.get()) {
                    try {
                        String message = readFrame(frames, output);
                        if (message != null) messages.offer(message);
                    } catch (SocketTimeoutException timeout) {
                        // Let close() terminate the loop without blocking the test thread.
                    }
                }
            } catch (Exception failure) {
                if (running.get()) messages.offer("__test_error__" + failure.getClass().getSimpleName());
            } finally {
                connected.countDown();
            }
        }

        private static String readFrame(DataInputStream input, BufferedOutputStream output) throws IOException {
            int first = input.readUnsignedByte();
            int second = input.readUnsignedByte();
            int opcode = first & 0x0f;
            long length = second & 0x7f;
            if (length == 126) length = input.readUnsignedShort();
            else if (length == 127) length = input.readLong();
            if (length > 65536) throw new IOException("oversized test frame");
            boolean masked = (second & 0x80) != 0;
            byte[] mask = new byte[masked ? 4 : 0];
            if (masked) input.readFully(mask);
            byte[] payload = new byte[(int) length];
            input.readFully(payload);
            if (masked) for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            if (opcode == 8) return null;
            if (opcode == 9) {
                synchronized (output) {
                    output.write(0x8a); output.write(payload.length); output.write(payload); output.flush();
                }
                return null;
            }
            return opcode == 1 ? new String(payload, StandardCharsets.UTF_8) : null;
        }

        @Override public void close() throws IOException {
            running.set(false);
            if (socket != null) socket.close();
            listener.close();
            worker.shutdownNow();
        }
    }
}
