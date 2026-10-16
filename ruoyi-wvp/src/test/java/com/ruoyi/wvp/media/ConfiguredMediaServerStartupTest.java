package com.ruoyi.wvp.media;

import com.ruoyi.wvp.conf.MediaConfig;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.event.mediaServer.MediaServerChangeEvent;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ConfiguredMediaServerStartupTest {
    @Test public void configuredNodeIsRegisteredOnlyInCacheWithoutReplacingTheForeignGlobalDefault() throws Exception {
        Fixture f=new Fixture();
        f.runner.run();
        verify(f.service,never()).add(any());
        verify(f.service).update(f.configured);
        assertTrue(f.configured.isDefaultServer());
        f.assertOwnEvent(); f.assertNoForeignMutation();
    }
    @Test public void restartedOwnNodeIsUpdatedWithoutDeletingOrAddingData() throws Exception {
        Fixture f=new Fixture();
        MediaServer stored=new MediaServer();stored.setId("local");stored.setDefaultServer(false);
        when(f.service.getOneFromDatabase("local")).thenReturn(stored);
        f.runner.run(); f.runner.run();
        verify(f.service,never()).add(any());
        verify(f.service,times(2)).update(f.configured);
        assertTrue(f.configured.isDefaultServer());
        f.assertNoForeignMutation();
    }
    @Test public void persistedOwnDefaultFlagIsPreservedWhenAlreadyOwned() throws Exception {
        Fixture f=new Fixture();
        MediaServer stored=new MediaServer();stored.setId("local");stored.setDefaultServer(true);
        when(f.service.getOneFromDatabase("local")).thenReturn(stored);
        f.runner.run();
        assertTrue(f.configured.isDefaultServer());
        verify(f.service,never()).add(any()); f.assertNoForeignMutation();
    }
    private static class Fixture {
        final MediaServerConfig runner=new MediaServerConfig();
        final IMediaServerService service=mock(IMediaServerService.class);
        final ApplicationEventPublisher publisher=mock(ApplicationEventPublisher.class);
        final MediaServer configured=new MediaServer();
        Fixture() throws Exception {
            configured.setId("local");configured.setDefaultServer(true);
            MediaConfig config=mock(MediaConfig.class);
            when(config.isLoadConfiguredOnly()).thenReturn(true);
            when(config.getMediaSerItem()).thenReturn(configured);
            set(runner,"mediaConfig",config);set(runner,"mediaServerService",service);set(runner,"applicationEventPublisher",publisher);
        }
        void assertOwnEvent() {
            ArgumentCaptor<MediaServerChangeEvent> event=ArgumentCaptor.forClass(MediaServerChangeEvent.class);
            verify(publisher).publishEvent(event.capture());
            assertEquals(1,event.getValue().getMediaServerItemList().size());
            assertEquals("local",event.getValue().getMediaServerItemList().get(0).getId());
        }
        void assertNoForeignMutation() {
            verify(service,never()).getDefaultMediaServer();verify(service,never()).delete(any());
            verify(service,never()).syncCatchFromDatabase();verify(service,never()).getAllFromDatabase();
            verify(service,never()).getOneFromDatabase(anyString());
        }
    }
    private static void set(Object target,String name,Object value) throws Exception {
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
