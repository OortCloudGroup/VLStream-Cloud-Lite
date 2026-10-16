package com.ruoyi.wvp.media.service.impl;

import com.ruoyi.wvp.common.VideoManagerConstants;
import com.ruoyi.wvp.conf.MediaConfig;
import com.ruoyi.wvp.conf.UserSetting;
import com.ruoyi.wvp.mapper.MediaServerMapper;
import com.ruoyi.wvp.gb28181.session.SSRCFactory;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.common.exception.ControllerException;
import org.junit.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ConfiguredMediaServerTest {
    @Test public void configuredNodeIsSelectedInsteadOfAnotherDeploymentsGlobalDefault() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer local = new MediaServer(); local.setId("local"); local.setStatus(true);
        when(f.hash.get(f.registryKey(), "local")).thenReturn(local);
        assertSame(local, f.service.getDefaultMediaServer());
        verify(f.mapper, never()).queryDefault();
        verify(f.mapper, never()).getMediaServerById(anyString());
        verify(f.hash, never()).get(VideoManagerConstants.MEDIA_SERVER_PREFIX + "000000", "local");
    }
    @Test public void absentLocalCacheReturnsTheConfigurationAsUnavailableWithoutDatabaseReads() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer local = new MediaServer(); local.setId("local"); local.setStatus(false);
        when(f.config.getMediaSerItem()).thenReturn(local);
        assertSame(local, f.service.getDefaultMediaServer());
        assertFalse(f.service.getDefaultMediaServer().isStatus());
        verifyNoInteractions(f.mapper);
    }
    @Test public void missingConfiguredNodeDoesNotUseAForeignOnlineNode() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer foreign = new MediaServer(); foreign.setId("polaris"); foreign.setStatus(true);
        when(f.mapper.queryDefault()).thenReturn(foreign);
        assertNull(f.service.getDefaultMediaServer());
        verify(f.mapper, never()).queryDefault();
    }
    @Test public void configuredRegistrationUpdatesOnlyTheOwnedCacheWithoutSharedDatabaseWrites() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer local = new MediaServer();local.setId("local");local.setStatus(false);
        f.service.update(local);
        verify(f.hash).put(f.registryKey(),"local",local);
        verifyNoInteractions(f.mapper);
        assertNotNull(local.getCreateTime());
    }
    @Test public void startupClearsOnlyItsIsolatedMediaOnlineSet() throws Exception {
        Fixture f = new Fixture(true);
        f.service.clearMediaServerForOnline();
        verify(f.redis).delete(VideoManagerConstants.ONLINE_MEDIA_SERVERS_PREFIX + "000000:configured:local");
        verify(f.redis, never()).delete(VideoManagerConstants.ONLINE_MEDIA_SERVERS_PREFIX + "000000");
    }
    @Test public void savingAnAbsentConfiguredNodeAddsOnlyToItsOwnedCache() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer local = new MediaServer(); local.setId("local");
        assertNull(f.service.getOneFromDatabase("local"));
        f.service.add(local);
        verify(f.hash).put(f.registryKey(), "local", local);
        verifyNoInteractions(f.mapper);
    }
    @Test public void configuredModeRejectsForeignAddAndUpdateWithoutTouchingSharedState() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer foreign = new MediaServer(); foreign.setId("other");
        try { f.service.add(foreign); fail("foreign addition must be refused"); }
        catch (ControllerException expected) { }
        try { f.service.update(foreign); fail("foreign update must be refused"); }
        catch (ControllerException expected) { }
        assertNull(f.service.getOne("other"));
        verifyNoInteractions(f.mapper, f.redis, f.hash);
    }
    @Test public void configuredNodeCannotBeDeletedThroughTheManagementApi() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer local = new MediaServer(); local.setId("local");
        try { f.service.delete(local); fail("configured deletion must be refused"); }
        catch (ControllerException expected) { }
        verifyNoInteractions(f.mapper, f.redis, f.hash);
    }
    @Test public void configuredSynchronizationDoesNotReadOrImportAnotherInstancesDatabaseNodes() throws Exception {
        Fixture f = new Fixture(true);
        MediaServer local = new MediaServer(); local.setId("local");
        when(f.config.getMediaSerItem()).thenReturn(local);
        f.service.syncCatchFromDatabase();
        assertEquals(java.util.Collections.singletonList(local), f.service.getAllFromDatabase());
        verifyNoInteractions(f.mapper);
    }
    @Test public void legacyMultinodeModeKeepsItsExistingDefaultAndCacheNamespace() throws Exception {
        Fixture f = new Fixture(false);
        MediaServer global = new MediaServer(); global.setId("polaris");
        when(f.mapper.queryDefault()).thenReturn(global);
        assertSame(global, f.service.getDefaultMediaServer());
        f.service.getOne("polaris");
        verify(f.hash).get(VideoManagerConstants.MEDIA_SERVER_PREFIX + "000000", "polaris");
    }
    private static class Fixture {
        final MediaServerServiceImpl service = new MediaServerServiceImpl();
        final MediaServerMapper mapper = mock(MediaServerMapper.class);
        final MediaConfig config = mock(MediaConfig.class);
        final RedisTemplate<Object,Object> redis = mock(RedisTemplate.class);
        final HashOperations<Object,Object,Object> hash = mock(HashOperations.class);
        Fixture(boolean isolated) throws Exception {
            when(config.isLoadConfiguredOnly()).thenReturn(isolated);
            when(config.getId()).thenReturn("local");
            when(redis.opsForHash()).thenReturn(hash);
            set(service,"mediaConfig",config); set(service,"userSetting",new UserSetting());
            set(service,"mediaServerMapper",mapper); set(service,"redisTemplate",redis);
            set(service,"ssrcFactory",mock(SSRCFactory.class));
        }
        String registryKey() { return VideoManagerConstants.MEDIA_SERVER_PREFIX + "000000:configured:local"; }
    }
    private static void set(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target,value);
    }
}
