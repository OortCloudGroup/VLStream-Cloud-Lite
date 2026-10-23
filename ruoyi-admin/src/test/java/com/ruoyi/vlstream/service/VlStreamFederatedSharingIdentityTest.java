package com.ruoyi.vlstream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.config.properties.AuthModeProperties;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.vlstream.config.VlStreamDeviceAccessProperties;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.controller.VlStreamDeviceController;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.sun.net.httpserver.HttpServer;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real federation HTTP verification, JWT filter, permission AOP and sharing SQL; only isolated fixtures run. */
public class VlStreamFederatedSharingIdentityTest {
    private static final String MANAGE = "vlstream:device:share:manage";
    private static final String APPROVE = "vlstream:device:share:approve";
    private static final String TOKEN = "federated-sharing-fixture-token";
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> verifiedTenant = new AtomicReference<>("owner");
    private final AtomicReference<Object> verifiedPermissions = new AtomicReference<>();
    private final AtomicReference<Object> nestedPermissions = new AtomicReference<>();
    private final AtomicInteger verificationCalls = new AtomicInteger();
    private JdbcConnectionPool pool;
    private JdbcTemplate jdbc;
    private AnnotationConfigApplicationContext context;
    private HttpServer verifier;
    private MockMvc api;
    private PermissionService permissions;

    @Before public void setup() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        pool = JdbcConnectionPool.create("jdbc:h2:mem:federated_sharing_" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(pool);
        schema();
        VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
        when(devices.selectById(7L)).thenAnswer(invocation -> device());
        when(devices.selectByIdForUpdate(7L)).thenAnswer(invocation -> {
            jdbc.queryForObject("select id from wvp_vlstream_device where id=7 for update", Long.class);
            return device();
        });
        VlStreamDeviceProperties tenancy = new VlStreamDeviceProperties();
        tenancy.setDefaultTenantId("default");
        VlStreamDeviceTenantService tenants = new VlStreamDeviceTenantService(tenancy, devices);
        VlStreamDeviceAccessService access = new VlStreamDeviceAccessService(jdbc, devices, tenants,
            new VlStreamDeviceAccessRefService(jdbc), new VlStreamDeviceAccessProperties(), mock(ApplicationEventPublisher.class));
        context = new AnnotationConfigApplicationContext();
        context.register(SecurityFixture.class);
        context.registerBean("ss", PermissionService.class);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(pool));
        context.registerBean(VlStreamDeviceOwnerPlaybackGuard.class, () -> new VlStreamDeviceOwnerPlaybackGuard(tenants));
        context.registerBean(VlStreamDeviceAccessService.class, () -> access);
        context.registerBean(VlStreamDevicePreviewLeaseService.class, () -> mock(VlStreamDevicePreviewLeaseService.class));
        context.registerBean(VlStreamDeviceController.class, () -> new VlStreamDeviceController(devices,
            mock(VlStreamDeviceStreamMapper.class), mock(IMediaServerService.class),
            mock(VlStreamFirmwareDeploymentService.class), tenants));
        context.refresh();
        permissions = context.getBean(PermissionService.class);
        assertTrue(AopUtils.isAopProxy(context.getBean(VlStreamDeviceController.class)));
        assertTrue(AopUtils.isAopProxy(context.getBean(VlStreamDeviceAccessService.class)));

