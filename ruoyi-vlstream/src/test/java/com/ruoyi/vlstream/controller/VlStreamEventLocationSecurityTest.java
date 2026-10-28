package com.ruoyi.vlstream.controller;

import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.mapper.VlStreamEventLocationMapper;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual Spring method-security proxy; synthetic identities, no real users or roles. */
public class VlStreamEventLocationSecurityTest {
    @Test public void unprivilegedSessionCannotReadLocationAndAllowedSessionStillCannotChooseTenant() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            VlStreamEventLocationController controller = context.getBean(VlStreamEventLocationController.class);
            VlStreamEventLocationMapper mapper = context.getBean(VlStreamEventLocationMapper.class);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", "tenant-a"); body.put("deviceIds", Collections.singletonList("CAM"));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", "unused", Collections.emptyList()));
            try { controller.locations(body); fail("Permission required"); } catch (AccessDeniedException expected) { }
            verifyNoInteractions(mapper);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", "unused",
                Collections.singletonList(new SimpleGrantedAuthority("vlstream:device:list"))));
            body.put("tenantId", "tenant-b");
            assertEquals(403, controller.locations(body).get("code"));
            verifyNoInteractions(mapper);
            body.put("tenantId", "tenant-a");
            assertEquals(200, controller.locations(body).get("code"));
            verify(mapper).selectLocations(eq("tenant-a"), eq("000000"), eq(Collections.singletonList("CAM")));
        } finally { SecurityContextHolder.clearContext(); }
    }

    public static class Permission {
        public boolean hasPermi(String permission) {
            return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
        }
    }
    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    public static class TestConfiguration {
        @Bean(name = "ss") public Permission permission() { return new Permission(); }
        @Bean public VlStreamEventLocationMapper mapper() { return mock(VlStreamEventLocationMapper.class); }
        @Bean public VlStreamDeviceTenantService tenants() {
            VlStreamDeviceTenantService tenants = mock(VlStreamDeviceTenantService.class);
            when(tenants.currentTenantId()).thenReturn("tenant-a"); return tenants;
        }
        @Bean public VlStreamEventLocationController controller() {
            return new VlStreamEventLocationController(mapper(), new VlStreamDeviceProperties(), tenants());
        }
    }
}
