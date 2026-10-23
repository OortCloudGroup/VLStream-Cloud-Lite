package com.ruoyi.vlstream.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.availability.CustomStreamProbe;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.vlstream.config.VlStreamCameraRtcProperties;
import com.ruoyi.vlstream.config.VlStreamDeviceAccessProperties;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual permission AOP + trusted tenant context + in-memory ticket persistence; no device/network calls. */
public class VlStreamOwnerPlaybackAuthorizationTest {
    private static final Long DEVICE_ID = 71L;
    private static final Long STREAM_ID = 81L;
    private static final String REF = "0123456789abcdef0123456789abcdef";
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private VlStreamDevice device;
    private VlStreamDeviceAccessService access;
    private VlStreamDeviceCameraRtcTicketService tickets;
    private VlStreamDevicePreviewLeaseService previews;
    private IMediaServerService media;
    private PermissionService permissions;

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    public static class SecurityConfiguration { }

    @Before public void setup() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        DriverManagerDataSource source = new DriverManagerDataSource(
            "jdbc:h2:mem:owner_playback_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table wvp_vlstream_device_access_ref(device_row_id bigint,access_ref varchar(32))");
        jdbc.execute("create table wvp_vlstream_access_grant(grant_id bigint,device_row_id bigint,viewer_tenant_id varchar(64),revoked_at timestamp,expires_at timestamp)");
        jdbc.execute("create table wvp_vlstream_camera_rtc_ticket(ticket_hash varchar(64) primary key,device_row_id bigint,stream_row_id bigint,viewer_tenant_id varchar(64),viewer_user_id bigint,grant_id bigint,expires_at timestamp,create_time timestamp,consumed_at timestamp)");
        jdbc.execute("create table wvp_vlstream_access_audit(device_row_id bigint,actor_tenant_id varchar(64),actor_user_id bigint,action varchar(64),grant_id bigint,detail varchar(1000),created_at timestamp)");
        jdbc.update("insert into wvp_vlstream_device_access_ref values (?,?)", DEVICE_ID, REF);
        device = new VlStreamDevice();
        device.setId(DEVICE_ID); device.setTenantId("owner"); device.setVisibility("PRIVATE"); device.setOnline(true);
        VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
        when(devices.selectById(DEVICE_ID)).thenReturn(device);
        when(devices.selectByIdForUpdate(DEVICE_ID)).thenReturn(device);
        VlStreamDeviceProperties tenantProperties = new VlStreamDeviceProperties();
        tenantProperties.setDefaultTenantId("default");
        VlStreamDeviceTenantService tenants = new VlStreamDeviceTenantService(tenantProperties, devices);
        VlStreamDeviceAccessRefService refs = new VlStreamDeviceAccessRefService(jdbc);
        VlStreamDeviceAccessProperties properties = new VlStreamDeviceAccessProperties();
        VlStreamDeviceStreamMapper streams = mock(VlStreamDeviceStreamMapper.class);
        VlStreamDeviceStream stream = new VlStreamDeviceStream();
        stream.setId(STREAM_ID); stream.setDeviceRowId(DEVICE_ID); stream.setAvailable(true);
        stream.setProtocol("https"); stream.setSourceUrl("https://camera.example/videocall/camera71");
        when(streams.selectById(STREAM_ID)).thenReturn(stream);
        when(streams.selectAvailableByDeviceId(DEVICE_ID)).thenReturn(Collections.singletonList(stream));
        VlStreamCameraRtcProperties rtcProperties = new VlStreamCameraRtcProperties();
        rtcProperties.setCameraRtcSignalUrl("wss://camera.example");
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);

        context = new AnnotationConfigApplicationContext();
        context.register(SecurityConfiguration.class);
        context.registerBean("ss", PermissionService.class);
        context.registerBean(VlStreamDeviceOwnerPlaybackGuard.class, () -> new VlStreamDeviceOwnerPlaybackGuard(tenants));
        context.registerBean(DataSourceTransactionManager.class, () -> transactions);
        context.registerBean(VlStreamDeviceAccessService.class, () -> new VlStreamDeviceAccessService(jdbc, devices,
            tenants, refs, properties, context));
        context.registerBean(VlStreamDeviceCameraRtcTicketService.class, () -> new VlStreamDeviceCameraRtcTicketService(
            jdbc, devices, streams, tenants, context.getBean(VlStreamDeviceAccessService.class), properties, rtcProperties));
        context.refresh();
        access = AopTestUtils.getTargetObject(context.getBean(VlStreamDeviceAccessService.class));
        tickets = context.getBean(VlStreamDeviceCameraRtcTicketService.class);
        permissions = context.getBean(PermissionService.class);
        media = mock(IMediaServerService.class);
        previews = new VlStreamDevicePreviewLeaseService(jdbc, devices, streams, tenants, refs, access, tickets,
            properties, media, mock(CustomStreamProbe.class), transactions);
        login("owner");
    }

    @After public void cleanup() {
        if (context != null) context.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test public void nativeOwnerWithoutOriginalPlayCannotReadPrivateStreams() {
        assertFalse(permissions.hasPermi("vlstream:device:play"));
        denied(() -> previews.streams(REF));
    }

    @Test public void nativeOwnerWithoutOriginalPlayCannotStartPrivateSharedPreview() {
        denied(() -> previews.start(REF, STREAM_ID));
        verifyNoInteractions(media);
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from wvp_vlstream_camera_rtc_ticket", Integer.class));
    }

    @Test public void nativeOwnerWithoutOriginalPlayCannotMintPrivateCameraRtcTicket() {
        denied(() -> tickets.issue(REF, STREAM_ID));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from wvp_vlstream_camera_rtc_ticket", Integer.class));
    }

    @Test public void explicitOriginalPlayAndTrustedOwnerTenantAreRequiredTogether() {
        login("owner", "vlstream:device:play");
        assertTrue(permissions.hasPermi("vlstream:device:play"));
        assertNull(access.requireReadAccess(device, "owner"));
        assertEquals(1, previews.streams(REF).size());
        assertNotNull(tickets.issue(REF, STREAM_ID).get("ticket"));
    }

    @Test public void legacyOwnerGuardKeepsTenantScopeEvenWithPlayPermission() {
        login("foreign", "vlstream:device:play");
        denied(() -> context.getBean(VlStreamDeviceOwnerPlaybackGuard.class).requireOwnerPlayback(DEVICE_ID));
    }

    @Test public void publicForeignViewerUsesSeparateReadOnlyEntitlementWithoutOwnerPlayPermission() {
        login("foreign");
        device.setVisibility("PUBLIC");
        assertFalse(permissions.hasPermi("vlstream:device:play"));
        assertNull(access.requireReadAccess(device, "foreign"));
        assertNotNull(tickets.issue(REF, STREAM_ID).get("ticket"));
    }

    @Test public void grantedForeignViewerUsesOnlyItsActiveGrantWithoutOwnerPlayPermission() {
        login("foreign");
        jdbc.update("insert into wvp_vlstream_access_grant values (991,?,'foreign',null,TIMESTAMPADD(DAY,1,NOW()))", DEVICE_ID);
        assertFalse(permissions.hasPermi("vlstream:device:play"));
        assertEquals(Long.valueOf(991L), access.requireReadAccess(device, "foreign"));
        assertNotNull(tickets.issue(REF, STREAM_ID).get("ticket"));
    }

    @Test public void privateForeignViewerWithoutGrantRemainsDenied() {
        login("foreign", "vlstream:device:play");
        denied(() -> access.requireReadAccess(device, "foreign"));
    }

    @Test public void callerSuppliedViewerTenantCannotImpersonateOwner() {
        login("foreign", "vlstream:device:play");
        denied(() -> access.requireReadAccess(device, "owner"));
    }

    @Test public void issuedOwnerTicketNeedsIssuingSessionAtHandshakeButBackgroundRechecksNeedNoLogin() {
        login("owner", "vlstream:device:play");
        Map<String, Object> issued = tickets.issue(REF, STREAM_ID);
        SecurityContextHolder.clearContext();
        denied(() -> tickets.consume((String) issued.get("ticket")));
        login("owner", "vlstream:device:play");
        VlStreamDeviceCameraRtcTicketService.RelayAuthorization authorization = tickets.consume((String) issued.get("ticket"));
        SecurityContextHolder.clearContext();
        assertTrue(tickets.stillAuthorized(authorization));
        login("owner");
        denied(() -> tickets.renewSession(DEVICE_ID, (String) issued.get("sessionId")));
        SecurityContextHolder.clearContext();
        assertFalse(tickets.stillAuthorized(authorization));
    }

    @Test public void ticketCapabilityCannotOutliveChangedPrivateOwnership() {
        login("owner", "vlstream:device:play");
        Map<String, Object> issued = tickets.issue(REF, STREAM_ID);
        SecurityContextHolder.clearContext();
        device.setTenantId("new-owner");
        login("owner", "vlstream:device:play");
        denied(() -> tickets.consume((String) issued.get("ticket")));
    }

    private static void denied(Runnable action) {
        try {
            action.run();
            fail("Expected 403 access denial");
        } catch (ServiceException denial) {
            assertEquals(Integer.valueOf(403), denial.getCode());
        } catch (AccessDeniedException denial) {
            // Real method-security denial of the legacy owner guard is also a 403.
        }
    }

    private static void login(String tenant, String... granted) {
        LoginUser user = new LoginUser();
        user.setTenantId(tenant); user.setUserId(101L); user.setFederated(false);
        user.setToken("owner-playback-fixture-session-" + tenant);
        user.setPermissions(new HashSet<>(Arrays.asList(granted)));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }
}
