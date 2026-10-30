package com.ruoyi.web.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.isup.ehome.*;
import com.ruoyi.isup.mapper.EhomeDeviceMapper;
import com.ruoyi.video.domain.*;
import com.ruoyi.video.service.VideoDeviceScopedReader;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.gb28181.bean.*;
import com.ruoyi.wvp.gb28181.service.*;
import com.ruoyi.wvp.streamPush.service.*;
import com.ruoyi.wvp.streamPush.bean.StreamPush;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/** Protocol-owned identities and channels. Browser requests never supply media addresses or SDK handles. */
@Service
public class CloudAiProtocolSources {
    private final JdbcTemplate jdbc;
    private final VideoDeviceScopedReader reader;
    private final VlStreamDeviceTenantService tenants;
    private final IDeviceService gbDevices;
    private final IDeviceChannelService gbChannels;
    private final IPlayService gbPlay;
    private final EhomeDeviceMapper ehomeDevices;
    private final EhomeSdk ehomeSdk;
    private final EhomePreviews ehomePreviews;
    private final CloudAiNativeStreams nativeStreams;
    private final CloudAiMediaSessions leases;
    private final IStreamPushService pushes;
    private final IStreamPushPlayService pushPlay;
    @Value("${vlstream.device.default-tenant-id:000000}") private String protocolDefaultTenant;
    private static final Map<String,String> TABLES = new LinkedHashMap<>();
    static { TABLES.put("ISUP","isup_device"); TABLES.put("EHOME","ehome_device"); TABLES.put("GB28181","wvp_device"); TABLES.put("DAHUA","dahua_device"); }
    public CloudAiProtocolSources(JdbcTemplate jdbc, VideoDeviceScopedReader reader, VlStreamDeviceTenantService tenants,
            IDeviceService gbDevices, IDeviceChannelService gbChannels, IPlayService gbPlay, EhomeDeviceMapper ehomeDevices,
            EhomeSdk ehomeSdk, EhomePreviews ehomePreviews, CloudAiNativeStreams nativeStreams, CloudAiMediaSessions leases,
            IStreamPushService pushes, IStreamPushPlayService pushPlay) {
        this.jdbc=jdbc; this.reader=reader; this.tenants=tenants; this.gbDevices=gbDevices; this.gbChannels=gbChannels; this.gbPlay=gbPlay;
        this.ehomeDevices=ehomeDevices; this.ehomeSdk=ehomeSdk; this.ehomePreviews=ehomePreviews; this.nativeStreams=nativeStreams;
        this.leases=leases; this.pushes=pushes; this.pushPlay=pushPlay;
    }
    public boolean supports(String protocol) { return TABLES.containsKey(protocol); }
    public Map<String,Object> devices(String protocol, int page, int size, String search) {
        List<VideoDeviceSource> rows=visible(protocol);
        if(search!=null&&!search.trim().isEmpty())rows=rows.stream().filter(row->
            (Objects.toString(row.getDeviceName(),"")+" "+Objects.toString(row.getDeviceId(),"")).toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        int limit=Math.max(1,Math.min(100,size)); long offset=(long)(Math.max(1,page)-1)*limit; int from=(int)Math.min(rows.size(),offset);
        List<Map<String,Object>> result=new ArrayList<>();
        for(VideoDeviceSource source:rows.subList(from,Math.min(rows.size(),from+limit))) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",source.getRowId());row.put("deviceName",source.getDeviceName());
            row.put("deviceId",source.getDeviceId());row.put("ip",source.getIpAddress());row.put("state",source.getState());result.add(row);
        }
        Map<String,Object> resultPage=new LinkedHashMap<>();resultPage.put("rows",result);resultPage.put("total",rows.size());return resultPage;
    }
    private List<VideoDeviceSource> visible(String protocol) {
        String tenant=tenants.currentTenantId();
        if(!Arrays.asList("VLSTREAM","ONVIF","RTSP").contains(protocol)&&!tenant.equals(tenants.defaultTenant()))return Collections.emptyList();
        VideoDeviceQuery query=new VideoDeviceQuery();query.setProtocol(protocol);query.setTenantId(tenant);
        query.setDefaultTenantId(Arrays.asList("ONVIF","RTSP").contains(protocol)?protocolDefaultTenant:tenants.defaultTenant());
        switch(protocol) {
            case "VLSTREAM":return reader.vlstream(query); case "ONVIF":return reader.onvif(query); case "RTSP":return reader.rtsp(query);
            case "CUSTOM":return reader.custom(query); case "ISUP":return reader.isup(query); case "EHOME":return reader.ehome(query);
            case "GB28181":return reader.gb(query); case "DAHUA":return reader.dahua(query);
            default:throw new ServiceException("未知设备目录",400);
        }
    }
    public Long authorize(String protocol, Long rowId) {
        if(visible(protocol).stream().noneMatch(row->String.valueOf(rowId).equals(row.getRowId())))throw new ServiceException("设备不存在或无权访问",403);
        return department(row(protocol,rowId));
    }
    private Map<String,Object> row(String protocol, Long rowId) {
        String table=TABLES.get(protocol);if(table==null)throw new ServiceException("未知设备目录",400);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM "+table+" WHERE id=?",rowId);
        if(rows.size()!=1)throw new ServiceException("已授权的设备已移除",403);return rows.get(0);
    }
    private static Long department(Map<String,Object> row) { return row.get("dept_id")==null?null:((Number)row.get("dept_id")).longValue(); }
    public List<Map<String,Object>> choices(String protocol,Long rowId) {
        authorize(protocol,rowId);Map<String,Object> row=row(protocol,rowId);List<Map<String,Object>> result=new ArrayList<>();
        if(("ISUP".equals(protocol)||"DAHUA".equals(protocol))&&Arrays.asList("2","3").contains(Objects.toString(row.get("play_type"),""))) {
            String type="2".equals(row.get("play_type"))?"PLATFORM":CloudAiSourceService.sourceType(null,Objects.toString(row.get("easy_NTS_url"),""));
            result.add(choice(null,"已配置播放来源",type));
        } else if("GB28181".equals(protocol)) {
            for(DeviceChannel channel:gbChannels.queryChaneListByDeviceDbId(Math.toIntExact(rowId)))
                if(!Integer.valueOf(1).equals(channel.getParental()))result.add(choice((long)channel.getId(),channel.getName(),"PLATFORM"));
        } else if("EHOME".equals(protocol)) {
            EhomeDevice device=ehome(rowId);if(device.getLuserId()==null||!"ON".equals(device.getStatus()))throw new ServiceException("设备离线，暂时无法读取通道");
            for(EhomeSdk.Channel channel:ehomeSdk.channels(device.getLuserId()))result.add(choice((long)channel.getId(),channel.getName(),"PLATFORM"));
        } else if("ISUP".equals(protocol)) {
            if(row.get("luser_id")==null)throw new ServiceException("ISUP 设备未注册");
            for(Map<String,Object> channel:nativeStreams.isupChannels(((Number)row.get("luser_id")).intValue()))result.add(channel);
        } else {
            String url=Objects.toString(row.get("url"),"");String type=CloudAiSourceService.sourceType(null,url);
            result.add(choice(null,"已保存通道",type==null?"PLATFORM":type));
        }
        return result;
    }
    static Map<String,Object> choice(Long id,String name,String type) {
        Map<String,Object> item=new LinkedHashMap<>();item.put("streamId",id==null?null:String.valueOf(id));item.put("name",name);
        item.put("type",type);item.put("supported",type!=null);return item;
    }
    public Map<String,Object> describe(String protocol,Long rowId,Long streamId,String tenant,Long dept) {
        if(!tenants.defaultTenant().equals(tenant))throw new ServiceException("该协议目录不属于当前租户",403);
        Map<String,Object> row=row(protocol,rowId);if(!Objects.equals(dept,department(row)))throw new ServiceException("设备访问范围已变化，请重新授权",403);
        Map<String,Object> result=new LinkedHashMap<>();result.put("protocol",protocol);result.put("deviceRowId",String.valueOf(rowId));
        result.put("streamId",streamId==null?null:String.valueOf(streamId));result.put("deviceId",protocol+":"+row.get("device_id"));
        result.put("name",row.get("name"));result.put("type","PLATFORM");result.put("provider",protocol);
        result.put("address",row.get("address_map"));result.put("latitude",row.get("lat"));result.put("longitude",row.get("lng"));
        String mode=Objects.toString(row.get("play_type"),"1");
        if(("ISUP".equals(protocol)||"DAHUA".equals(protocol))&&"2".equals(mode)) {
            result.put("provider","PUSH");result.put("streamKey",row.get("stream_id"));
        } else if(("ISUP".equals(protocol)||"DAHUA".equals(protocol))&&"3".equals(mode)) {
            String url=Objects.toString(row.get("easy_NTS_url"),"");String type=CloudAiSourceService.sourceType(null,url);
            if(type==null)throw new ServiceException("设备的 EasyNTS 来源不是可接收的媒体流");result.put("url",url);result.put("type",type);
        } else if("GB28181".equals(protocol)) {
            DeviceChannel selected=gbChannel(rowId,streamId);result.put("channelName",selected.getName());result.put("channelId",selected.getDeviceId());
            result.put("deviceId",result.get("deviceId")+"/"+selected.getDeviceId());
        } else if("ISUP".equals(protocol)) {
            if(streamId==null||streamId<1||streamId>65535)throw new ServiceException("请选择有效设备通道");
            if(row.get("luser_id")==null)throw new ServiceException("ISUP 设备未注册");
            int loginId=((Number)row.get("luser_id")).intValue();
            boolean channelExists=nativeStreams.isupChannels(loginId).stream()
                .anyMatch(channel->String.valueOf(streamId).equals(channel.get("streamId")));
            if(!channelExists)throw new ServiceException("ISUP 设备未返回该通道，请刷新通道列表",403);
        } else if("EHOME".equals(protocol)) {
            if(streamId==null||streamId<1||streamId>65535)throw new ServiceException("请选择有效设备通道");
        } else if("DAHUA".equals(protocol)) {
            if("2".equals(mode)){result.put("provider","PUSH");result.put("streamKey",row.get("stream_id"));}
            else {
                String url=Objects.toString(row.get("3".equals(mode)?"easy_NTS_url":"url"),"");
                String type=CloudAiSourceService.sourceType(null,url);if(type!=null){result.put("url",url);result.put("type",type);}
            }
        }
        return result;
    }
    private DeviceChannel gbChannel(Long rowId,Long streamId) {
        if(streamId==null)throw new ServiceException("请选择国标通道");
        return gbChannels.queryChaneListByDeviceDbId(Math.toIntExact(rowId)).stream().filter(channel->channel.getId()==streamId).findFirst()
            .orElseThrow(()->new ServiceException("国标通道已移除或不属于该设备",403));
    }
    private EhomeDevice ehome(Long id) {
        EhomeDevice query=new EhomeDevice();query.setId(id);
        return ehomeDevices.selectDevices(query).stream().filter(device->id.equals(device.getId())).findFirst().orElseThrow(()->new ServiceException("EHome 设备已移除",403));
    }
    public void acquire(Map<String,Object> source,String grant,String run) {
        if(!"PLATFORM".equals(source.get("type")))return;
        String url=leases.acquire(grant,run,()->open(source));
        String type=CloudAiSourceService.sourceType(null,url);if(type==null)throw new ServiceException("平台未返回 GPU 可接收的媒体流");
        source.put("url",url);source.put("type",type);
    }
    public void release(String grant,String run) { leases.release(grant,run); }
    private CloudAiMediaSessions.Session open(Map<String,Object> source) {
        String provider=String.valueOf(source.get("provider"));Long rowId=Long.valueOf(String.valueOf(source.get("deviceRowId")));
        Long streamId=source.get("streamId")==null?null:Long.valueOf(String.valueOf(source.get("streamId")));
        if("EHOME".equals(provider)) {
            Map<String,Object> preview=ehomePreviews.start(ehome(rowId),Math.toIntExact(streamId),0,Long.MIN_VALUE);
            String id=String.valueOf(preview.get("sessionId"));return new CloudAiMediaSessions.Session(String.valueOf(preview.get("url")),()->ehomePreviews.touch(id,Long.MIN_VALUE),()->ehomePreviews.stop(id,Long.MIN_VALUE));
        }
        if("ISUP".equals(provider)||"DAHUA".equals(provider)) {
            CloudAiNativeStreams.Stream stream="ISUP".equals(provider)?nativeStreams.isup(((Number)row(provider,rowId).get("luser_id")).intValue(),Math.toIntExact(streamId)):nativeStreams.dahua(rowId);
            return new CloudAiMediaSessions.Session(stream.url,()->{},stream::close);
        }
        CompletableFuture<StreamInfo> ready=new CompletableFuture<>();
        if("GB28181".equals(provider)) {
            Map<String,Object> row=row(provider,rowId);Device device=gbDevices.getDeviceByDeviceId(String.valueOf(row.get("device_id")));
            DeviceChannel channel=gbChannel(rowId,streamId);
            gbPlay.play(gbPlay.getNewMediaServerItem(device),device.getDeviceId(),channel.getDeviceId(),null,
                (code,message,stream)->{if(code==0&&stream!=null)ready.complete(stream);else ready.completeExceptionally(new ServiceException("国标开流失败"));});
        } else if("PUSH".equals(provider)) {
            StreamPush stream=pushes.selectByStream(Objects.toString(source.get("streamKey"),""));
            if(stream==null)throw new ServiceException("设备配置的推流尚未上线");
            pushPlay.start(stream.getId(),(code,message,info)->{if(code==0&&info!=null)ready.complete(info);else ready.completeExceptionally(new ServiceException("推流不可用"));},null,null);
        } else throw new ServiceException("该设备尚未提供媒体来源");
        try {
            StreamInfo stream=ready.get(25,TimeUnit.SECONDS);
            // GB/push streams are shared with playback/recording. Closing the GPU reader uses the existing no-reader lifecycle.
            return new CloudAiMediaSessions.Session(stream.getRtsp().getUrl(),()->{},()->{});
        } catch(Exception failure) { if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw new ServiceException("平台开流失败或等待媒体超时"); }
    }
}
