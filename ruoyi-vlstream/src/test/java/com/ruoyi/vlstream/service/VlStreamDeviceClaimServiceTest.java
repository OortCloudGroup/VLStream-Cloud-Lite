package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import org.junit.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VlStreamDeviceClaimServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VlStreamDeviceMapper devices = mock(VlStreamDeviceMapper.class);
    private final VlStreamDeviceTenantService tenants = mock(VlStreamDeviceTenantService.class);
    private final VlStreamDeviceClaimService service = new VlStreamDeviceClaimService(jdbc, tenants, devices,
            mock(VlStreamDeviceAccessRefService.class), mock(ApplicationEventPublisher.class),
            "fixture-only-claim-key-01234567890123456789");
    private static final String PROOF = "8MkHhLSxve2KCwwiJae43WYsMGbkHvuwErbPFX0eNMA";

    @Test public void factorySignatureBindsDeviceId() {
        service.verify("CAM-1", PROOF);
        assertThrows(ServiceException.class, () -> service.verify("CAM-2", PROOF));
        assertThrows(ServiceException.class, () -> service.verify("CAM-1", ""));
    }

    @Test public void repeatedScanByOwnerIsIdempotent() {
        prepare("member", true);
        assertEquals("7", service.claim("CAM-1", PROOF));
    }

    @Test public void claimedDefaultAndNonDefaultAreProtected() {
        prepare("default", true);
        assertEquals(Integer.valueOf(409), assertThrows(ServiceException.class, () -> service.claim("CAM-1", PROOF)).getCode());
        prepare("another", false);
        assertEquals(Integer.valueOf(409), assertThrows(ServiceException.class, () -> service.claim("CAM-1", PROOF)).getCode());
    }

    private void prepare(String owner, boolean consumed) {
        VlStreamDevice row = new VlStreamDevice();
        row.setId(7L);
        row.setDeviceId("CAM-1");
        when(devices.selectByDeviceIdForUpdate("CAM-1")).thenReturn(row);
        when(tenants.currentTenantId()).thenReturn("member");
        when(tenants.defaultTenant()).thenReturn("default");
        when(tenants.deviceTenant(row)).thenReturn(owner);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(consumed);
    }
}
