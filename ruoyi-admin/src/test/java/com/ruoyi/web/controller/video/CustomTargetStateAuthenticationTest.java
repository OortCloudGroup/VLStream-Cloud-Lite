package com.ruoyi.web.controller.video;

import com.ruoyi.custom.mapper.CustomDeviceStateMapper;
import com.ruoyi.custom.service.CustomTargetStateReader;
import com.ruoyi.custom.domain.CustomTargetHeartbeat;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.PermissionService;
import org.junit.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real native ss method-security proxy; the status mapper is an isolated fixture. */
public class CustomTargetStateAuthenticationTest {
    private AnnotationConfigApplicationContext context;
    private CustomDeviceStateMapper mapper;
    private CustomTargetStateReader reader;
    @Configuration @EnableMethodSecurity(prePostEnabled=true)
    static class Config {
        @Bean(name="ss") PermissionService permissions() { return new PermissionService(); }
        @Bean CustomDeviceStateMapper mapper() { return mock(CustomDeviceStateMapper.class); }
        @Bean CustomTargetStateReader reader(CustomDeviceStateMapper mapper) { return new CustomTargetStateReader(mapper); }
    }
    @Before public void setup() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        context = new AnnotationConfigApplicationContext(Config.class);
        mapper = context.getBean(CustomDeviceStateMapper.class); reader = context.getBean(CustomTargetStateReader.class);
        when(mapper.selectCameraTarget("exact", "default", "default")).thenReturn(new CustomTargetHeartbeat());
    }
    @After public void cleanup() {
        SecurityContextHolder.clearContext(); context.close();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
    private void login(boolean federated, String... permissions) {
        LoginUser user = new LoginUser(); user.setTenantId("default"); user.setFederated(federated);
        user.setPermissions(new HashSet<>(Arrays.asList(permissions)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }
    @Test public void customPermissionDoesNotGrantNativeTargetState() {
        login(false, "custom:device:list");
        assertThrows(AccessDeniedException.class, () -> reader.read("exact", "default", "default")); verifyNoInteractions(mapper);
    }
    @Test public void nativeListPermissionCanReadOnlyExplicitProjection() {
        login(false, "vlstream:device:list"); assertNotNull(reader.read("exact", "default", "default"));
        verify(mapper).selectCameraTarget("exact", "default", "default");
    }
    @Test public void normalFederatedProtocolAuthorizationStillUsesNativeSsSemantics() {
        login(true); assertNotNull(reader.read("exact", "default", "default"));
        verify(mapper).selectCameraTarget("exact", "default", "default");
    }
}
