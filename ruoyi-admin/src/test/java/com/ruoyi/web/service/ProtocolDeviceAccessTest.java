package com.ruoyi.web.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.service.IRtspDeviceService;
import com.ruoyi.onvif.service.IOnvifDeviceService;
import org.junit.After;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProtocolDeviceAccessTest {
    @After public void clear() { SecurityContextHolder.clearContext(); }
    private void login(String tenant) {
        LoginUser user = new LoginUser(); user.setTenantId(tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }
    @Test public void foreignTenantCannotReadLegacyProtocolDirectory() {
        login("foreign");
        IRtspDeviceService rtsp = mock(IRtspDeviceService.class);
        IOnvifDeviceService onvif = mock(IOnvifDeviceService.class);
        ProtocolDeviceAccess access = new ProtocolDeviceAccess(rtsp, onvif, "default");
        assertThrows(ServiceException.class, () -> access.rtsp(7L));
        assertThrows(ServiceException.class, () -> access.onvif(7L));
        verify(rtsp).selectRtspDeviceList(any());
        verify(onvif).selectOnvifDeviceList(any());
    }
    @Test public void sourceMustRemainVisibleInDepartmentScopedServiceResult() {
        login("default");
        IRtspDeviceService rtsp = mock(IRtspDeviceService.class);
        IOnvifDeviceService onvif = mock(IOnvifDeviceService.class);
        ProtocolDeviceAccess access = new ProtocolDeviceAccess(rtsp, onvif, "default");
        RtspDevice row = new RtspDevice(); row.setId(7L);
        when(rtsp.selectRtspDeviceList(any())).thenReturn(Collections.singletonList(row));
        assertSame(row, access.rtsp(7L));
        assertThrows(ServiceException.class, () -> access.rtsp(8L));
    }
}