        verifier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        verifier.createContext("/blade-system/user/info", exchange -> {
            verificationCalls.incrementAndGet();
            boolean valid = ("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", valid ? 200 : 401);
            result.put("success", valid);
            if (valid) {
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("userId", "federated-sharing-user");
                user.put("userName", "Sharing fixture");
                user.put("tenantId", verifiedTenant.get());
                user.put("permissions", nestedPermissions.get());
                user.put("roles", Arrays.asList("admin", "tenant-admin"));
                user.put("isAdmin", true);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("user", user);
                data.put("roles", Arrays.asList("admin", "tenant-admin"));
                data.put("permissions", verifiedPermissions.get());
                result.put("data", data);
            }
            byte[] body = json.writeValueAsBytes(result);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        verifier.start();
        TokenService tokens = new TokenService();
        AuthModeProperties mode = new AuthModeProperties();
        mode.setMode(AuthModeProperties.Mode.LOCAL);
        ReflectionTestUtils.setField(tokens, "redisCache", mock(RedisCache.class));
        ReflectionTestUtils.setField(tokens, "authModeProperties", mode);
        ReflectionTestUtils.setField(tokens, "header", "accessToken");
        ReflectionTestUtils.setField(tokens, "secret", "abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(tokens, "expireTime", 30);
        ReflectionTestUtils.setField(tokens, "vlstreamVerifyTokenAddress", "http://127.0.0.1:"
            + verifier.getAddress().getPort() + "/blade-system/user/info");
        JwtAuthenticationTokenFilter authentication = new JwtAuthenticationTokenFilter();
        ReflectionTestUtils.setField(authentication, "tokenService", tokens);
        api = MockMvcBuilders.standaloneSetup(context.getBean(VlStreamDeviceController.class))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(authentication).build();
    }

    @After public void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        if (verifier != null) verifier.stop(0);
        if (context != null) context.close();
        if (pool != null) pool.dispose();
    }

    @Test public void verifiedExactApprovalPermissionReachesRealOwnerGrantHistory() throws Exception {
        verifiedPermissions.set(Arrays.asList("*:*:*", APPROVE));
        api.perform(federated(get("/vlstream/device/7/access-grants")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.rows[0].grantId").value("101"))
            .andExpect(jsonPath("$.data.rows[0].requesterTenantId").value("viewer"));
        LoginUser user = SecurityUtils.getLoginUser();
        assertTrue(user.isFederated());
        assertEquals("owner", user.getTenantId());
        assertTrue(permissions.hasPermi(APPROVE));
        assertFalse(permissions.hasPermi(MANAGE));
        assertFalse(user.getPermissions().contains("*:*:*"));
        assertEquals(1, verificationCalls.get());
    }

    @Test public void verifiedExactManagementPermissionChangesVisibilityAndAuditsTrustedActor() throws Exception {
        verifiedPermissions.set(Collections.singletonList(MANAGE));
        api.perform(federated(visibility()).header("tenantId", "attacker").param("tenantId", "attacker"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
        assertEquals("PUBLIC", jdbc.queryForObject("select visibility from wvp_vlstream_device where id=7", String.class));
        Map<String, Object> audit = jdbc.queryForMap("select actor_tenant_id,actor_user_id from wvp_vlstream_access_audit where action='VISIBILITY_CHANGED'");
        assertEquals("owner", audit.get("actor_tenant_id"));
        assertEquals(SecurityUtils.getUserId().longValue(), ((Number) audit.get("actor_user_id")).longValue());
        assertTrue(permissions.hasPermi(MANAGE));
        assertFalse(permissions.hasPermi(APPROVE));
    }

    @Test public void missingPermissionsAndClientClaimsCannotGrantEitherSharingPower() throws Exception {
        nestedPermissions.set(Arrays.asList(MANAGE, APPROVE));
        api.perform(federated(get("/vlstream/device/7/access-grants"))
                .header("permissions", APPROVE).header("X-WVP-Permissions", APPROVE)
                .header("X-User-Roles", "admin").param("permissions", APPROVE))
            .andExpect(jsonPath("$.code").value(403));
        SecurityContextHolder.clearContext();
        api.perform(federated(visibility()).header("permissions", MANAGE))
            .andExpect(jsonPath("$.code").value(403));
        assertFalse(permissions.hasPermi(MANAGE));
        assertFalse(permissions.hasPermi(APPROVE));
        assertEquals("PRIVATE", jdbc.queryForObject("select visibility from wvp_vlstream_device where id=7", String.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from wvp_vlstream_access_audit", Integer.class));
    }

    @Test public void serverWildcardsAndAdminRoleNamesDoNotBecomeExplicitSharingPermissions() throws Exception {
        verifiedPermissions.set(Arrays.asList("*:*:*", "vlstream:*", "vlstream:device:share:*", "system:user:list"));
        api.perform(federated(get("/vlstream/device/7/access-grants")))
            .andExpect(jsonPath("$.code").value(403));
        assertFalse(SecurityUtils.getLoginUser().getPermissions().contains("*:*:*"));
        assertFalse(SecurityUtils.getLoginUser().getPermissions().contains("vlstream:*"));
        assertFalse(permissions.hasPermi(APPROVE));
        assertFalse(permissions.hasPermi(MANAGE));
    }

    @Test public void explicitSharingPermissionsNeverReplaceTrustedOwnerTenantChecks() throws Exception {
        verifiedTenant.set("foreign");
        verifiedPermissions.set(Arrays.asList(MANAGE, APPROVE));
        api.perform(federated(get("/vlstream/device/7/access-grants"))
                .header("tenantid", "owner").param("tenantId", "owner"))
            .andExpect(jsonPath("$.code").value(403));
        assertEquals("foreign", SecurityUtils.getLoginUser().getTenantId());
        assertTrue(permissions.hasPermi(APPROVE));
        SecurityContextHolder.clearContext();
        api.perform(federated(visibility()).header("tenantid", "owner"))
            .andExpect(jsonPath("$.code").value(403));
        assertTrue(permissions.hasPermi(MANAGE));
        assertEquals("PRIVATE", jdbc.queryForObject("select visibility from wvp_vlstream_device where id=7", String.class));
    }

    @Test public void freshVerificationRemovesPermissionsInsteadOfReusingAnOldLoginCache() throws Exception {
        verifiedPermissions.set(Collections.singletonList(APPROVE));
        api.perform(federated(get("/vlstream/device/7/access-grants")))
            .andExpect(jsonPath("$.code").value(200));
        verifiedPermissions.set(Collections.emptyList());
        SecurityContextHolder.clearContext();
        api.perform(federated(get("/vlstream/device/7/access-grants")))
            .andExpect(jsonPath("$.code").value(403));
        assertFalse(permissions.hasPermi(APPROVE));
        assertEquals(2, verificationCalls.get());
    }

    @Test public void malformedPermissionsCannotBeCoercedIntoAStringArray() throws Exception {
        for (Object malformed : Arrays.asList(APPROVE, Collections.singletonMap(APPROVE, true),
                Arrays.asList(APPROVE, 1))) {
            verifiedPermissions.set(malformed);
            SecurityContextHolder.clearContext();
            api.perform(federated(get("/vlstream/device/7/access-grants")))
                .andExpect(jsonPath("$.code").value(403));
            assertFalse(permissions.hasPermi(APPROVE));
        }
    }

    private MockHttpServletRequestBuilder federated(MockHttpServletRequestBuilder request) {
        return request.header("X-WVP-Auth-Source", "vlstream").header("X-VLStream-Token", TOKEN);
    }

    private MockHttpServletRequestBuilder visibility() {
        return put("/vlstream/device/7/visibility").contentType(MediaType.APPLICATION_JSON)
            .content("{\"visibility\":\"PUBLIC\",\"tenantId\":\"attacker\",\"permissions\":[\"" + MANAGE + "\"]}");
    }

    private VlStreamDevice device() {
        Map<String, Object> row = jdbc.queryForMap("select tenant_id,visibility from wvp_vlstream_device where id=7");
        VlStreamDevice device = new VlStreamDevice();
        device.setId(7L); device.setTenantId(String.valueOf(row.get("tenant_id")));
        device.setVisibility(String.valueOf(row.get("visibility")));
        return device;
    }

    private void schema() {
        jdbc.execute("create table wvp_vlstream_device(id bigint primary key,tenant_id varchar(64),visibility varchar(16),update_time timestamp)");
        jdbc.execute("create table wvp_vlstream_access_request(request_id bigint primary key,device_row_id bigint,requester_tenant_id varchar(64),requester_user_id bigint,request_reason varchar(1000),status varchar(16),resolved_grant_id bigint,created_at timestamp,updated_at timestamp)");
        jdbc.execute("create table wvp_vlstream_access_grant(grant_id bigint primary key,request_id bigint unique,device_row_id bigint,owner_tenant_id varchar(64),viewer_tenant_id varchar(64),approved_by_user_id bigint,expires_at timestamp,expired_at timestamp,revoked_at timestamp,create_time timestamp)");
        jdbc.execute("create table wvp_vlstream_access_audit(audit_id bigint auto_increment primary key,device_row_id bigint,actor_tenant_id varchar(64),actor_user_id bigint,action varchar(32),request_id bigint,grant_id bigint,detail varchar(1000),created_at timestamp)");
        jdbc.update("insert into wvp_vlstream_device values(7,'owner','PRIVATE',NOW())");
        jdbc.update("insert into wvp_vlstream_access_request values(10,7,'viewer',100,'review','APPROVED',101,NOW(),NOW())");
        jdbc.update("insert into wvp_vlstream_access_grant values(101,10,7,'owner','viewer',200,TIMESTAMPADD(DAY,1,NOW()),null,null,NOW())");
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    @EnableTransactionManagement
    public static class SecurityFixture { }
}
