package com.ruoyi.wvp.gb28181;

import com.ruoyi.wvp.conf.SipConfig;
import com.ruoyi.wvp.conf.UserSetting;
import com.ruoyi.wvp.gb28181.transmit.ISIPProcessorObserver;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

/**
 * SipLayer 生命周期与端口释放测试
 */
public class SipLayerTest {

    private SipLayer sipLayer;

    @After
    public void tearDown() {
        if (sipLayer != null) {
            sipLayer.destroy();
        }
    }

    /**
     * 测试 SipLayer 启动、销毁与二次重启时的端口释放和平滑重载能力
     */
    @Test
    public void testSipLayerLifecycleAndRestart() {
        sipLayer = new SipLayer();

        SipConfig sipConfig = new SipConfig();
        sipConfig.setIp("127.0.0.1");
        // 使用非业务默认端口进行单元测试，避免环境占用
        sipConfig.setPort(18116);
        ReflectionTestUtils.setField(sipLayer, "sipConfig", sipConfig);

        UserSetting userSetting = new UserSetting();
        userSetting.setSipLog(false);
        ReflectionTestUtils.setField(sipLayer, "userSetting", userSetting);

        ISIPProcessorObserver observer = mock(ISIPProcessorObserver.class);
        ReflectionTestUtils.setField(sipLayer, "sipProcessorObserver", observer);

        // 第一次启动
        sipLayer.run();
        assertNotNull("首次启动后应能获取 UDP Provider", sipLayer.getUdpSipProvider("127.0.0.1"));
        assertNotNull("首次启动后应能获取 TCP Provider", sipLayer.getTcpSipProvider("127.0.0.1"));

        // 触发销毁
        sipLayer.destroy();
        assertNull("销毁后 UDP Provider 应清空", sipLayer.getUdpSipProvider("127.0.0.1"));
        assertNull("销毁后 TCP Provider 应清空", sipLayer.getTcpSipProvider("127.0.0.1"));

        // 模拟 DevTools 热重载或容器二次启动：在同一 JVM 实例中再次在相同端口启动
        sipLayer.run();
        assertNotNull("二次重启后应能再次成功绑定并获取 UDP Provider", sipLayer.getUdpSipProvider("127.0.0.1"));
        assertNotNull("二次重启后应能再次成功绑定并获取 TCP Provider", sipLayer.getTcpSipProvider("127.0.0.1"));
    }
}
