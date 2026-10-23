package com.ruoyi.framework.security.filter;

import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.config.properties.AuthModeProperties;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the real JWT filter and TokenService using only in-memory cache and loopback verification. */
public class SharedCameraRtcAuthenticationRequestTest {
    private static final String PATH = "/vlstream/device/shared/camera-rtc";
    private static final String PUBLIC_PROTOCOL = "vlstream-shared-rtc.v1";
    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final String TOKEN_SECRET = "abcdefghijklmnopqrstuvwxyz";
    private static final String APPROVE = "vlstream:device:share:approve";
    private final AtomicReference<Headers> verifierHeaders = new AtomicReference<>();
    private final AtomicInteger verifierCalls = new AtomicInteger();
    private JwtAuthenticationTokenFilter filter;
    private TokenService tokens;
    private HttpServer verifier;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HttpServletRequest chainedRequest;
    private LoginUser chainedUser;
    private LoginUser localUser;
    private String localJwt;

    @Before public void setup() throws Exception {
        verifier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        verifier.createContext("/verify", exchange -> {
            verifierCalls.incrementAndGet();
            verifierHeaders.set(exchange.getRequestHeaders());
            boolean valid = "Bearer fixture-vls-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = valid ? "{\"code\":200,\"success\":true,\"data\":{\"tenantId\":\"verified-tenant\","
                + "\"user\":{\"userId\":\"verified-user\",\"userName\":\"verified-name\"},"
                + "\"permissions\":[\"" + APPROVE + "\"]}}"
                : "{\"code\":401,\"success\":false,\"msg\":\"invalid fixture credentials\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        verifier.start();
        RedisCache cache = mock(RedisCache.class);
        tokens = new TokenService();
        ReflectionTestUtils.setField(tokens, "redisCache", cache);
        ReflectionTestUtils.setField(tokens, "header", "accessToken");
        ReflectionTestUtils.setField(tokens, "secret", TOKEN_SECRET);
        ReflectionTestUtils.setField(tokens, "expireTime", -1);
        AuthModeProperties mode = new AuthModeProperties();
        mode.setMode(AuthModeProperties.Mode.LOCAL);
        ReflectionTestUtils.setField(tokens, "authModeProperties", mode);
        ReflectionTestUtils.setField(tokens, "vlstreamVerifyTokenAddress", "http://127.0.0.1:" + verifier.getAddress().getPort() + "/verify");
        SysUser sysUser = new SysUser(); sysUser.setUserId(42L); sysUser.setUserName("local-user");
        localUser = new LoginUser(42L, 1L, sysUser, Collections.singleton("vlstream:device:play"));
        localUser.setTenantId("local-tenant");
        localUser.setToken("fixture-local-session");
        localUser.setExpireTime(Long.MAX_VALUE);
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + "fixture-local-session")).thenReturn(localUser);
        localJwt = Jwts.builder().claim(Constants.LOGIN_USER_KEY, "fixture-local-session")
            .signWith(SignatureAlgorithm.HS512, TOKEN_SECRET).compact();
        filter = new JwtAuthenticationTokenFilter();
        ReflectionTestUtils.setField(filter, "tokenService", tokens);
        freshRequest();
    }

    @After public void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        if (verifier != null) verifier.stop(0);
    }

    @Test public void browserPayloadUsesRealTokenVerificationAndTrustedIdentity() throws Exception {
        bridge(vlsHeaders());
        execute();
        assertNotNull(chainedUser);
        assertEquals("verified-tenant", chainedUser.getTenantId());
        assertEquals("verified-name", chainedUser.getUsername());
        assertTrue(chainedUser.isFederated());
        assertTrue(new PermissionService().hasPermi(APPROVE));
        assertEquals("Bearer fixture-vls-token", verifierHeaders.get().getFirst("Authorization"));
        assertEquals("fixture-platform-token", verifierHeaders.get().getFirst("accesstoken"));
        assertEquals("client-selected-tenant", verifierHeaders.get().getFirst("tenantid"));
        assertEquals("fixture-app", verifierHeaders.get().getFirst("appid"));
        assertEquals("fixture-key", verifierHeaders.get().getFirst("secretkey"));
        assertEquals(1, verifierCalls.get());
    }

