package com.ruoyi.wvp.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wvp.conf.SipConfig;
import com.ruoyi.wvp.conf.SipIdentityConfig;
import com.ruoyi.wvp.conf.SipPlatformRunner;
import com.ruoyi.wvp.gb28181.session.SSRCFactory;
import com.ruoyi.wvp.mapper.SipConfigMapper;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 界面修改国标 SIP 身份接口的校验、持久化与生效行为测试。
 */
public class WvpServerControllerSipIdentityTest {

    private WvpServerController controller;

    private SipConfigMapper sipConfigMapper;

    private SipPlatformRunner sipPlatformRunner;

    private SSRCFactory ssrcFactory;

    private IMediaServerService mediaServerService;

    private SipConfig sipConfig;

    @Before
    public void setUp() {
        controller = new WvpServerController();
        sipConfigMapper = mock(SipConfigMapper.class);
        sipPlatformRunner = mock(SipPlatformRunner.class);
        ssrcFactory = mock(SSRCFactory.class);
        mediaServerService = mock(IMediaServerService.class);
        sipConfig = new SipConfig();
        sipConfig.setId("41010500002000000001");
        sipConfig.setDomain("4101050000");
        ReflectionTestUtils.setField(controller, "sipConfig", sipConfig);
        ReflectionTestUtils.setField(controller, "sipConfigMapper", sipConfigMapper);
        ReflectionTestUtils.setField(controller, "sipPlatformRunner", sipPlatformRunner);
        ReflectionTestUtils.setField(controller, "ssrcFactory", ssrcFactory);
        ReflectionTestUtils.setField(controller, "mediaServerService", mediaServerService);
    }

    private SipIdentityConfig identity(String sipId, String sipDomain) {
        SipIdentityConfig config = new SipIdentityConfig();
        config.setSipId(sipId);
        config.setSipDomain(sipDomain);
        return config;
    }

    @Test
    public void rejectsInvalidSipId() {
        AjaxResult result = controller.updateSipIdentity(identity("123", "4101050000"));

        assertTrue(result.isError());
        verify(sipConfigMapper, never()).upsertDefault(anyString(), anyString());
    }

    @Test
    public void rejectsInvalidSipDomain() {
        AjaxResult result = controller.updateSipIdentity(identity("44010200492000000001", "4401"));

        assertTrue(result.isError());
        verify(sipConfigMapper, never()).upsertDefault(anyString(), anyString());
    }

    @Test
    public void unchangedIdentityDoesNotPersistOrReRegister() {
        AjaxResult result = controller.updateSipIdentity(identity("41010500002000000001", "4101050000"));

        assertTrue(result.isSuccess());
        verify(sipConfigMapper, never()).upsertDefault(anyString(), anyString());
        verify(sipPlatformRunner, never()).refreshRegistrations();
    }

    @Test
    public void validIdentityPersistsAppliesAndReRegisters() {
        AjaxResult result = controller.updateSipIdentity(identity("44010200492000000001", "4401020049"));

        assertTrue(result.isSuccess());
        verify(sipConfigMapper).upsertDefault("44010200492000000001", "4401020049");
        assertEquals("44010200492000000001", sipConfig.getId());
        assertEquals("4401020049", sipConfig.getDomain());
        verify(sipPlatformRunner).refreshRegistrations();
    }

    @Test
    public void idOnlyChangeSkipsSsrcRebuild() {
        controller.updateSipIdentity(identity("44010200492000000001", "4101050000"));

        assertEquals("44010200492000000001", sipConfig.getId());
        assertEquals("4101050000", sipConfig.getDomain());
        verify(mediaServerService, never()).getAll();
    }

    @Test
    public void domainChangeRebuildsSsrcPools() {
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("zlm-local");
        when(mediaServerService.getAll()).thenReturn(Collections.singletonList(mediaServer));

        controller.updateSipIdentity(identity("41010500002000000001", "4401020049"));

        verify(ssrcFactory).initMediaServerSSRC("zlm-local", null);
        verify(sipPlatformRunner).refreshRegistrations();
    }

    @Test
    public void persistFailureKeepsRuntimeIdentity() {
        doThrow(new RuntimeException("database unavailable"))
                .when(sipConfigMapper).upsertDefault("44010200492000000001", "4401020049");

        try {
            controller.updateSipIdentity(identity("44010200492000000001", "4401020049"));
            fail("持久化失败时必须向调用方抛出异常");
        } catch (RuntimeException e) {
            // 持久化失败必须向调用方抛出，不能静默生效
        }

        assertEquals("41010500002000000001", sipConfig.getId());
        assertEquals("4101050000", sipConfig.getDomain());
        verify(sipPlatformRunner, never()).refreshRegistrations();
    }
}
