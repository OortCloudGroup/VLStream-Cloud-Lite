package com.ruoyi.web.controller.video;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.deviceclassification.service.DeviceClassificationService;
import com.ruoyi.custom.availability.*;
import com.ruoyi.custom.controller.CustomDeviceController;
import com.ruoyi.custom.domain.*;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.custom.service.*;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.junit.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual native ss method-security + real tenant/state service. Media/network and mapper are fixtures. */
public class CustomAvailabilityPermissionTest {
    @Configuration @EnableMethodSecurity(prePostEnabled=true) static class Config {
        @Bean(name="ss") PermissionService permissions(){return new PermissionService();}
        @Bean CustomDeviceMapper mapper(){return mock(CustomDeviceMapper.class);}
        @Bean CustomTargetStateReader targets(){return mock(CustomTargetStateReader.class);}
        @Bean CustomAvailabilityMonitor monitor(){return mock(CustomAvailabilityMonitor.class);}
        @Bean CustomMediaBindings bindings(){return mock(CustomMediaBindings.class);}
        @Bean com.ruoyi.wvp.media.service.IMediaServerService mediaServers(){return mock(com.ruoyi.wvp.media.service.IMediaServerService.class);}
        @Bean VlStreamDeviceTenantService tenants(){VlStreamDeviceTenantService t=mock(VlStreamDeviceTenantService.class);when(t.defaultTenant()).thenReturn("default");return t;}
        @Bean CustomDeviceStateService states(CustomDeviceMapper m,CustomTargetStateReader r,VlStreamDeviceTenantService t){return new CustomDeviceStateService(m,r,new VlStreamDeviceProperties(),t);}
        @Bean CustomMediaService media(){return mock(CustomMediaService.class);}
        @Bean CustomPreviewSessionService sessions(){return mock(CustomPreviewSessionService.class);}
        @Bean DeviceClassificationService classifications(){return mock(DeviceClassificationService.class);}
        @Bean CustomDeviceController controller(CustomDeviceMapper m,CustomMediaService s,DeviceClassificationService c){return new CustomDeviceController(m,s,c);}
    }
    AnnotationConfigApplicationContext context;CustomDeviceController controller;CustomDeviceMapper mapper;CustomAvailabilityMonitor monitor;
    void login(String tenant,String... permissions){LoginUser u=new LoginUser();u.setUserId(2L);u.setTenantId(tenant);u.setPermissions(new HashSet<>(Arrays.asList(permissions)));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,Collections.emptyList()));}
    @Before public void setup(){MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("tenantId","default");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));context=new AnnotationConfigApplicationContext(Config.class);controller=context.getBean(CustomDeviceController.class);mapper=context.getBean(CustomDeviceMapper.class);monitor=context.getBean(CustomAvailabilityMonitor.class);CustomDevice d=new CustomDevice();d.setId(1L);d.setStreamUrl("rtsp://192.168.1.20/video");d.setStatus("ONLINE");when(mapper.selectDeviceById(1L)).thenReturn(d);when(mapper.selectDeviceList(any())).thenReturn(Collections.singletonList(d));CustomAvailabilityCache.Evidence e=new CustomAvailabilityCache.Evidence();e.getSnapshot().setState("ONLINE");e.getSnapshot().setReason("AVAILABLE");e.getSnapshot().setCheckedAt(System.currentTimeMillis());e.setExpiresAt(System.currentTimeMillis()+30000);when(monitor.cached(any())).thenReturn(e);when(monitor.preflight(any())).thenReturn(e);}
    @After public void cleanup(){if(context!=null)context.close();SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();}
    @Test public void customListPermissionNeedsNoVlsHeartbeatPermission(){login("default","custom:device:list");assertEquals(1,controller.list(new CustomDevice()).getTotal());verifyNoInteractions(org.springframework.test.util.AopTestUtils.<CustomTargetStateReader>getTargetObject(context.getBean(CustomTargetStateReader.class)));verify(monitor,never()).preflight(any());}
    @Test public void anonymousAndMissingPermissionCannotReadOrProbe(){SecurityContextHolder.clearContext();assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.list(new CustomDevice()));login("default","custom:device:list");assertThrows(AccessDeniedException.class,()->controller.preview(1L,true));verify(monitor,never()).preflight(any());verify(context.getBean(CustomPreviewSessionService.class),never()).start(anyLong(),anyLong());}
    @Test public void foreignTenantCannotEnumerateOrProbeEvenWithPermissionAndSpoofedHeader(){login("foreign","custom:device:list","custom:device:play");assertThrows(ServiceException.class,()->controller.list(new CustomDevice()));assertThrows(ServiceException.class,()->controller.preview(1L,true));verifyNoInteractions(mapper);verify(monitor,never()).preflight(any());}
    @Test public void allowedIndependentSourcePassesControlledPreflight(){login("default","custom:device:play");controller.preview(1L,true);verify(monitor).preflight(any());verify(context.getBean(CustomPreviewSessionService.class)).start(1L,2L);verifyNoInteractions(context.getBean(CustomTargetStateReader.class));}
    @Test public void foreignRecordingQueriesCannotBypassSourceScope(){login("foreign","custom:device:record");assertThrows(ServiceException.class,()->controller.getRecordPlan(1L));assertThrows(ServiceException.class,()->controller.recordStatus(1L));verify(mapper,never()).selectPlanByDeviceId(anyLong());verifyNoInteractions(context.getBean(CustomMediaService.class));}
    @Test public void addEditAuditExcludesSourceCredentials()throws Exception{for(String method:Arrays.asList("add","edit")){com.ruoyi.common.annotation.Log audit=CustomDeviceController.class.getMethod(method,CustomDevice.class).getAnnotation(com.ruoyi.common.annotation.Log.class);assertTrue(Arrays.asList(audit.excludeParamNames()).contains("streamUrl"));}}
}
