package com.ruoyi.framework.security.filter;

import com.ruoyi.framework.config.FilterConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import static org.junit.Assert.*;

/** Uses the real 5.7 firewall and Spring CORS with synthetic headers, no production credentials. */
public class GatewayUserInfoHeaderFilterTest {
    private static final String CHINESE_JSON = "{\"userName\":\"\u7528\u6237\u5458\"}";
    private final GatewayUserInfoHeaderFilter metadata = new GatewayUserInfoHeaderFilter();
    private final StrictHttpFirewall firewall = new StrictHttpFirewall();

    @After public void clearContext() { SecurityContextHolder.clearContext(); }
    private String mojibake() {
        return new String(CHINESE_JSON.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }
    private MockHttpServletRequest request(String metadataValue) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rtsp/RtspDevice/8/preview");
        request.setServletPath("/rtsp/RtspDevice/8/preview");
        request.addHeader("Origin", "https://portal.fixture");
        request.addHeader("x-userinfo-kong", metadataValue);
        request.addHeader("X-VLStream-Token", "fixture-local-token");
        request.addHeader("accesstoken", "fixture-platform-token");
        request.addHeader("tenantid", "fixture-tenant");
        request.addHeader("X-WVP-Auth-Source", "vlstream");
        return request;
    }
    private void cors(HttpServletRequest request, AtomicBoolean passed) throws ServletException, IOException {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        FirewalledRequest secured = firewall.getFirewalledRequest(request);
        new CorsFilter(source).doFilter(secured, new MockHttpServletResponse(), (req, res) -> passed.set(true));
    }
    private void filtered(MockHttpServletRequest request) throws Exception {
        AtomicBoolean passed = new AtomicBoolean();
        metadata.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            HttpServletRequest filtered = (HttpServletRequest) req;
            assertNull(filtered.getHeader("X-USERINFO-KONG"));
            assertFalse(filtered.getHeaders("x-userinfo-kong").hasMoreElements());
            assertEquals(-1, filtered.getIntHeader("X-USERINFO-KONG"));
            assertEquals(-1L, filtered.getDateHeader("x-userinfo-kong"));
            assertFalse(Collections.list(filtered.getHeaderNames()).stream().anyMatch("x-userinfo-kong"::equalsIgnoreCase));
            assertEquals("fixture-local-token", filtered.getHeader("X-VLStream-Token"));
            assertEquals("fixture-platform-token", filtered.getHeader("accesstoken"));
            assertEquals("fixture-tenant", filtered.getHeader("tenantid"));
            cors(filtered, passed);
        });
        assertTrue(passed.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test public void asciiUsernamePassesTheExistingFirewall() throws Exception {
        AtomicBoolean passed = new AtomicBoolean();
        cors(request("{\"userName\":\"fixture-ascii\"}"), passed);
        assertTrue(passed.get());
    }
    @Test public void latin1DecodedChineseReproducesTheProductionRejection() {
        String value = mojibake();
        assertTrue(value.indexOf('\u0088') >= 0); assertTrue(value.indexOf('\u0091') >= 0);
        assertThrows(RequestRejectedException.class, () -> cors(request(value), new AtomicBoolean()));
    }
    @Test public void asciiEscapedChineseIsSafeWithoutWeakeningTheFirewall() throws Exception {
        AtomicBoolean passed = new AtomicBoolean();
        cors(request("{\"userName\":\"\\u7528\\u6237\\u5458\"}"), passed);
        assertTrue(passed.get());
    }
    @Test public void unusedAsciiMetadataIsRemoved() throws Exception {
        filtered(request("{\"userName\":\"fixture-ascii\"}"));
    }
    @Test public void unusedChineseMetadataIsRemoved() throws Exception { filtered(request(CHINESE_JSON)); }
    @Test public void unusedMojibakeMetadataIsRemoved() throws Exception { filtered(request(mojibake())); }
    @Test public void headerRemovalIsCaseInsensitiveAndDoesNotCreateIdentity() throws Exception {
        MockHttpServletRequest request = request("{\"isAdmin\":true}");
        request.removeHeader("x-userinfo-kong"); request.addHeader("X-USERINFO-KONG", mojibake());
        filtered(request);
    }
    @Test public void allMetadataValuesAreRemovedAndOtherHeaderValuesArePreserved() throws Exception {
        MockHttpServletRequest request = request("{\"userName\":\"fixture\"}");
        request.addHeader("X-USERINFO-KONG", mojibake());
        request.addHeader("Authorization", "Bearer fixture-one");
        request.addHeader("Authorization", "Bearer fixture-two");
        metadata.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            HttpServletRequest filtered = (HttpServletRequest) req;
            assertNull(filtered.getHeader("x-userinfo-kong"));
            assertFalse(filtered.getHeaders("X-USERINFO-KONG").hasMoreElements());
            assertFalse(Collections.list(filtered.getHeaderNames()).stream().anyMatch("x-userinfo-kong"::equalsIgnoreCase));
            assertEquals("Bearer fixture-one", filtered.getHeader("authorization"));
            assertEquals(Arrays.asList("Bearer fixture-one", "Bearer fixture-two"), Collections.list(filtered.getHeaders("AUTHORIZATION")));
        });
        filtered(request);
    }
    @Test public void illegalControlInOtherHeadersIsStillRejected() {
        MockHttpServletRequest request = request("{\"userName\":\"fixture\"}");
        request.addHeader("X-Unsafe", "bad\u0088value");
        assertThrows(RequestRejectedException.class, () -> filtered(request));
    }
    @Test public void illegalControlInAuthenticationTokenIsStillRejected() {
        MockHttpServletRequest request = request("{\"userName\":\"fixture\"}");
        request.addHeader("Authorization", "Bearer bad\u0091value");
        assertThrows(RequestRejectedException.class, () -> filtered(request));
    }
    @Test public void filterRunsBeforeConfiguredSpringSecurityOrder() {
        SecurityProperties security = new SecurityProperties();
        security.getFilter().setOrder(-200);
        FilterRegistrationBean<GatewayUserInfoHeaderFilter> registration = new FilterConfig().gatewayUserInfoHeaderFilter(security);
        assertTrue(registration.getOrder() < security.getFilter().getOrder());
    }
    @Test public void filterRunsBeforeSpringSecurity() {
        FilterRegistrationBean<GatewayUserInfoHeaderFilter> registration = new FilterConfig().gatewayUserInfoHeaderFilter(new SecurityProperties());
        assertTrue(registration.getOrder() < SecurityProperties.DEFAULT_FILTER_ORDER);
        assertEquals(Collections.singleton("/*"), registration.getUrlPatterns());
    }
}
