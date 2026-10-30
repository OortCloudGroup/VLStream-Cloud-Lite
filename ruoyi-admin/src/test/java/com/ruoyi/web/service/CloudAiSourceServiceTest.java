package com.ruoyi.web.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.custom.service.CustomDeviceStateService;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.onvif.mapper.OnvifDeviceMapper;
import com.ruoyi.rtsp.mapper.RtspDeviceMapper;
import com.ruoyi.vlstream.config.VlStreamCameraRtcProperties;
import com.ruoyi.vlstream.mapper.*;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CloudAiSourceServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final PermissionService permissions=mock(PermissionService.class);
    private final VlStreamDeviceTenantService tenants=mock(VlStreamDeviceTenantService.class);
    private final CustomDeviceMapper custom=mock(CustomDeviceMapper.class);
    private final CloudAiProtocolSources protocols=mock(CloudAiProtocolSources.class);
    private CloudAiSourceService service;
    private Map<String,Object> grant;
    private final String token=String.join("",Collections.nCopies(43,"a"));
    @Before public void setup() {
        service=new CloudAiSourceService(jdbc,permissions,mock(ProtocolDeviceAccess.class),tenants,mock(VlStreamDeviceMapper.class),
            mock(VlStreamDeviceStreamMapper.class),mock(RtspDeviceMapper.class),mock(OnvifDeviceMapper.class),new VlStreamCameraRtcProperties(),custom,mock(CustomDeviceStateService.class),protocols);
        when(tenants.defaultTenant()).thenReturn("owner");
        grant=new LinkedHashMap<>();grant.put("id",UUID.randomUUID().toString());grant.put("tenant_id","owner");grant.put("actor_id","actor");
        grant.put("protocol","CUSTOM");grant.put("device_row_id",2L);grant.put("source_identity","CUSTOM:12");
        when(jdbc.queryForList(anyString(),anyString())).thenReturn(Collections.singletonList(grant));
        CustomDevice device=new CustomDevice();device.setId(2L);device.setDeviceCode("12");device.setDeviceName("fixture");device.setStreamUrl("rtsp://user:secret@192.0.2.1/live");
        when(custom.selectDeviceById(2L)).thenReturn(device);
    }
    @Test public void acceptsNetworkMediaAndRejectsLocalOrMalformedSources() {
        assertEquals("RTSP",CloudAiSourceService.sourceType(null,"rtsps://camera.invalid/live"));
        assertEquals("RTMP",CloudAiSourceService.sourceType(null,"rtmp://camera.invalid/live/test"));
        assertEquals("HTTP",CloudAiSourceService.sourceType(null,"https://camera.invalid/live.m3u8"));
        assertEquals("MEDIA",CloudAiSourceService.sourceType(null,"srt://camera.invalid:9000"));
        assertEquals("CAMERA_RTC",CloudAiSourceService.sourceType(null,"http://camera.invalid/videocall/TARGET-1"));
        for(String url:new String[]{"file:///etc/passwd","concat:http://camera/a|file:/a","http://camera/live\r\n","http://camera/live#bad"})assertNull(CloudAiSourceService.sourceType(null,url));
    }
    @Test public void allEightDirectoriesRequireTheirOwnPlaybackPermissionBeforeReading() {
        for(String protocol:new String[]{"VLSTREAM","RTSP","ONVIF","ISUP","EHOME","GB28181","DAHUA","CUSTOM"})
            assertThrows(ServiceException.class,()->service.devices(protocol,1,10,null));
        verifyNoInteractions(protocols);
    }
    @Test public void dahuaGrantRequiresTheExistingPlaybackPermission() {
        when(permissions.hasPermi("dahua:zlmApi:play")).thenReturn(true);
        when(protocols.devices("DAHUA",1,10,null)).thenReturn(Collections.singletonMap("total",0));
        service.devices("DAHUA",1,10,null);
        verify(permissions).hasPermi("dahua:zlmApi:play");
        verify(permissions,never()).hasPermi("dahua:device:query");
    }
    @Test public void resolvesSavedCustomSourceWithoutStartingPlatformMedia() {
        Map<String,Object> source=service.resolve(token,null);
        assertEquals("CUSTOM:12",source.get("deviceId"));assertEquals("RTSP",source.get("type"));assertEquals("actor",source.get("actorId"));
        verify(protocols,never()).acquire(anyMap(),anyString(),anyString());
        Map<String,Object> publicSource=CloudAiSourceService.publicSource(source);
        assertFalse(publicSource.containsKey("url"));assertFalse(publicSource.containsKey("socketUrl"));
    }
    @Test public void foreignTenantAndChangedDeviceIdentityInvalidateDelegation() {
        grant.put("tenant_id","other");assertThrows(ServiceException.class,()->service.resolve(token,null));
        grant.put("tenant_id","owner");grant.put("source_identity","CUSTOM:old");assertThrows(ServiceException.class,()->service.resolve(token,null));
        verify(protocols,never()).acquire(anyMap(),anyString(),anyString());
    }
    @Test public void acquiresMediaOnlyForExplicitRunAndReleaseCanCleanRevokedGrant() {
        String run=UUID.randomUUID().toString();service.resolve(token,run);
        verify(protocols).acquire(anyMap(),eq(String.valueOf(grant.get("id"))),eq(run));
        service.release(token,run);verify(protocols).release(String.valueOf(grant.get("id")),run);
        verify(jdbc).queryForList(eq("SELECT * FROM wvp_cloud_ai_source_grant WHERE token_hash=?"),anyString());
    }
    @Test public void rtspCredentialsAreEncodedAndExistingUserInfoIsPreserved() {
        String address=CloudAiSourceService.credentials("rtsp://camera.invalid/live?channel=1","a@b","s:/# p");
        assertEquals("rtsp://a%40b:s%3A%2F%23%20p@camera.invalid/live?channel=1",address);
        assertEquals("rtsp://saved:secret@camera/live",CloudAiSourceService.credentials("rtsp://saved:secret@camera/live","new","value"));
    }
}
