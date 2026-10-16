package com.ruoyi.custom.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.domain.*;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.custom.controller.CustomDeviceController;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.junit.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CustomDeviceStateServiceTest {
    private CustomDeviceMapper devices;
    private CustomTargetStateReader states;
    private CustomDeviceStateService service;
    private CustomTargetHeartbeat heartbeat;
    private CustomDevice device;
    private void login(String tenant) {
        LoginUser user = new LoginUser(); user.setTenantId(tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }
    @Before public void setup() {
        login("default"); devices = mock(CustomDeviceMapper.class); states = mock(CustomTargetStateReader.class);
        VlStreamDeviceTenantService tenants = mock(VlStreamDeviceTenantService.class);
        when(tenants.defaultTenant()).thenReturn("default");
        service = new CustomDeviceStateService(devices, states, new VlStreamDeviceProperties(), tenants);
        device = new CustomDevice(); device.setId(1L); device.setStatus("ONLINE");
        device.setDeviceCode("wrong-code"); device.setDeviceName("same name");
        device.setStreamUrl("https://fixture.invalid/videocall/exact-target?room=1");
        heartbeat = new CustomTargetHeartbeat(); heartbeat.setOnline(false); heartbeat.setOnlineReason("heartbeat_timeout");
        heartbeat.setLastHeartbeatTime(new Date(System.currentTimeMillis() - 3600000));
        heartbeat.setUpdateTime(new Date());
        when(states.read("exact-target", "default", "default")).thenReturn(heartbeat);
        when(devices.selectDeviceById(1L)).thenReturn(device);
    }
    @After public void cleanup() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }
    @Test public void savedOnlineCannotOverrideExactTargetOfflineAndManagementMatchesSource() {
        assertEquals("UNKNOWN", service.requireDevice(1L).getStatus()); assertEquals("ONLINE", device.getConfiguredStatus());
        assertEquals("OFFLINE", device.getAvailability().getDeviceState());
        VideoDeviceSource source = new VideoDeviceSource(); source.setProtocol("CUSTOM"); source.setRowId("1"); source.setState("ONLINE");
        service.enrichSources(Collections.singletonList(source));
        assertEquals(device.getStatus(), source.getState()); assertEquals("ONLINE", source.getConfiguredState());
        assertEquals("heartbeat_timeout", source.getAvailability().getDeviceReason());
        verify(states, atLeastOnce()).read("exact-target", "default", "default");
        verify(states, never()).read(eq("wrong-code"), anyString(), anyString());
    }
    @Test public void freshHeartbeatRecoveryExpiresAndNeverUsesSavedOffline() {
        device.setStatus("OFFLINE"); heartbeat.setOnline(true); heartbeat.setLastHeartbeatTime(new Date());
        CustomDeviceState state = service.resolve(device);
        assertEquals("UNKNOWN", state.getState()); assertEquals("ONLINE", state.getDeviceState());
        heartbeat.setLastHeartbeatTime(new Date(System.currentTimeMillis() - 181000));
        assertEquals("UNKNOWN", service.resolve(device).getState()); assertEquals("STALE_HEARTBEAT", service.resolve(device).getDeviceReason());
        heartbeat.setLastHeartbeatTime(new Date()); assertEquals("ONLINE", service.resolve(device).getDeviceState());
    }
    @Test public void nullMissingAndFutureHeartbeatAreUnknown() {
        heartbeat.setOnline(null); assertEquals("UNKNOWN", service.resolve(device).getState());
        heartbeat.setOnline(true); heartbeat.setLastHeartbeatTime(null); assertEquals("STATE_UNREPORTED", service.resolve(device).getDeviceReason());
        heartbeat.setLastHeartbeatTime(new Date(System.currentTimeMillis() + 60000));
        assertEquals("FUTURE_HEARTBEAT", service.resolve(device).getDeviceReason()); assertEquals("UNKNOWN", service.resolve(device).getState());
        when(states.read(anyString(), anyString(), anyString())).thenReturn(null);
        assertEquals("NO_AUTHORIZED_HEARTBEAT", service.resolve(device).getDeviceReason());
    }
    @Test public void ordinaryConfiguredStreamsRemainUnknownButCanAttemptMedia() {
        for (String url : Arrays.asList("rtsp://fixture.invalid/live", "rtmp://fixture.invalid/live", "https://fixture.invalid/live.m3u8")) {
            device.setStreamUrl(url); assertEquals("UNKNOWN", service.resolve(device).getState()); service.requirePlayable(device);
        }
        verifyNoInteractions(states);
        device.setStreamUrl(null);assertThrows(ServiceException.class,()->service.requirePlayable(device));
    }
    @Test public void bindingUsesOnlyValidExplicitTargetNotNameCodeOrCredentials() {
        for (String url : Arrays.asList("https:///videocall/target", "http://user:secret@fixture.invalid/videocall/target",
            "https://fixture.invalid/videocall/", "https://fixture.invalid/videocall/a%2Fb", "file:///videocall/a"))
            assertNull(CustomDeviceStateService.cameraTarget(url));
        assertEquals("exact-target", CustomDeviceStateService.cameraTarget(device.getStreamUrl()));
    }
    @Test public void permissionDenialIsUnknownWithoutExposingAnotherTargetState() {
        when(states.read(anyString(), anyString(), anyString())).thenThrow(new AccessDeniedException("fixture"));
        assertEquals("UNKNOWN", service.resolve(device).getState()); assertEquals("TARGET_STATUS_FORBIDDEN", service.resolve(device).getDeviceReason());
    }
    @Test public void foreignTenantAndAnonymousFailBeforeDeviceReads() {
        login("foreign"); assertThrows(ServiceException.class, () -> service.requireDevice(1L));
        verifyNoInteractions(devices, states); SecurityContextHolder.clearContext();
        assertThrows(ServiceException.class, () -> service.resolve(device)); verifyNoInteractions(states);
    }
    @Test public void runtimeFilterRunsBeforePagingAndRestoresQuery() {
        com.ruoyi.custom.availability.CustomAvailabilityMonitor monitor=mock(com.ruoyi.custom.availability.CustomAvailabilityMonitor.class);
        ReflectionTestUtils.setField(service,"availabilityMonitor",monitor);
        when(monitor.cached(any())).thenAnswer(call->evidence(((CustomDevice)call.getArgument(0)).getId()==1L?"OFFLINE":"UNKNOWN"));
        CustomDevice unknown = new CustomDevice(); unknown.setId(2L); unknown.setStatus("ONLINE"); unknown.setStreamUrl("rtsp://fixture.invalid/live");
        when(devices.selectDeviceList(any())).thenAnswer(call -> {
            assertNull(((CustomDevice) call.getArgument(0)).getStatus()); return Arrays.asList(unknown, device);
        });
        CustomDeviceController controller = new CustomDeviceController(devices, null, null);
        ReflectionTestUtils.setField(controller, "deviceStates", service);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addParameter("pageNum", "1"); request.addParameter("pageSize", "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        CustomDevice query = new CustomDevice(); query.setStatus("OFFLINE");
        com.ruoyi.common.core.page.TableDataInfo result = controller.list(query);
        assertEquals(1L, result.getTotal()); assertEquals(1L, ((CustomDevice) result.getRows().get(0)).getId().longValue()); assertEquals("OFFLINE", query.getStatus());
        request.setParameter("pageNum", "2"); result = controller.list(query); assertEquals(1L, result.getTotal()); assertTrue(result.getRows().isEmpty());
    }
    @Test public void confirmedOfflineBlocksBothLegacyAndLeasePlaybackBeforeMedia() {
        com.ruoyi.custom.availability.CustomAvailabilityMonitor monitor=mock(com.ruoyi.custom.availability.CustomAvailabilityMonitor.class);
        ReflectionTestUtils.setField(service,"availabilityMonitor",monitor);when(monitor.cached(any())).thenReturn(evidence("OFFLINE"));when(monitor.preflight(any())).thenReturn(evidence("OFFLINE"));
        com.ruoyi.custom.service.CustomMediaService media = mock(com.ruoyi.custom.service.CustomMediaService.class);
        CustomPreviewSessionService sessions = mock(CustomPreviewSessionService.class);
        CustomDeviceController controller = new CustomDeviceController(devices, media, null);
        ReflectionTestUtils.setField(controller, "deviceStates", service); ReflectionTestUtils.setField(controller, "previewSessions", sessions);
        assertThrows(ServiceException.class, () -> controller.preview(1L, false));
        assertThrows(ServiceException.class, () -> controller.preview(1L, true)); verifyNoInteractions(media, sessions);
    }
    @Test public void readOnlyEvidenceCannotBeForgedByRequestJson() throws Exception {
        CustomDevice parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
            "{\"status\":\"ONLINE\",\"configuredStatus\":\"OFFLINE\",\"availability\":{\"state\":\"ONLINE\"}}", CustomDevice.class);
        assertNull(parsed.getAvailability()); assertNull(parsed.getConfiguredStatus()); assertEquals("ONLINE", parsed.getStatus());
    }
    @Test public void nativePermissionExpressionRemainsTheAuthority() throws Exception {
        assertEquals("@ss.hasPermi('vlstream:device:list')", CustomTargetStateReader.class
            .getMethod("read", String.class, String.class, String.class)
            .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());
    }
    private com.ruoyi.custom.availability.CustomAvailabilityCache.Evidence evidence(String state){
        com.ruoyi.custom.availability.CustomAvailabilityCache.Evidence result=new com.ruoyi.custom.availability.CustomAvailabilityCache.Evidence();
        result.getSnapshot().setState(state);result.getSnapshot().setCheckedAt(System.currentTimeMillis());result.setExpiresAt(System.currentTimeMillis()+90000);return result;
    }
    @Test public void independentSourceOnlineDoesNotRequireNativeHeartbeat() {
        device.setStreamUrl("rtsp://fixture.invalid/live");device.setStatus("OFFLINE");
        com.ruoyi.custom.availability.CustomAvailabilityMonitor monitor=mock(com.ruoyi.custom.availability.CustomAvailabilityMonitor.class);
        ReflectionTestUtils.setField(service,"availabilityMonitor",monitor);when(monitor.cached(any())).thenReturn(evidence("ONLINE"));
        assertEquals("ONLINE",service.enrich(device).getStatus());assertEquals("OFFLINE",device.getConfiguredStatus());verifyNoInteractions(states);
    }
    @Test public void nativeDeviceOfflineCannotOverrideIndependentSourceOnline() {
        com.ruoyi.custom.availability.CustomAvailabilityMonitor monitor=mock(com.ruoyi.custom.availability.CustomAvailabilityMonitor.class);
        ReflectionTestUtils.setField(service,"availabilityMonitor",monitor);when(monitor.cached(any())).thenReturn(evidence("ONLINE"));
        assertEquals("ONLINE",service.resolve(device).getState());assertEquals("OFFLINE",service.resolve(device).getDeviceState());
    }
    @Test public void invalidLegacyCameraAddressCannotBreakTheWholeList(){
        device.setStreamUrl("https://fixture.invalid/videocall/exact-target#invalid");
        com.ruoyi.custom.availability.CustomAvailabilityMonitor monitor=mock(com.ruoyi.custom.availability.CustomAvailabilityMonitor.class);
        ReflectionTestUtils.setField(service,"availabilityMonitor",monitor);when(monitor.cached(any())).thenThrow(new com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure("CONFIGURATION_ERROR"));
        assertEquals("UNKNOWN",service.enrich(device).getStatus());assertEquals("CONFIGURATION_ERROR",device.getAvailability().getReason());verifyNoInteractions(states);
    }
}