    @Test public void nativePayloadUsesExistingSignedTokenAndCache() throws Exception {
        bridge("{\"ACCESSTOKEN\":\"Bearer " + localJwt + "\"}");
        execute();
        assertSame(localUser, chainedUser);
        assertEquals(0, verifierCalls.get());
    }

    @Test public void privateProtocolAndInjectedCredentialsAreRemovedFromTheChainView() throws Exception {
        bridge(vlsHeaders());
        execute();
        assertNotNull(chainedUser);
        assertEquals(PUBLIC_PROTOCOL, chainedRequest.getHeader(PROTOCOL_HEADER));
        assertEquals(Collections.singletonList(PUBLIC_PROTOCOL), Collections.list(chainedRequest.getHeaders("sec-websocket-protocol")));
        assertNull(chainedRequest.getHeader("X-VLStream-Token"));
        assertNull(chainedRequest.getHeader("tenantid"));
        for (String name : Collections.list(chainedRequest.getHeaderNames())) {
            for (String value : Collections.list(chainedRequest.getHeaders(name))) assertFalse(value.contains("rtc-auth."));
        }
        assertFalse(assertThrows(NumberFormatException.class, () -> chainedRequest.getIntHeader(PROTOCOL_HEADER)).getMessage().contains("rtc-auth."));
        assertFalse(assertThrows(IllegalArgumentException.class, () -> chainedRequest.getDateHeader(PROTOCOL_HEADER)).getMessage().contains("rtc-auth."));
        assertEquals(1, Collections.frequency(Collections.list(chainedRequest.getHeaderNames()), PROTOCOL_HEADER));
    }

