package com.ruoyi.wvp.media.zlm;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.ruoyi.wvp.conf.MediaConfig;
import com.ruoyi.wvp.conf.DynamicTask;
import com.ruoyi.wvp.gb28181.event.EventPublisher;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.event.mediaServer.MediaServerChangeEvent;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Map;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ZLMMediaServerStatusManagerTest {
    @Test public void lateMediaStartupRecoversAfterAnInitialFailedProbe() throws Exception {
        Fixture f=new Fixture();f.queue(0);
        when(f.api.getMediaServerConfig(f.node)).thenReturn(null,healthy());
        f.manager.execute(0);assertFalse(f.node.isStatus());
        f.manager.execute(10000);assertTrue(f.node.isStatus());
        verify(f.events).mediaServerOnlineEventPublish(f.node);
        assertTrue(f.primary().isEmpty());assertTrue(f.secondary().isEmpty());
        f.manager.execute(20000);verify(f.api,times(2)).getMediaServerConfig(f.node);
    }
    @Test public void secondaryBackoffEventuallyRetriesInsteadOfSkippingForever() throws Exception {
        Fixture f=new Fixture();f.queue(0);
        long promoted=30*60*1000L+1;
        when(f.api.getMediaServerConfig(f.node)).thenReturn(null,healthy());
        f.manager.execute(promoted);assertFalse(f.node.isStatus());
        f.manager.execute(promoted+60000);verify(f.api,times(1)).getMediaServerConfig(f.node);
        f.manager.execute(promoted+30*60*1000L);assertTrue(f.node.isStatus());
        verify(f.api,times(2)).getMediaServerConfig(f.node);
    }
    @Test public void failedApiAuthenticationNeverMarksTheMediaServerOnline() throws Exception {
        Fixture f=new Fixture();f.queue(0);
        JSONObject bad=healthy();bad.put("code",-100);
        when(f.api.getMediaServerConfig(f.node)).thenReturn(bad);
        f.manager.execute(10000);assertFalse(f.node.isStatus());
        verify(f.events,never()).mediaServerOnlineEventPublish(any());
    }
    @Test public void heartbeatExpiryAndAHealthyRetryRestoreOnlineState() throws Exception {
        Fixture f=new Fixture();f.queue(0);
        when(f.api.getMediaServerConfig(f.node)).thenReturn(healthy());
        f.manager.execute(10000);
        ArgumentCaptor<Runnable> timeout=ArgumentCaptor.forClass(Runnable.class);
        verify(f.tasks).startDelay(anyString(),timeout.capture(),eq(20000));
        timeout.getValue().run();assertFalse(f.node.isStatus());
        verify(f.events).mediaServerOfflineEventPublish(f.node);
        f.manager.execute(System.currentTimeMillis()+10000);assertTrue(f.node.isStatus());
        verify(f.events,times(2)).mediaServerOnlineEventPublish(f.node);
    }
    @Test public void otherSavedNodePortsAreNotReplacedByTheDefaultPort() throws Exception {
        Fixture f=new Fixture();f.node.setId("other");f.node.setHttpPort(6080);
        MediaServerChangeEvent change=new MediaServerChangeEvent(this);change.setMediaServerItemList(Collections.singletonList(f.node));
        f.manager.onApplicationEvent(change);
        assertEquals(6080,f.node.getHttpPort());
    }
    @Test public void configuredModeIgnoresForeignRegistrationEventsWithoutProbingThem() throws Exception {
        Fixture f=new Fixture();f.node.setId("other");
        when(f.config.isLoadConfiguredOnly()).thenReturn(true);
        MediaServerChangeEvent change=new MediaServerChangeEvent(this);
        change.setMediaServerItemList(Collections.singletonList(f.node));
        f.manager.onApplicationEvent(change);
        assertTrue(f.primary().isEmpty());assertTrue(f.secondary().isEmpty());
        verifyNoInteractions(f.api,f.service,f.events,f.tasks);
    }
    @Test public void failureLogsDoNotIncludeTheManagementSecret() throws Exception {
        Fixture f=new Fixture();f.queue(0);f.node.setSecret("fixture-secret-do-not-log");
        Logger logger=(Logger)LoggerFactory.getLogger(ZLMMediaServerStatusManager.class);
        ListAppender<ILoggingEvent> capture=new ListAppender<>();capture.start();logger.addAppender(capture);
        try {
            f.manager.execute(10000);
            assertFalse(capture.list.isEmpty());
            for(ILoggingEvent event:capture.list)assertFalse(event.getFormattedMessage().contains("fixture-secret-do-not-log"));
        } finally {logger.detachAppender(capture);capture.stop();}
    }
    @Test public void scheduledRecoveryUsesTenSecondsByDefault() throws Exception {
        Scheduled schedule=ZLMMediaServerStatusManager.class.getMethod("execute").getAnnotation(Scheduled.class);
        assertEquals("${media.offline-retry-interval-ms:10000}",schedule.fixedDelayString());
    }
    private static JSONObject healthy() {
        JSONObject result=new JSONObject();result.put("code",0);
        JSONArray data=new JSONArray();data.add(new JSONObject());result.put("data",data);return result;
    }
    private static class Fixture {
        final ZLMMediaServerStatusManager manager=new ZLMMediaServerStatusManager();
        final MediaServer node=new MediaServer();
        final ZLMRESTfulUtils api=mock(ZLMRESTfulUtils.class);
        final IMediaServerService service=mock(IMediaServerService.class);
        final EventPublisher events=mock(EventPublisher.class);
        final DynamicTask tasks=mock(DynamicTask.class);
        final MediaConfig config=mock(MediaConfig.class);
        Fixture() throws Exception {
            node.setId("local");node.setIp("127.0.0.1");node.setType("zlm");node.setHttpPort(8090);node.setAutoConfig(false);
            node.setHttpSSlPort(4443);node.setRtmpPort(1935);node.setRtmpSSlPort(1936);node.setRtspPort(554);node.setRtspSSLPort(5544);
            node.setRtpProxyPort(11000);node.setFlvSSLPort(4443);node.setWsFlvSSLPort(4443);node.setHookAliveInterval(10F);
            when(config.getId()).thenReturn("local");when(config.getHttpPort()).thenReturn(8090);
            set(manager,"zlmresTfulUtils",api);set(manager,"mediaServerService",service);set(manager,"mediaConfig",config);
            set(manager,"eventPublisher",events);set(manager,"dynamicTask",tasks);
        }
        void queue(long time) throws Exception {primary().put(node.getId(),node);times().put(node.getId(),time);}
        Map<Object,MediaServer> primary() throws Exception {return map("offlineZlmPrimaryMap");}
        Map<Object,MediaServer> secondary() throws Exception {return map("offlineZlmsecondaryMap");}
        Map<Object,Long> times() throws Exception {return map("offlineZlmTimeMap");}
        @SuppressWarnings("unchecked") <T> Map<Object,T> map(String name) throws Exception {
            java.lang.reflect.Field field=manager.getClass().getDeclaredField(name);field.setAccessible(true);return (Map<Object,T>)field.get(manager);
        }
    }
    private static void set(Object target,String name,Object value) throws Exception {
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
