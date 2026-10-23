package com.ruoyi.vlstream.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.vlstream.config.VlStreamDeviceAccessProperties;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.controller.VlStreamDeviceController;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real H2 SQL, transaction and method-security checks; no application services or live databases start. */
public class VlStreamDeviceOwnerGrantHistoryTest {
    private static final String APPROVAL_PERMISSION = "vlstream:device:share:approve";
    private URLClassLoader h2Loader;
    private Object h2Pool;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private VlStreamDeviceAccessService service;
    private VlStreamDeviceController controller;
    private VlStreamDevice device;
    private MockMvc api;

    @Before public void setup() throws Exception {
        String jar = System.getProperty("video.square.h2Jar");
        Assume.assumeTrue("Optional cached H2 jar was not supplied", jar != null && new File(jar).isFile());
        h2Loader = new URLClassLoader(new URL[]{new File(jar).toURI().toURL()}, getClass().getClassLoader());
        Class<?> poolClass = h2Loader.loadClass("org.h2.jdbcx.JdbcConnectionPool");
        h2Pool = poolClass.getMethod("create", String.class, String.class, String.class).invoke(null,
            "jdbc:h2:mem:owner_grants_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        DataSource source = (DataSource) h2Pool;
        jdbc = new JdbcTemplate(source);
        schema();
        fixture();

        device = new VlStreamDevice();
        device.setId(7L); device.setTenantId("owner"); device.setDeviceName("Owner fixture");
        VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
        when(devices.selectById(7L)).thenReturn(device);
        when(devices.selectByIdForUpdate(7L)).thenAnswer(invocation -> {
            jdbc.queryForObject("select id from wvp_vlstream_device where id=7 for update", Long.class);
            return device;
        });
        VlStreamDeviceProperties tenantProperties = new VlStreamDeviceProperties();
        tenantProperties.setDefaultTenantId("default");
        VlStreamDeviceTenantService tenants = new VlStreamDeviceTenantService(tenantProperties, devices);
        VlStreamDeviceAccessService target = new VlStreamDeviceAccessService(jdbc, devices, tenants,
            new VlStreamDeviceAccessRefService(jdbc), new VlStreamDeviceAccessProperties(), mock(ApplicationEventPublisher.class));
        context = new AnnotationConfigApplicationContext();
        context.register(SecurityFixture.class);
        context.getBeanFactory().registerSingleton("ss", new PermissionsFixture());
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(VlStreamDeviceOwnerPlaybackGuard.class, () -> new VlStreamDeviceOwnerPlaybackGuard(tenants));
        context.registerBean(VlStreamDeviceAccessService.class, () -> target);
        context.registerBean(VlStreamDevicePreviewLeaseService.class, () -> mock(VlStreamDevicePreviewLeaseService.class));
        context.registerBean(VlStreamDeviceController.class, () -> new VlStreamDeviceController(devices,
            mock(VlStreamDeviceStreamMapper.class), mock(IMediaServerService.class),
            mock(VlStreamFirmwareDeploymentService.class), tenants));
        context.refresh();
        service = context.getBean(VlStreamDeviceAccessService.class);
        controller = context.getBean(VlStreamDeviceController.class);
        api = MockMvcBuilders.standaloneSetup(controller).build();
        authenticate("owner", 200L, true);
    }

    @After public void cleanup() throws Exception {
        SecurityContextHolder.clearContext();
        if (context != null) context.close();
        if (h2Pool != null) h2Pool.getClass().getMethod("dispose").invoke(h2Pool);
        if (h2Loader != null) h2Loader.close();
    }

    @Test public void endpointReturnsStablePagesAndOnlySafeOwnerScopedGrantMetadata() throws Exception {
        MvcResult result = api.perform(get("/vlstream/device/7/access-grants")
                .param("pageNum", "1").param("pageSize", "2")
                .param("tenantId", "foreign").param("ownerTenantId", "foreign")
                .param("orderByColumn", "viewer_tenant_id"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(4))
            .andExpect(jsonPath("$.data.pageNum").value(1)).andExpect(jsonPath("$.data.pageSize").value(2))
            .andExpect(jsonPath("$.data.status").value("ALL"))
            .andExpect(jsonPath("$.data.rows[0].grantId").value("104"))
            .andExpect(jsonPath("$.data.rows[0].expirationAuditId").value("503"))
            .andExpect(jsonPath("$.data.rows[1].grantId").value("103"))
            .andExpect(jsonPath("$.data.rows[1].requestId").value("12"))
            .andExpect(jsonPath("$.data.rows[1].requesterTenantId").value("viewer-c"))
            .andExpect(jsonPath("$.data.rows[1].requesterUserId").value("112"))
            .andExpect(jsonPath("$.data.rows[1].approvedByUserId").value("200"))
            .andExpect(jsonPath("$.data.rows[1].revocationAuditId").value("500"))
            .andExpect(jsonPath("$.data.rows[1].revokedByUserId").value("201"))
            .andReturn();
        String json = result.getResponse().getContentAsString();
        for (String absent : new String[]{"foreign-owner-reason", "other-device-reason", "audit-secret", "deviceRowId",
                "deviceId", "serial", "sourceUrl", "ipAddress", "ticket", "actor_tenant_id"}) {
            assertFalse("Unexpected disclosure: " + absent, json.contains(absent));
        }
        List<Map<String, Object>> secondPage = rows(service.ownerGrants(7L, 2, 2, null));
        assertEquals("102", secondPage.get(0).get("grantId"));
        assertEquals("101", secondPage.get(1).get("grantId"));
        assertNotNull(service.ownerGrants(7L, null, null, null).get("asOf"));
    }

    @Test public void stateFiltersUseDatabaseDeadlineAndDoNotFabricateExpiryAudit() {
        int before = jdbc.queryForObject("select count(*) from wvp_vlstream_access_audit", Integer.class);
        Map<String, Object> expired = service.ownerGrants(7L, 1, 100, " expired ");
        assertEquals(Long.valueOf(2L), expired.get("total"));
        Map<String, Object> notProcessed = find(expired, "102");
        assertEquals("EXPIRED", notProcessed.get("status"));
        assertEquals(Boolean.FALSE, notProcessed.get("canRevoke"));
        assertNull(notProcessed.get("expiredAt"));
        assertNull(notProcessed.get("expirationAuditId"));
        Map<String, Object> active = service.ownerGrants(7L, null, null, "ACTIVE");
        assertEquals(Long.valueOf(1L), active.get("total"));
        assertEquals(Boolean.TRUE, find(active, "101").get("canRevoke"));
        Map<String, Object> revoked = service.ownerGrants(7L, null, null, "REVOKED");
        assertEquals(Long.valueOf(1L), revoked.get("total"));
        assertEquals("REVOKED", find(revoked, "103").get("status"));
        assertEquals(before, jdbc.queryForObject("select count(*) from wvp_vlstream_access_audit", Integer.class).intValue());
    }

    @Test public void serviceAndControllerEnforceApprovalPermissionAndTrustedOwnerTenant() {
        authenticate("owner", 200L, false);
        assertThrows(AccessDeniedException.class, () -> service.ownerGrants(7L, null, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.ownerGrants(7L, null, null, null));
        authenticate("requester", 100L, true);
        ServiceException foreign = assertThrows(ServiceException.class, () -> service.ownerGrants(7L, null, null, null));
        assertEquals(Integer.valueOf(403), foreign.getCode());
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.ownerGrants(7L, null, null, null));
    }

    @Test public void queryRechecksDeviceOwnershipAndDoesNotRevealPreviousOwnerHistory() {
        // Even a stale mapper answer must not bypass the SQL ownership predicate.
        jdbc.update("update wvp_vlstream_device set tenant_id='foreign' where id=7");
        assertEquals(Long.valueOf(0L), service.ownerGrants(7L, 1, 20, null).get("total"));
        device.setTenantId("foreign");
        authenticate("foreign", 300L, true);
        Map<String, Object> current = service.ownerGrants(7L, 1, 20, null);
        assertEquals(Long.valueOf(1L), current.get("total"));
        assertEquals("105", rows(current).get(0).get("grantId"));
    }

    @Test public void invalidFiltersAreRejectedAndLargePagesCannotOverflowTheOffset() throws Exception {
        for (int[] page : new int[][]{{0, 20}, {-1, 20}, {1, 0}, {1, -1}, {1, 101}}) {
            ServiceException denied = assertThrows(ServiceException.class, () -> service.ownerGrants(7L, page[0], page[1], null));
            assertEquals(Integer.valueOf(400), denied.getCode());
        }
        ServiceException invalidStatus = assertThrows(ServiceException.class,
            () -> service.ownerGrants(7L, 1, 20, "ACTIVE' OR 1=1"));
        assertEquals(Integer.valueOf(400), invalidStatus.getCode());
        Map<String, Object> huge = service.ownerGrants(7L, Integer.MAX_VALUE, 100, "ALL");
        assertEquals(Long.valueOf(4L), huge.get("total"));
        assertTrue(rows(huge).isEmpty());
        api.perform(get("/vlstream/device/7/access-grants").param("pageNum", "invalid"))
            .andExpect(status().isBadRequest());
    }

    @Test public void mismatchedRequestMetadataCannotDiscloseAnotherTenantApplicant() {
        jdbc.update("update wvp_vlstream_access_request set requester_tenant_id='unrelated',requester_user_id=777," +
            "request_reason='unrelated-private-reason' where request_id=12");
        Map<String, Object> row = find(service.ownerGrants(7L, 1, 20, "REVOKED"), "103");
        assertEquals("viewer-c", row.get("requesterTenantId"));
        assertNull(row.get("requesterUserId"));
        assertNull(row.get("reason"));
        assertNull(row.get("requestCreatedAt"));
        assertEquals("500", row.get("revocationAuditId"));
    }

    @Test public void legacyBlankDeviceTenantUsesOnlyTheTrustedDefaultTenant() {
        jdbc.update("update wvp_vlstream_access_grant set owner_tenant_id='default' where grant_id=101");
        authenticate("default", 200L, true);
        for (String legacyTenant : new String[]{null, "", "   "}) {
            device.setTenantId(legacyTenant);
            jdbc.update("update wvp_vlstream_device set tenant_id=? where id=7", legacyTenant);
            Map<String, Object> page = service.ownerGrants(7L, 1, 20, null);
            assertEquals(Long.valueOf(1L), page.get("total"));
            assertEquals("101", rows(page).get(0).get("grantId"));
        }
        authenticate("other", 300L, true);
        assertEquals(Integer.valueOf(403), assertThrows(ServiceException.class,
            () -> service.ownerGrants(7L, 1, 20, null)).getCode());
    }

    @Test public void listedActiveGrantCanBeRevokedAndReloadReturnsItsActualAuditIdentity() {
        String grantId = String.valueOf(rows(service.ownerGrants(7L, 1, 20, "ACTIVE")).get(0).get("grantId"));
        assertEquals("101", grantId);
        authenticate("owner", 202L, true);
        service.revoke(Long.valueOf(grantId));
        Map<String, Object> reloaded = find(service.ownerGrants(7L, 1, 20, "REVOKED"), grantId);
        assertEquals(Boolean.FALSE, reloaded.get("canRevoke"));
        assertEquals("202", reloaded.get("revokedByUserId"));
        assertNotNull(reloaded.get("revokedAt"));
        String auditId = String.valueOf(reloaded.get("revocationAuditId"));
        assertTrue(Long.parseLong(auditId) > 503L);
        assertEquals("REVOKED", jdbc.queryForObject("select action from wvp_vlstream_access_audit where audit_id=?", String.class, Long.valueOf(auditId)));
        assertEquals(Integer.valueOf(409), assertThrows(ServiceException.class, () -> service.revoke(Long.valueOf(grantId))).getCode());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> page) { return (List<Map<String, Object>>) page.get("rows"); }

    private Map<String, Object> find(Map<String, Object> page, String grantId) {
        for (Map<String, Object> row : rows(page)) if (grantId.equals(row.get("grantId"))) return row;
        fail("Missing actual grant " + grantId);
        return null;
    }

    private void authenticate(String tenant, long userId, boolean canApprove) {
        LoginUser principal = new LoginUser();
        principal.setTenantId(tenant); principal.setUserId(userId);
        principal.setPermissions(canApprove ? Collections.singleton(APPROVAL_PERMISSION) : Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }

    private void schema() {
        jdbc.execute("create table wvp_vlstream_device(id bigint primary key,tenant_id varchar(64))");
        jdbc.execute("create table wvp_vlstream_access_request(request_id bigint primary key,device_row_id bigint,requester_tenant_id varchar(64),requester_user_id bigint,request_reason varchar(1000),status varchar(16),resolved_grant_id bigint,created_at timestamp,updated_at timestamp)");
        jdbc.execute("create table wvp_vlstream_access_grant(grant_id bigint primary key,request_id bigint unique,device_row_id bigint,owner_tenant_id varchar(64),viewer_tenant_id varchar(64),approved_by_user_id bigint,expires_at timestamp,expired_at timestamp,revoked_at timestamp,create_time timestamp)");
        jdbc.execute("create table wvp_vlstream_access_audit(audit_id bigint auto_increment primary key,device_row_id bigint,actor_tenant_id varchar(64),actor_user_id bigint,action varchar(32),request_id bigint,grant_id bigint,detail varchar(1000),created_at timestamp)");
    }

    private void fixture() {
        jdbc.update("insert into wvp_vlstream_device values(7,'owner'),(8,'owner')");
        seedGrant(101L, 10L, 7L, "owner", "viewer-a", "review-a", 86400, false, false);
        seedGrant(102L, 11L, 7L, "owner", "viewer-b", "review-b", -60, false, false);
        seedGrant(103L, 12L, 7L, "owner", "viewer-c", "review-c", -60, true, false);
        seedGrant(104L, 13L, 7L, "owner", "viewer-d", "review-d", -60, false, true);
        seedGrant(105L, 14L, 7L, "foreign", "viewer-secret", "foreign-owner-reason", 86400, false, false);
        seedGrant(106L, 15L, 8L, "owner", "viewer-secret", "other-device-reason", 86400, false, false);
        audit(500L, 7L, "owner", 201L, "REVOKED", 103L);
        audit(501L, 7L, "foreign", 999L, "REVOKED", 103L);
        audit(502L, 8L, "owner", 998L, "REVOKED", 103L);
        audit(503L, 7L, "owner", 0L, "GRANT_EXPIRED", 104L);
    }

    private void seedGrant(long grantId, long requestId, long deviceRowId, String ownerTenant,
            String viewerTenant, String reason, int remainingSeconds, boolean revoked, boolean processedExpiry) {
        Timestamp created = Timestamp.valueOf("2026-01-01 00:00:00");
        jdbc.update("insert into wvp_vlstream_access_request values(?,?,?,?,?,'APPROVED',?,?,?)",
            requestId, deviceRowId, viewerTenant, 100L + requestId, reason, grantId, created, created);
        jdbc.update("insert into wvp_vlstream_access_grant values(?,?,?,?,?,200,TIMESTAMPADD(SECOND,?,NOW()),?,?,?)",
            grantId, requestId, deviceRowId, ownerTenant, viewerTenant, remainingSeconds,
            processedExpiry ? created : null, revoked ? created : null, created);
    }

    private void audit(long auditId, long deviceRowId, String tenant, long userId, String action, long grantId) {
        jdbc.update("insert into wvp_vlstream_access_audit values(?,?,?,?,?,NULL,?,'audit-secret',NOW())",
            auditId, deviceRowId, tenant, userId, action, grantId);
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    @EnableTransactionManagement
    public static class SecurityFixture { }

    /** Fixture permission source used by the real Spring method-security interceptor. */
    public static class PermissionsFixture {
        public boolean hasPermi(String permission) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) return false;
            LoginUser principal = (LoginUser) authentication.getPrincipal();
            return principal.getPermissions() != null && principal.getPermissions().contains(permission);
        }
    }
}