    @Test public void credentialsAreCaseInsensitiveAndProtocolHeadersMayBeSeparate() throws Exception {
        request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL);
        request.addHeader(PROTOCOL_HEADER, encoded("{\"x-wvp-auth-source\":\"vlstream\",\"x-vlstream-token\":\"fixture-vls-token\"}"));
        execute();
        assertNotNull(chainedUser);
        assertEquals("verified-tenant", chainedUser.getTenantId());
        assertEquals(Collections.singletonList(PUBLIC_PROTOCOL), Collections.list(chainedRequest.getHeaders(PROTOCOL_HEADER)));
    }

    @Test public void existingHttpHeadersStillAuthenticateNativeClients() throws Exception {
        request.addHeader("accessToken", "Bearer " + localJwt);
        request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL);
        execute();
        assertSame(localUser, chainedUser);
    }

    @Test public void anonymousNativeHandshakeCannotEstablishAnIdentity() throws Exception {
        request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL);
        assertRejected();
    }

    @Test public void anonymousNativeHandshakeCannotReuseAnOldAuthenticationContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(localUser, null, Collections.emptyList()));
        request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL);
        assertRejected();
    }

    @Test public void nativeHttpCredentialsReplaceAnOldAuthenticationContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(localUser, null, Collections.emptyList()));
        request.addHeader("X-WVP-Auth-Source", "vlstream");
        request.addHeader("X-VLStream-Token", "fixture-vls-token");
        request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL);
        execute();
        assertNotNull(chainedUser);
        assertEquals("verified-tenant", chainedUser.getTenantId());
        assertTrue(chainedUser.isFederated());
    }

    @Test public void identicalRealAndEncodedHeadersAreUnambiguous() throws Exception {
        request.addHeader("X-WVP-Auth-Source", "vlstream");
        request.addHeader("X-VLStream-Token", "fixture-vls-token");
        bridge(vlsHeaders());
        execute();
        assertNotNull(chainedUser);
    }

    @Test public void conflictingRealAuthOrContextHeadersAreRejectedBeforeVerification() throws Exception {
        for (String header : new String[]{"accesstoken", "X-VLStream-Token", "X-WVP-Auth-Source", "tenantid", "requesttype", "appid", "secretkey"}) {
            freshRequest();
            request.addHeader(header, "conflicting-value");
            bridge(vlsHeaders());
            assertRejected();
        }
        assertEquals(0, verifierCalls.get());
    }

    @Test public void duplicateRealHeadersCannotHideAConflict() throws Exception {
        request.addHeader("X-VLStream-Token", "fixture-vls-token");
        request.addHeader("X-VLStream-Token", "conflicting-value");
        bridge(vlsHeaders());
        assertRejected();
        assertEquals(0, verifierCalls.get());
    }

    @Test public void strictJsonRejectsDuplicateUnknownAndNonStringValues() throws Exception {
        for (String json : new String[]{
            "{\"accesstoken\":\"a\",\"accesstoken\":\"b\"}",
            "{\"accesstoken\":\"a\",\"AccessToken\":\"b\"}",
            "{\"accesstoken\":\"a\",\"\\u0061ccesstoken\":\"b\"}",
            "{\"tenantid\":\"a\",\"tenantID\":\"b\"}",
            "{\"permissions\":\"vlstream:device:share:approve\"}",
            "{\"accesstoken\":null}", "{\"accesstoken\":true}", "{\"accesstoken\":42}",
            "{\"accesstoken\":[\"token\"]}", "{\"accesstoken\":{\"token\":\"a\"}}",
            "[]", "null", "\"token\"", "{} {}", "{\"accesstoken\":\"a\",}",
            "{\"accesstoken\":\"a\\r\\nAuthorization: injected\"}",
            "{\"accesstoken\":\"a\\u0000b\"}", "{\"accesstoken\":\"a\\u0085b\"}",
            "{\"accesstoken\":\"a\\u009fb\"}", "{\"accesstoken\":\"a\\ud800b\"}"
        }) {
            freshRequest(); bridge(json); assertRejected();
        }
        assertEquals(0, verifierCalls.get());
    }

    @Test public void strictProtocolParsingRejectsAmbiguousAndMalformedEnvelopes() throws Exception {
        String valid = encoded(vlsHeaders());
        for (String protocol : new String[]{valid, PUBLIC_PROTOCOL + ", " + valid + ", " + valid,
            PUBLIC_PROTOCOL + ", " + PUBLIC_PROTOCOL + ", " + valid,
            PUBLIC_PROTOCOL + ", rtc-auth.", PUBLIC_PROTOCOL + ", rtc-auth.%%%",
            PUBLIC_PROTOCOL + ", rtc-auth.e30=", PUBLIC_PROTOCOL + ", rtc-auth.A",
            PUBLIC_PROTOCOL + ", rtc-auth._w", PUBLIC_PROTOCOL + ", " + valid + ",",
            PUBLIC_PROTOCOL + ", rtc-auth." + repeat('A', 8193)}) {
            freshRequest(); request.addHeader(PROTOCOL_HEADER, protocol); assertRejected();
        }
        assertEquals(0, verifierCalls.get());
    }

    @Test public void strictUtf8RejectsMalformedByteSequences() throws Exception {
        request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL + ", rtc-auth."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{'{', '"', (byte) 0xc3, (byte) 0x28, '"', ':', '"', 'x', '"', '}'}));
        assertRejected();
    }

    @Test public void exactSizeLimitIsAcceptedAndOneByteMoreIsRejected() throws Exception {
        String json = "{\"accesstoken\":\"Bearer " + localJwt + "\"}";
        String boundary = json + repeat(' ', 6144 - json.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(8192, encoded(boundary).length() - "rtc-auth.".length());
        bridge(boundary);
        execute();
        assertSame(localUser, chainedUser);
        freshRequest();
        bridge(boundary + " ");
        assertRejected();
        assertFalse(response.getErrorMessage().contains(localJwt));
    }

    @Test public void invalidSelectedCredentialsCannotAuthenticateOrReachHandshake() throws Exception {
        bridge("{\"X-WVP-Auth-Source\":\"vlstream\",\"X-VLStream-Token\":\"not-valid\",\"tenantid\":\"verified-tenant\"}");
        assertRejected();
        assertEquals(1, verifierCalls.get());
    }

    @Test public void authorizationAliasesDoNotInventATokenOrFederatedSource() throws Exception {
        bridge("{\"Authorization\":\"Bearer fixture-vls-token\",\"blade-auth\":\"Bearer fixture-vls-token\"}");
        assertRejected();
        assertEquals(0, verifierCalls.get());
    }

    @Test public void configuredAuthorizationHeaderRetainsItsExistingTokenServiceMeaning() throws Exception {
        ReflectionTestUtils.setField(tokens, "header", "Authorization");
        bridge("{\"Authorization\":\"Bearer " + localJwt + "\"}");
        execute();
        assertSame(localUser, chainedUser);
    }

    @Test public void configuredAuthorizationHeaderConflictsAreRejected() throws Exception {
        ReflectionTestUtils.setField(tokens, "header", "Authorization");
        request.addHeader("Authorization", "Bearer conflicting-token");
        bridge("{\"Authorization\":\"Bearer " + localJwt + "\"}");
        assertRejected();
    }

    @Test public void invalidNativeHttpCredentialsCannotReuseAnOldAuthenticationContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(localUser, null, Collections.emptyList()));
        request.addHeader("accessToken", "not-a-signed-token");
        request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL);
        assertRejected();
    }

    @Test public void suppliedCredentialsReplaceAStaleAuthenticationContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(localUser, null, Collections.emptyList()));
        bridge(vlsHeaders());
        execute();
        assertNotNull(chainedUser);
        assertEquals("verified-tenant", chainedUser.getTenantId());
        assertTrue(chainedUser.isFederated());
    }

    @Test public void bridgeOnlyAppliesToTheExactGetWebSocketEndpoint() throws Exception {
        for (String path : new String[]{"/vlstream/device/shared/camera-rtc/", "/vlstream/device/shared/camera-rtc/other", "/video/device/list"}) {
            freshRequest(); request.setServletPath(path); request.setRequestURI(path); bridge(vlsHeaders());
            execute(); assertNull(chainedUser); assertNotNull(chainedRequest);
            assertTrue(chainedRequest.getHeader(PROTOCOL_HEADER).contains("rtc-auth."));
        }
        freshRequest(); request.setMethod("POST"); bridge(vlsHeaders()); execute(); assertNull(chainedUser);
        freshRequest(); request.removeHeader("Upgrade"); bridge(vlsHeaders()); execute(); assertNull(chainedUser);
        freshRequest(); request.removeHeader("Upgrade"); request.addHeader("Upgrade", "h2c"); bridge(vlsHeaders()); execute(); assertNull(chainedUser);
        assertEquals(0, verifierCalls.get());
    }

    @Test public void ordinaryApiAuthenticationExceptionsKeepTheirExistingBehavior() throws Exception {
        request.setServletPath("/video/device/list"); request.setRequestURI("/video/device/list");
        request.addHeader("X-WVP-Auth-Source", "vlstream"); request.addHeader("X-VLStream-Token", "not-valid");
        assertThrows(ServiceException.class, this::execute);
        assertNull(chainedRequest);
    }

    @Test public void ordinaryApiKeepsItsExistingAuthenticationContextBehavior() throws Exception {
        request.setServletPath("/video/device/list"); request.setRequestURI("/video/device/list");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(localUser, null, Collections.emptyList()));
        request.addHeader("X-WVP-Auth-Source", "vlstream"); request.addHeader("X-VLStream-Token", "fixture-vls-token");
        execute();
        assertSame(localUser, chainedUser);
    }

    private void freshRequest() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest("GET", PATH);
        request.setServletPath(PATH);
        request.addHeader("Upgrade", "websocket");
        response = new MockHttpServletResponse();
        chainedRequest = null; chainedUser = null;
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void bridge(String json) { request.addHeader(PROTOCOL_HEADER, PUBLIC_PROTOCOL + ", " + encoded(json)); }
    private String encoded(String json) { return "rtc-auth." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)); }
    private String repeat(char value, int count) { return String.join("", Collections.nCopies(count, String.valueOf(value))); }
    private String vlsHeaders() {
        return "{\"accesstoken\":\"fixture-platform-token\",\"X-VLStream-Token\":\"fixture-vls-token\","
            + "\"X-WVP-Auth-Source\":\"vlstream\",\"tenantid\":\"client-selected-tenant\",\"requesttype\":\"app\","
            + "\"appid\":\"fixture-app\",\"secretkey\":\"fixture-key\"}";
    }

    private void execute() throws Exception {
        filter.doFilter(request, response, (next, result) -> {
            chainedRequest = (HttpServletRequest) next;
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                chainedUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            }
        });
    }

    private void assertRejected() throws Exception {
        execute();
        assertEquals("Malformed or conflicting credentials must be forbidden", 403, response.getStatus());
        assertNull(chainedRequest);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
