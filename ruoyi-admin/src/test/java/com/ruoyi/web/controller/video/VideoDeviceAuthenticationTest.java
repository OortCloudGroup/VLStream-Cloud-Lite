package com.ruoyi.web.controller.video;

import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.aspectj.DataScopeAspect;
import com.ruoyi.framework.config.properties.AuthModeProperties;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.video.config.VideoDeviceIdentityProperties;
import com.ruoyi.video.controller.VideoDeviceController;
import com.ruoyi.video.domain.VideoDeviceQuery;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.video.mapper.VideoDeviceMapper;
import com.ruoyi.video.service.VideoDeviceScopedReader;
import com.ruoyi.video.service.VideoDeviceService;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real TokenService, JWT filter, PermissionService, method-security and DataScope AOP.
 * Only the upstream verification endpoint, Redis and device mapper use isolated fixtures.
 */
public class VideoDeviceAuthenticationTest {
    private static final String SECRET = "abcdefghijklmnopqrstuvwxyz";
    private AnnotationConfigApplicationContext context;
    private HttpServer upstream;
    private final AtomicReference<String> verifiedTenant = new AtomicReference<>("default");
    private final List<VideoDeviceQuery> queries = new ArrayList<>();
    private RedisCache redis;
    private VideoDeviceMapper mapper;
    private MockMvc api;

    @Before public void setup() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        mapper = context.getBean(VideoDeviceMapper.class);
        when(mapper.selectSources(any())).thenAnswer(invocation -> {
            VideoDeviceQuery query = invocation.getArgument(0);
            queries.add(query);
            VideoDeviceSource source = new VideoDeviceSource();
            source.setProtocol(query.getProtocol());
            source.setRowId("VLSTREAM".equals(query.getProtocol()) && "foreign".equals(query.getTenantId()) ? "2" : "1");
            source.setDeviceId("fixture-" + query.getProtocol());
            source.setDeviceName(query.getProtocol() + " " + query.getTenantId());
            source.setState("CUSTOM".equals(query.getProtocol()) ? "ONLINE" : "OFFLINE");
            return Collections.singletonList(source);
        });
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/verify", exchange -> {
            byte[] body = ("{\"code\":200,\"success\":true,\"data\":{\"user\":{"
                + "\"userId\":\"federated-device-user\",\"userName\":\"fixture\",\"tenantId\":\""
                + verifiedTenant.get() + "\"}}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        redis = mock(RedisCache.class);
        TokenService tokens = new TokenService();
        AuthModeProperties mode = new AuthModeProperties();
        mode.setMode(AuthModeProperties.Mode.LOCAL);
        ReflectionTestUtils.setField(tokens, "redisCache", redis);
        ReflectionTestUtils.setField(tokens, "authModeProperties", mode);
        ReflectionTestUtils.setField(tokens, "header", "accessToken");
        ReflectionTestUtils.setField(tokens, "secret", SECRET);
        ReflectionTestUtils.setField(tokens, "expireTime", 30);
        ReflectionTestUtils.setField(tokens, "vlstreamVerifyTokenAddress", "http://127.0.0.1:" + upstream.getAddress().getPort() + "/verify");
        JwtAuthenticationTokenFilter authentication = new JwtAuthenticationTokenFilter();
        ReflectionTestUtils.setField(authentication, "tokenService", tokens);
        api = MockMvcBuilders.standaloneSetup(context.getBean(VideoDeviceController.class))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(authentication).build();
        assertTrue(AopUtils.isAopProxy(context.getBean(VideoDeviceController.class)));
        assertTrue(AopUtils.isAopProxy(context.getBean(VideoDeviceScopedReader.class)));
    }

