package com.ruoyi.framework.security.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Removes unused gateway identity metadata before the security firewall.
 * WVP authenticates with verified tokens and never trusts this user JSON.
 */
public class GatewayUserInfoHeaderFilter extends OncePerRequestFilter {
    static final String GATEWAY_USER_INFO = "x-userinfo-kong";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new HttpServletRequestWrapper(request) {
            private boolean excluded(String name) { return GATEWAY_USER_INFO.equalsIgnoreCase(name); }
            @Override public String getHeader(String name) {
                return excluded(name) ? null : super.getHeader(name);
            }
            @Override public Enumeration<String> getHeaders(String name) {
                return excluded(name) ? Collections.enumeration(Collections.emptyList()) : super.getHeaders(name);
            }
            @Override public Enumeration<String> getHeaderNames() {
                Enumeration<String> names = super.getHeaderNames();
                if (names == null) return null;
                List<String> retained = Collections.list(names);
                retained.removeIf(this::excluded);
                return Collections.enumeration(retained);
            }
            @Override public int getIntHeader(String name) {
                return excluded(name) ? -1 : super.getIntHeader(name);
            }
            @Override public long getDateHeader(String name) {
                return excluded(name) ? -1 : super.getDateHeader(name);
            }
        }, response);
    }
}
