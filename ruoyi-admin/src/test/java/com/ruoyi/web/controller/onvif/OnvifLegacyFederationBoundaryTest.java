package com.ruoyi.web.controller.onvif;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifJavaPZT;
import com.ruoyi.onvif.domain.bo.AbsoluteMoveBo;
import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import com.ruoyi.onvif.domain.bo.PresetsBo;
import com.ruoyi.onvif.service.IOnvifJavaService;
import com.ruoyi.onvif.service.IOnvifService;
import org.junit.After;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

/** Raw-address controls are not a tenant ownership boundary. */
public class OnvifLegacyFederationBoundaryTest {
    @After public void cleanup() { SecurityContextHolder.clearContext(); }

    private void login(boolean federated) {
        LoginUser user = new LoginUser();
        user.setFederated(federated);
        user.setTenantId("tenant-a");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @Test public void federatedRawPresetActionsFailBeforeAnyDeviceRequest() {
        login(true);
        OnvifServiceController controller = new OnvifServiceController();
        assertThrows(ServiceException.class, () -> controller.getPresets(new AbsoluteMoveBo()));
        assertThrows(ServiceException.class, () -> controller.gotoPreset(new PresetsBo()));
        assertThrows(ServiceException.class, () -> controller.removePreset(new PresetsBo()));
        assertThrows(ServiceException.class, () -> controller.addPreset(new PresetsBo()));
    }

    @Test public void federatedRawDiscoveryAndCredentialEndpointsFailBeforeAnyDeviceRequest() {
        IOnvifService service = mock(IOnvifService.class);
        OnvifServiceController controller = new OnvifServiceController();
        ReflectionTestUtils.setField(controller, "service", service);
        FetchMainAndSubStreamUrisBo request = new FetchMainAndSubStreamUrisBo();
        login(true);
        assertThrows(ServiceException.class, () -> controller.getDigitalChannel(request));
        assertThrows(ServiceException.class, () -> controller.getOnvifDeviceInfo(request));
        assertThrows(ServiceException.class, () -> controller.getChannelToken(request));
        verifyNoInteractions(service);
    }

    @Test public void rawOnvifJavaControlIsBlockedForFederationAndRetainedForNative() {
        IOnvifJavaService service = mock(IOnvifJavaService.class);
        OnvifJavaServiceController controller = new OnvifJavaServiceController();
        ReflectionTestUtils.setField(controller, "service", service);
        OnvifJavaPZT request = new OnvifJavaPZT();
        login(true);
        assertThrows(ServiceException.class, () -> controller.onvifPZTStart(request));
        verifyNoInteractions(service);
        login(false);
        controller.onvifPZTStart(request);
        verify(service).ptz(request);
    }
}
