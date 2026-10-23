package com.ruoyi.framework.web.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.config.properties.AuthModeProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/** Real federation HTTP verification and PermissionService; no client claims become permissions. */
public class TokenServiceFederatedSharingTest {
    private static final String MANAGE = "vlstream:device:share:manage";
    private static final String APPROVE = "vlstream:device:share:approve";
    private final AtomicReference<String> verifiedData = new AtomicReference<>();
    private final AtomicReference<String> verifiedTenant = new AtomicReference<>();
    private final AtomicReference<String> verifiedUser = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private TokenService tokens;
    private HttpServer verifier;
    private MockHttpServletRequest request;
    private final PermissionService permissions = new PermissionService();

    @Before public void setup() throws Exception {
        verifiedData.set("\"permissions\":[]");
        verifiedTenant.set("\"trusted-owner\"");
        verifiedUser.set("{\"userId\":\"verified-user\",\"userName\":\"server-user\",\"tenantId\":\"trusted-owner\"}");
        verifier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        verifier.createContext("/blade-system/user/info", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            receivedToken.set(authorization);
            boolean valid = "Bearer fixture-valid-vls-token".equals(authorization);
            String payload = valid ? "{\"code\":200,\"success\":true,\"data\":{\"tenantId\":" + verifiedTenant.get() + "," +
                "\"user\":" + verifiedUser.get() + "," + verifiedData.get() + "}}"
                : "{\"code\":401,\"success\":false,\"msg\":\"invalid fixture token\"}";
            byte[] response = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        verifier.start();
        tokens = new TokenService();
        ReflectionTestUtils.setField(tokens, "redisCache", mock(RedisCache.class));
        ReflectionTestUtils.setField(tokens, "header", "accessToken");
        ReflectionTestUtils.setField(tokens, "expireTime", 30);
        AuthModeProperties mode = new AuthModeProperties(); mode.setMode(AuthModeProperties.Mode.LOCAL);
        ReflectionTestUtils.setField(tokens, "authModeProperties", mode);
        ReflectionTestUtils.setField(tokens, "vlstreamVerifyTokenAddress", "http://127.0.0.1:" + verifier.getAddress().getPort() + "/blade-system/user/info");
        request = new MockHttpServletRequest();
        request.addHeader("X-WVP-Auth-Source", "vlstream");
        request.addHeader("X-VLStream-Token", "fixture-valid-vls-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @After public void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        if (verifier != null) verifier.stop(0);
    }

    @Test public void verifiedServerExactSharingPermissionsReachTheRealPermissionService() {
        verifiedData.set("\"permissions\":[\"*:*:*\",\"" + MANAGE + "\",\"" + APPROVE + "\",\"system:user:remove\"]");
        LoginUser login = authenticate();
        assertEquals("Bearer fixture-valid-vls-token", receivedToken.get());
        assertEquals("trusted-owner", login.getTenantId());
        assertTrue(login.isFederated());
        assertTrue(permissions.hasPermi(MANAGE));
        assertTrue(permissions.hasPermi(APPROVE));
        assertFalse(permissions.hasPermi("system:user:remove"));
        assertFalse(login.getPermissions().contains("*:*:*"));
        assertTrue(permissions.hasPermi("vlstream:device:play"));
    }

    @Test public void verifiedPermissionsRemainGranularAndRefreshOnEveryRequest() {
        verifiedData.set("\"permissions\":[\"" + APPROVE + "\"]");
        authenticate();
        assertTrue(permissions.hasPermi(APPROVE));
        assertFalse(permissions.hasPermi(MANAGE));
        verifiedData.set("\"permissions\":[]");
        authenticate();
        assertFalse(permissions.hasPermi(APPROVE));
        assertFalse(permissions.hasPermi(MANAGE));
    }

    @Test public void missingPermissionsAndOldResponseRemainCompatibleWithoutSharingPowers() {
        verifiedData.set("\"legacy\":true");
        LoginUser login = authenticate();
        assertTrue(login.isFederated());
        assertTrue(permissions.hasPermi("vlstream:device:list"));
        assertFalse(permissions.hasPermi(APPROVE));
        assertFalse(permissions.hasPermi(MANAGE));
    }

    @Test public void wildcardRoleAndNestedPermissionFieldsCannotGrantSharingPowers() {
        verifiedData.set("\"permissions\":[\"*:*:*\",\"vlstream:*\",\"vlstream:device:share:*\"]," +
            "\"roles\":[\"admin\"],\"isAdmin\":true,\"isTenantAdmin\":1," +
            "\"claims\":{\"permissions\":[\"" + APPROVE + "\"]}");
        authenticate();
        assertFalse(permissions.hasPermi(MANAGE));
        assertFalse(permissions.hasPermi(APPROVE));
        verifiedData.set("\"permissions\":[],\"claims\":{\"permissions\":[\"" + MANAGE + "\"]}");
        authenticate();
        assertFalse(permissions.hasPermi(MANAGE));
        verifiedUser.set("{\"userId\":\"verified-user\",\"tenantId\":\"trusted-owner\"," +
            "\"permissions\":[\"" + APPROVE + "\"],\"params\":{\"permissions\":[\"" + MANAGE + "\"]}}");
        verifiedData.set("\"permissions\":[]");
        authenticate();
        assertFalse(permissions.hasPermi(MANAGE));
        assertFalse(permissions.hasPermi(APPROVE));
    }

    @Test public void missingVerifiedTenantNeverImportsSharingPermissionsFromTheDefaultOrClientTenant() {
        verifiedTenant.set("null");
        verifiedUser.set("{\"userId\":\"verified-user\",\"userName\":\"server-user\"}");
        verifiedData.set("\"permissions\":[\"" + APPROVE + "\",\"" + MANAGE + "\"]");
        request.addHeader("tenantid", "attacker-selected-tenant");
        LoginUser login = authenticate();
        assertNull(login.getTenantId());
        assertFalse(permissions.hasPermi(MANAGE));
        assertFalse(permissions.hasPermi(APPROVE));
    }

    @Test public void malformedOrNearMatchPermissionValuesFailClosed() {
        for (String value : new String[]{"null", "\"" + APPROVE + "\"", "{\"" + APPROVE + "\":true}",
                "[\"" + APPROVE + "\",42]", "[\" " + APPROVE + "\"]", "[\"VLSTREAM:DEVICE:SHARE:APPROVE\"]"}) {
            verifiedData.set("\"permissions\":" + value);
            authenticate();
            assertFalse("Malformed/near-match value: " + value, permissions.hasPermi(APPROVE));
            assertFalse(permissions.hasPermi(MANAGE));
        }
    }

    @Test public void clientPermissionAndTenantHeadersCannotOverrideTheVerifiedResponse() {
        request.addHeader("tenantid", "attacker-tenant");
        request.addHeader("permissions", APPROVE);
        request.addHeader("X-WVP-Permissions", MANAGE);
        request.addHeader("X-Userinfo-Kong", "{\"roles\":[\"admin\"],\"permissions\":[\"" + APPROVE + "\"]}");
        LoginUser login = authenticate();
        assertEquals("trusted-owner", login.getTenantId());
        assertFalse(permissions.hasPermi(APPROVE));
        assertFalse(permissions.hasPermi(MANAGE));
    }

    @Test public void unverifiedTokenCannotSelfAssertIdentityOrSharingClaims() {
        verifiedData.set("\"permissions\":[\"" + APPROVE + "\"]");
        request.removeHeader("X-VLStream-Token");
        request.addHeader("X-VLStream-Token", "unverified-token-with-permissions-claim");
        assertThrows(ServiceException.class, () -> tokens.getLoginUser(request));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private LoginUser authenticate() {
        LoginUser login = tokens.getLoginUser(request);
        assertNotNull(login);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, Collections.emptyList()));
        return login;
    }
}