    @After public void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        if (upstream != null) upstream.stop(0);
        if (context != null) context.close();
    }

    @Test public void normalFederatedRequestRetainsVlstreamViaOriginalPermissionAndAopSemantics() throws Exception {
        api.perform(get("/video/device/list").header("X-WVP-Auth-Source", "vlstream")
                .header("X-VLStream-Token", "isolated-federation-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(8))
            .andExpect(jsonPath("$.data.protocols[0]").value("VLSTREAM"));
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertTrue(user.isFederated()); assertFalse(user.getUser().isAdmin());
        assertFalse(user.getPermissions().contains("*:*:*"));
        assertFalse(user.getPermissions().contains("vlstream:*"));
        assertFalse(com.ruoyi.common.utils.SecurityUtils.hasPermi(user.getPermissions(), "vlstream:device:list"));
        assertTrue(context.getBean(PermissionService.class).hasPermi("vlstream:device:list"));
        assertEquals(8, queries.size());
        for (VideoDeviceQuery query : queries) {
            assertEquals("default", query.getTenantId());
            if (!Arrays.asList("VLSTREAM", "CUSTOM").contains(query.getProtocol())) {
                assertEquals("", query.getParams().get("dataScope")); // Real federated DataScope branch.
            }
        }
    }

    @Test public void verifiedForeignTenantCannotReadLegacySourcesOrSpoofQueryTenant() throws Exception {
        verifiedTenant.set("foreign");
        api.perform(get("/video/device/list").header("X-WVP-Auth-Source", "vlstream")
                .header("X-VLStream-Token", "isolated-federation-token").param("tenantId", "default"))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.protocols.length()").value(3));
        assertEquals(3, queries.size());
        assertEquals(Arrays.asList("VLSTREAM", "RTSP", "ONVIF"),
            Arrays.asList(queries.get(0).getProtocol(), queries.get(1).getProtocol(), queries.get(2).getProtocol()));
        for (VideoDeviceQuery query : queries) {
            assertEquals("foreign", query.getTenantId());
            assertEquals("VLSTREAM".equals(query.getProtocol()) ? "default" : "000000", query.getDefaultTenantId());
        }
    }

    @Test public void nativeUserKeepsExactProtocolPermissionAndRealDepartmentScope() throws Exception {
        String jwt = nativeToken(Collections.singleton("onvif:device:list"));
        api.perform(get("/video/device/list").header("accessToken", "Bearer " + jwt))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.protocols[0]").value("ONVIF"));
        assertEquals(1, queries.size());
        assertEquals("ONVIF", queries.get(0).getProtocol());
        assertEquals(" AND (d.dept_id = 2 )", queries.get(0).getParams().get("dataScope"));
    }

    @Test public void userWithoutDevicePermissionsIsRejectedBeforeAnyDeviceRead() throws Exception {
        String jwt = nativeToken(Collections.singleton("system:user:list"));
        api.perform(get("/video/device/list").header("accessToken", "Bearer " + jwt))
            .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(mapper);
        VideoDeviceQuery query = new VideoDeviceQuery(); query.setProtocol("VLSTREAM");
        try { context.getBean(VideoDeviceScopedReader.class).vlstream(query); fail("Reader must deny direct access too"); }
        catch (AccessDeniedException expected) { }
        verifyNoInteractions(mapper);
    }

    @Test public void identityMappingCannotPullAnUnauthorizedOnlineSourceIntoAnOfflineDevice() throws Exception {
        context.getBean(VideoDeviceIdentityProperties.class).setIdentityGroups(Collections.singletonMap(
            "verified-camera", Arrays.asList("VLSTREAM:1", "CUSTOM:1")));
        String jwt = nativeToken(Collections.singleton("vlstream:device:list"));
        api.perform(get("/video/device/list").header("accessToken", "Bearer " + jwt))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.rows[0].status").value("OFFLINE"))
            .andExpect(jsonPath("$.data.rows[0].sources.length()").value(1));
        assertEquals(1, queries.size()); assertEquals("VLSTREAM", queries.get(0).getProtocol());
    }

    @Test public void anonymousEntryIsRejectedByMethodSecurity() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("fixture", "anonymous",
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        api.perform(get("/video/device/list")).andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(mapper);
    }

    private String nativeToken(Set<String> permissions) {
        SysRole role = new SysRole(); role.setRoleId(2L); role.setStatus("0");
        role.setDataScope("3"); role.setPermissions(permissions);
        SysUser user = new SysUser(); user.setUserId(2L); user.setDeptId(2L); user.setUserName("native-fixture");
        user.setRoles(Collections.singletonList(role));
        LoginUser login = new LoginUser(2L, 2L, user, permissions);
        login.setTenantId("default"); login.setExpireTime(Long.MAX_VALUE);
        when(redis.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + "native-fixture")).thenReturn(login);
        return Jwts.builder().claim(Constants.LOGIN_USER_KEY, "native-fixture").signWith(SignatureAlgorithm.HS512, SECRET).compact();
    }

    @Configuration
    @EnableAspectJAutoProxy
    @EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
    public static class TestConfiguration {
        @Bean(name = "ss") public PermissionService permissions() { return new PermissionService(); }
        @Bean public DataScopeAspect dataScope() { return new DataScopeAspect(); }
        @Bean public VideoDeviceMapper mapper() { return mock(VideoDeviceMapper.class); }
        @Bean public VideoDeviceIdentityProperties identities() { return new VideoDeviceIdentityProperties(); }
        @Bean public VlStreamDeviceTenantService tenants() {
            VlStreamDeviceTenantService tenants = mock(VlStreamDeviceTenantService.class);
            when(tenants.defaultTenant()).thenReturn("default"); return tenants;
        }
        @Bean public VideoDeviceScopedReader reader(VideoDeviceMapper mapper) { return new VideoDeviceScopedReader(mapper); }
        @Bean public VideoDeviceService service(VideoDeviceScopedReader reader, VlStreamDeviceTenantService tenants,
                                                VideoDeviceIdentityProperties identities) {
            return new VideoDeviceService(reader, tenants, identities);
        }
        @Bean public VideoDeviceController controller(VideoDeviceService service) { return new VideoDeviceController(service); }
    }
}
