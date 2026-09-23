package com.ruoyi.vlstream.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import org.junit.After;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VlStreamDeviceTenantServiceTest {
    private final VlStreamDeviceProperties properties = new VlStreamDeviceProperties();
    private final VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
    private final VlStreamDeviceTenantService service = new VlStreamDeviceTenantService(properties, devices);

    @After
    public void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    public void absentTenantUsesDefaultForNewDeviceAndKeepsExistingOwner() {
        properties.setDefaultTenantId("default-tenant");
        JSONObject message = JSON.parseObject("{\"deviceId\":\"CAM-1\"}");
        assertEquals("default-tenant", service.resolveReportedTenant(message, null));
        VlStreamDevice existing = new VlStreamDevice();
        assertEquals("default-tenant", service.resolveReportedTenant(message, existing));
        existing.setTenantId("tenant-a");
        assertEquals("tenant-a", service.resolveReportedTenant(message, existing));
    }

    @Test
    public void provisioningProofMatchesIndependentNodeCryptoVector() {
        properties.setTenantBindingSecret("test-only-tenant-key-01234567890123456789");
        JSONObject message = provisionedMessage();
        assertEquals("tenant-a", service.resolveReportedTenant(message, null));
        message.put("tenantId", "tenant-b");
        assertEquals(Integer.valueOf(403), assertThrows(ServiceException.class,
            () -> service.resolveReportedTenant(message, null)).getCode());
        message.put("tenantId", "tenant-a");
        message.put("deviceId", "CAM-2");
        assertThrows(ServiceException.class, () -> service.resolveReportedTenant(message, null));
    }

    @Test
    public void rawTenantCannotClaimDeviceOrChangeExistingOwner() {
        properties.setTenantBindingSecret("test-only-tenant-key-01234567890123456789");
        JSONObject message = provisionedMessage();
        VlStreamDevice existing = new VlStreamDevice();
        existing.setTenantId("tenant-b");
        assertThrows(ServiceException.class, () -> service.resolveReportedTenant(message, existing));
        message.remove("tenantBindingProof");
        assertThrows(ServiceException.class, () -> service.resolveReportedTenant(message, null));
        properties.setTenantBindingSecret("");
        assertEquals(Integer.valueOf(503), assertThrows(ServiceException.class,
            () -> service.resolveReportedTenant(provisionedMessage(), null)).getCode());
    }

    @Test
    public void verifiedLoginOverridesCallerScopeAndRejectsOtherTenantDevice() {
        authenticate("tenant-a", true);
        VlStreamDevice query = new VlStreamDevice();
        query.setTenantId("tenant-b");
        query.setLegacyTenantId("tenant-a");
        service.scopeQuery(query);
        assertEquals("tenant-a", query.getTenantId());
        assertEquals("000000", query.getLegacyTenantId());
        VlStreamDevice device = new VlStreamDevice();
        device.setTenantId("tenant-b");
        when(devices.selectById(1L)).thenReturn(device);
        assertEquals(Integer.valueOf(403), assertThrows(ServiceException.class,
            () -> service.requireDevice(1L)).getCode());
        device.setTenantId("tenant-a");
        assertSame(device, service.requireDevice(1L));
    }

    @Test
    public void legacyDevicesAreVisibleOnlyToDefaultTenantAndMissingFederatedTenantIsRejected() {
        VlStreamDevice device = new VlStreamDevice();
        when(devices.selectById(1L)).thenReturn(device);
        authenticate("tenant-a", true);
        assertThrows(ServiceException.class, () -> service.requireDevice(1L));
        authenticate("000000", true);
        assertSame(device, service.requireDevice(1L));
        authenticate(null, true);
        assertThrows(ServiceException.class, () -> service.requireDevice(1L));
        authenticate(null, false);
        assertSame(device, service.requireDevice(1L));
    }

    private JSONObject provisionedMessage() {
        return JSON.parseObject("{\"deviceId\":\"CAM-1\",\"tenantId\":\"tenant-a\","
            + "\"tenantBindingProof\":\"v1.ho0hxAF55nWMVM4PZrRTPZWmx58_zdVju_BDzD8abRA\"}");
    }

    private void authenticate(String tenantId, boolean federated) {
        LoginUser user = new LoginUser();
        user.setTenantId(tenantId);
        user.setFederated(federated);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }
}
