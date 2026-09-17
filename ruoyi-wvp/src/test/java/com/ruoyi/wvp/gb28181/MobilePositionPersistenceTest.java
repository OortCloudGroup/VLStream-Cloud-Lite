package com.ruoyi.wvp.gb28181;

import com.ruoyi.wvp.conf.UserSetting;
import com.ruoyi.wvp.gb28181.bean.MobilePosition;
import com.ruoyi.wvp.mapper.DeviceChannelMapper;
import com.ruoyi.wvp.mapper.DeviceMobilePositionMapper;
import com.ruoyi.wvp.service.IMobilePositionService;
import com.ruoyi.wvp.service.impl.MobilePositionServiceImpl;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.Collections;
import static org.mockito.Mockito.*;

public class MobilePositionPersistenceTest {
    @Test public void singleReportRollsBackWhenChannelWriteFails() {
        MobilePositionServiceImpl service = new MobilePositionServiceImpl();
        DeviceChannelMapper channels = mock(DeviceChannelMapper.class);
        DeviceMobilePositionMapper history = mock(DeviceMobilePositionMapper.class);
        UserSetting settings = mock(UserSetting.class);
        when(settings.getSavePositionHistory()).thenReturn(true);
        ReflectionTestUtils.setField(service, "channelMapper", channels);
        ReflectionTestUtils.setField(service, "mobilePositionMapper", history);
        ReflectionTestUtils.setField(service, "userSetting", settings);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(tx.getTransaction(any())).thenReturn(status);
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        IMobilePositionService proxy = (IMobilePositionService) factory.getProxy();
        MobilePosition position = new MobilePosition();
        position.setDeviceId("test-device");
        position.setChannelId(1);
        doThrow(new IllegalStateException("test database failure")).when(channels).batchUpdatePosition(anyList());
        try { proxy.add(position); org.junit.Assert.fail("write should fail"); }
        catch (IllegalStateException expected) { }
        verify(history).batchadd(anyList());
        verify(tx).rollback(status);
        verify(tx, never()).commit(any());
    }

    @Test public void batchWritesImmediatelyWithoutRedis() {
        MobilePositionServiceImpl service = new MobilePositionServiceImpl();
        DeviceChannelMapper channels = mock(DeviceChannelMapper.class);
        DeviceMobilePositionMapper history = mock(DeviceMobilePositionMapper.class);
        UserSetting settings = mock(UserSetting.class);
        when(settings.getSavePositionHistory()).thenReturn(true);
        ReflectionTestUtils.setField(service, "channelMapper", channels);
        ReflectionTestUtils.setField(service, "mobilePositionMapper", history);
        ReflectionTestUtils.setField(service, "userSetting", settings);
        MobilePosition position = new MobilePosition();
        position.setChannelId(1);
        position.setDeviceId("test-device");
        service.add(Collections.singletonList(position));
        verify(history).batchadd(anyList());
        verify(channels).batchUpdatePosition(anyList());
    }
}
