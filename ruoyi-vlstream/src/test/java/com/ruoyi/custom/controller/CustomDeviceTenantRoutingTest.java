package com.ruoyi.custom.controller;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.custom.service.CustomDeviceStateService;
import com.ruoyi.custom.service.CustomMediaService;
import com.ruoyi.custom.service.CustomTargetStateReader;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.junit.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real default-tenant resolver and MVC route selection; no database or media connections. */
public class CustomDeviceTenantRoutingTest {
    private final String topTenant = "0e391fd7-1033-4f09-88c0-187582fee462";
    private CustomDeviceMapper devices;
    private CustomDeviceStateService states;
    private VlStreamDeviceProperties properties;
    private CustomDeviceController controller;
    private MockMvc api;

    @Before public void setup() {
        login(topTenant);
        properties = new VlStreamDeviceProperties(); properties.setTenantType("multi");
        VlStreamDeviceTenantService tenants = new VlStreamDeviceTenantService(properties, mock(VlStreamDeviceMapper.class));
        devices = mock(CustomDeviceMapper.class);
        CustomDevice device = new CustomDevice(); device.setId(1L); device.setDeviceName("fixture");
        device.setStreamUrl("rtsp://fixture.invalid/live"); device.setStatus("ONLINE");
        when(devices.selectDeviceList(any())).thenReturn(Collections.singletonList(device));
        when(devices.selectDeviceById(1L)).thenReturn(device);
        states = new CustomDeviceStateService(devices, mock(CustomTargetStateReader.class), properties, tenants);
        CustomMediaService media = mock(CustomMediaService.class); when(media.isMediaAvailable()).thenReturn(true);
        controller = new CustomDeviceController(devices, media, null);
        ReflectionTestUtils.setField(controller, "deviceStates", states);
        api = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @After public void cleanup() {
        SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes();
    }

    private void login(String tenant) {
        LoginUser user = new LoginUser(); user.setTenantId(tenant); user.setFederated(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @Test public void multiTenantListSelectsLiteralRouteAndNeverTreatsListAsDeviceId() throws Exception {
        api.perform(get("/custom/device/list").param("pageNum", "1").param("pageSize", "10")
                .param("deviceName", "").param("deviceCode", "").header("X-WVP-Auth-Source", "vlstream"))
            .andExpect(handler().methodName("list")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.total").value(1));
        verify(devices).selectDeviceList(any()); verify(devices, never()).selectDeviceById(anyLong());
    }

    @Test public void mediaHealthCanBeAvailableWhileLegacyDefaultRejectsDirectory() throws Exception {
        properties.setTenantType("single");
        ServiceException denied = assertThrows(ServiceException.class, () -> controller.list(new CustomDevice()));
        assertEquals(Integer.valueOf(403), denied.getCode());
        assertEquals("设备不存在或不属于当前租户", denied.getMessage());
        api.perform(get("/custom/device/media/status")).andExpect(handler().methodName("mediaStatus"))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.available").value(true));
        verifyNoInteractions(devices);
    }

    @Test public void foreignTenantStillCannotReadListOrDetails() {
        login("foreign");
        assertThrows(ServiceException.class, () -> states.list(new CustomDevice()));
        assertThrows(ServiceException.class, () -> states.requireDevice(1L));
        verifyNoInteractions(devices);
    }

    @Test public void multiTenantDefaultAllowsExistingCustomDetailWithoutChangingOwnership() throws Exception {
        api.perform(get("/custom/device/1")).andExpect(handler().methodName("get"))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.id").value(1));
        verify(devices).selectDeviceById(1L);
    }
}
