package com.ruoyi.wvp.conf;

import com.ruoyi.wvp.mapper.SipConfigMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动加载国标 SIP 身份的优先级与兜底行为测试。
 */
public class SipConfigDatabaseInitializerTest {

    private SipConfig applicationConfig() {
        SipConfig sipConfig = new SipConfig();
        sipConfig.setId("41010500002000000001");
        sipConfig.setDomain("4101050000");
        return sipConfig;
    }

    private SipConfigDatabaseInitializer initializer(SipConfigMapper mapper, SipConfig sipConfig) {
        SipConfigDatabaseInitializer initializer = new SipConfigDatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "sipConfigMapper", mapper);
        ReflectionTestUtils.setField(initializer, "sipConfig", sipConfig);
        return initializer;
    }

    @Test
    public void storedIdentityOverridesApplicationConfig() {
        SipConfigMapper mapper = mock(SipConfigMapper.class);
        SipIdentityConfig stored = new SipIdentityConfig();
        stored.setSipId("44010200492000000001");
        stored.setSipDomain("4401020049");
        when(mapper.selectDefault()).thenReturn(stored);
        SipConfig sipConfig = applicationConfig();

        initializer(mapper, sipConfig).run();

        assertEquals("44010200492000000001", sipConfig.getId());
        assertEquals("4401020049", sipConfig.getDomain());
    }

    @Test
    public void missingRowKeepsApplicationConfig() {
        SipConfigMapper mapper = mock(SipConfigMapper.class);
        when(mapper.selectDefault()).thenReturn(null);
        SipConfig sipConfig = applicationConfig();

        initializer(mapper, sipConfig).run();

        assertEquals("41010500002000000001", sipConfig.getId());
        assertEquals("4101050000", sipConfig.getDomain());
    }

    @Test
    public void invalidStoredIdentityKeepsApplicationConfig() {
        SipConfigMapper mapper = mock(SipConfigMapper.class);
        SipIdentityConfig stored = new SipIdentityConfig();
        stored.setSipId("not-a-gb-id");
        stored.setSipDomain("4101050000");
        when(mapper.selectDefault()).thenReturn(stored);
        SipConfig sipConfig = applicationConfig();

        initializer(mapper, sipConfig).run();

        assertEquals("41010500002000000001", sipConfig.getId());
        assertEquals("4101050000", sipConfig.getDomain());
    }

    @Test
    public void databaseFailureKeepsApplicationConfig() {
        SipConfigMapper mapper = mock(SipConfigMapper.class);
        when(mapper.selectDefault()).thenThrow(new RuntimeException("database unavailable"));
        SipConfig sipConfig = applicationConfig();

        initializer(mapper, sipConfig).run();

        assertEquals("41010500002000000001", sipConfig.getId());
        assertEquals("4101050000", sipConfig.getDomain());
    }
}
