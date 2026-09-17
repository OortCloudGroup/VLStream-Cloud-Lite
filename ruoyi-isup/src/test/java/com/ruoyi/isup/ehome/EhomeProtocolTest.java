package com.ruoyi.isup.ehome;

import com.ruoyi.isup.mapper.EhomeDeviceMapper;
import com.ruoyi.isup.service.cmsService.HCISUPCMS;
import com.sun.jna.Memory;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EhomeProtocolTest {
    @Test public void administratorAllDataRequiresBothExplicitRoleAndPermission() {
        com.ruoyi.common.core.domain.model.LoginUser login = new com.ruoyi.common.core.domain.model.LoginUser();
        com.ruoyi.common.core.domain.entity.SysUser user = new com.ruoyi.common.core.domain.entity.SysUser();
        com.ruoyi.common.core.domain.entity.SysRole role = new com.ruoyi.common.core.domain.entity.SysRole();
        role.setRoleKey("admin"); role.setDataScope("1"); role.setStatus("0");
        user.setUserId(139L); user.setRoles(java.util.Collections.singletonList(role));
        login.setUser(user); login.setPermissions(java.util.Collections.singleton("*:*:*"));
        assertTrue(EhomeDevices.hasAllDataAdminRole(login));
        role.setDataScope("2"); assertFalse(EhomeDevices.hasAllDataAdminRole(login));
        role.setDataScope("1"); role.setStatus("1"); assertFalse(EhomeDevices.hasAllDataAdminRole(login));
        role.setStatus("0"); login.setPermissions(java.util.Collections.singleton("ehome:device:list"));
        assertFalse(EhomeDevices.hasAllDataAdminRole(login));
    }
    @Test public void oldProtocolsAreSeparatedFromIsup5() {
        assertTrue(EhomeNative.legacy("2.6"));
        assertTrue(EhomeNative.legacy("3.0"));
        assertTrue(EhomeNative.legacy("4.0"));
        assertFalse(EhomeNative.legacy("5.0"));
        assertFalse(EhomeNative.legacy(""));
        assertFalse(EhomeNative.requiresPush("2.6"));
        assertFalse(EhomeNative.requiresPush("3.0"));
        assertTrue(EhomeNative.requiresPush("4.0"));
    }
    @Test public void nativeVersionsAndNullTerminatedStrings() {
        assertEquals("4.0", EhomeNative.version(new byte[]{4,0,0,0,0,0}));
        assertEquals("2.6", EhomeNative.version(new byte[]{'2','.', '6',0,0,0}));
        assertEquals("", EhomeNative.version(new byte[6]));
        assertEquals("cam", EhomeNative.text(new byte[]{'c','a','m',0,'x'}));
    }
    @Test public void channelQueryIncludesDigitalChannelsFromTotalCount() {
        assertEquals(8, EhomeSdk.channelRange(33, 8).size());
        assertEquals(33, EhomeSdk.channelRange(33, 8).get(0).getId());
        assertEquals(40, EhomeSdk.channelRange(33, 8).get(7).getId());
        assertTrue(EhomeSdk.channelRange(0, 0).isEmpty());
    }
    @Test public void registrationReconnectUsesStableDeviceIdentityAndNoCredentials() {
        EhomeDeviceMapper mapper = mock(EhomeDeviceMapper.class);
        when(mapper.findRegisteredId("test-legacy")).thenReturn(17L);
        EhomeRegistration registration = new EhomeRegistration(mapper, new EhomeConfig());
        HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 info = registration("2.6");
        HCISUPCMS.NET_EHOME_SERVER_INFO reply = new HCISUPCMS.NET_EHOME_SERVER_INFO();
        Memory buffer = new Memory(reply.size());
        assertTrue(registration.online(99, info.getPointer(), info.size(), buffer, reply.size()));
        ArgumentCaptor<EhomeDevice> device = ArgumentCaptor.forClass(EhomeDevice.class);
        verify(mapper).updateRegistration(device.capture());
        verify(mapper, never()).insertRegistration(any());
        assertEquals(Long.valueOf(17), device.getValue().getId());
        assertEquals(Integer.valueOf(99), device.getValue().getLuserId());
        assertEquals("2.6", device.getValue().getDevProtocolVersion());
        assertEquals(15, buffer.getInt(4));
        registration.offline(99);
        verify(mapper).markOffline(99);
    }
    @Test public void rejectIsup5AndTruncatedNativeBuffers() {
        EhomeDeviceMapper mapper = mock(EhomeDeviceMapper.class);
        EhomeRegistration registration = new EhomeRegistration(mapper, new EhomeConfig());
        HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 info = registration("5.0");
        assertFalse(registration.register(1, info.getPointer(), info.size()));
        assertFalse(registration.register(1, info.getPointer(), 16));
        verifyNoInteractions(mapper);
    }
    private HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 registration(String version) {
        HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 info = new HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12();
        EhomeNative.copy("test-legacy", info.struRegInfo.byDeviceID);
        System.arraycopy(version.getBytes(StandardCharsets.UTF_8), 0, info.struRegInfo.byDevProtocolVersion, 0, version.length());
        info.write();
        return info;
    }
}
