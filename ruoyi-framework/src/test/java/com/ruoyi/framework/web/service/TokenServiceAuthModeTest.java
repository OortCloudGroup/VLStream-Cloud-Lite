package com.ruoyi.framework.web.service;

import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.config.properties.AuthModeProperties;
import com.ruoyi.framework.dto.PlatformLoginUser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class TokenServiceAuthModeTest
{
    private static final String TOKEN_HEADER = "accessToken";
    private static final String TOKEN_SECRET = "abcdefghijklmnopqrstuvwxyz";

    private TokenService tokenService;
    private RedisCache redisCache;
    private AuthModeProperties authModeProperties;

    @Before
    public void setUp()
    {
        tokenService = new TokenService();
        redisCache = mock(RedisCache.class);
        authModeProperties = new AuthModeProperties();
        authModeProperties.setMode(AuthModeProperties.Mode.LOCAL);

        ReflectionTestUtils.setField(tokenService, "redisCache", redisCache);
        ReflectionTestUtils.setField(tokenService, "authModeProperties", authModeProperties);
        ReflectionTestUtils.setField(tokenService, "header", TOKEN_HEADER);
        ReflectionTestUtils.setField(tokenService, "secret", TOKEN_SECRET);
        ReflectionTestUtils.setField(tokenService, "expireTime", 30);
    }

    @Test
    public void defaultAuthModeKeepsExistingSsoBehavior()
    {
        AuthModeProperties properties = new AuthModeProperties();

        assertEquals(AuthModeProperties.Mode.SSO, properties.getMode());
        assertEquals("sso", properties.getModeValue());
    }

    @Test
    public void localModeReadsLoginUserFromJwtAndRedis()
    {
        String uuid = "local-login-uuid";
        String jwt = createJwt(uuid);
        LoginUser expected = new LoginUser();
        when(redisCache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + uuid)).thenReturn(expected);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TOKEN_HEADER, Constants.TOKEN_PREFIX + jwt);

        assertSame(expected, tokenService.getLoginUser(request));
    }

    @Test
    public void localModeReturnsNullForInvalidJwt()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TOKEN_HEADER, "not-a-jwt");

        assertNull(tokenService.getLoginUser(request));
        verifyNoInteractions(redisCache);
    }

    @Test
    public void requestWithoutTokenIsAnonymousInBothModes()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertNull(tokenService.getLoginUser(request));
        authModeProperties.setMode(AuthModeProperties.Mode.SSO);
        assertNull(tokenService.getLoginUser(request));
        verifyNoInteractions(redisCache);
    }

    @Test
    public void vlstreamRequestPrefersDedicatedFederatedToken()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TOKEN_HEADER, "platform-token");
        request.addHeader("X-WVP-Auth-Source", "vlstream");
        request.addHeader("X-VLStream-Token", "Bearer local-vlstream-token");

        assertEquals("local-vlstream-token", tokenService.resolveAccessToken(request));
    }

    @Test
    public void federatedProtocolPermissionsIncludeEhome()
    {
        @SuppressWarnings("unchecked")
        Set<String> permissions = (Set<String>) ReflectionTestUtils.getField(tokenService,
                "VLSTREAM_PROTOCOL_PERMISSIONS");
        assertTrue(permissions.contains("ehome:*"));
    }

    @Test
    public void nonVlstreamRequestKeepsPlatformAccessToken()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TOKEN_HEADER, "platform-token");
        request.addHeader("X-VLStream-Token", "local-vlstream-token");

        assertEquals("platform-token", tokenService.resolveAccessToken(request));
    }

    @Test
    public void neverExpireUsesPersistentRedisEntry()
    {
        ReflectionTestUtils.setField(tokenService, "expireTime", -1);
        LoginUser loginUser = new LoginUser();
        loginUser.setToken("permanent-token");

        tokenService.refreshToken(loginUser);
        tokenService.verifyToken(loginUser);

        String userKey = CacheConstants.LOGIN_TOKEN_KEY + "permanent-token";
        assertEquals(Long.valueOf(Long.MAX_VALUE), loginUser.getExpireTime());
        verify(redisCache).setCacheObject(userKey, loginUser);
        verify(redisCache, never()).setCacheObject(eq(userKey), eq(loginUser), anyInt(), any(TimeUnit.class));
    }

    @Test
    public void vlstreamVerificationForwardsGatewayIdentityWithSplitTokens() throws Exception
    {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/blade-system/user/info", exchange -> {
            capturedHeaders.set(exchange.getRequestHeaders());
            byte[] body = ("{\"code\":200,\"success\":true,\"data\":{"
                    + "\"user\":{\"userId\":\"user-1\",\"userName\":\"tester\","
                    + "\"tenantId\":\"tenant-1\"}}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            ReflectionTestUtils.setField(tokenService, "vlstreamVerifyTokenAddress",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/blade-system/user/info");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("accesstoken", "platform-token");
            request.addHeader("requesttype", "app");
            request.addHeader("appid", "app-id");
            request.addHeader("secretkey", "secret-key");
            request.addHeader("tenantid", "tenant-1");

            PlatformLoginUser user = ReflectionTestUtils.invokeMethod(tokenService,
                    "getVlstreamLoginUser", request, "local-vlstream-token");

            Headers headers = capturedHeaders.get();
            assertEquals("user-1", user.getUserId());
            assertEquals("Bearer local-vlstream-token", headers.getFirst("Authorization"));
            assertEquals("Bearer local-vlstream-token", headers.getFirst("blade-auth"));
            assertEquals("platform-token", headers.getFirst("accesstoken"));
            assertEquals("app", headers.getFirst("requesttype"));
            assertEquals("app-id", headers.getFirst("appid"));
            assertEquals("secret-key", headers.getFirst("secretkey"));
            assertEquals("tenant-1", headers.getFirst("tenantid"));
        } finally {
            server.stop(0);
        }
    }

    private String createJwt(String uuid)
    {
        return Jwts.builder()
                .claim(Constants.LOGIN_USER_KEY, uuid)
                .signWith(SignatureAlgorithm.HS512, TOKEN_SECRET)
                .compact();
    }
}
