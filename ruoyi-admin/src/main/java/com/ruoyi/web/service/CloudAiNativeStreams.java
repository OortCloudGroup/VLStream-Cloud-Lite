package com.ruoyi.web.service;

import com.aizuda.zlm4j.structure.MK_INI;
import com.aizuda.zlm4j.structure.MK_MEDIA;
import com.ruoyi.common.config.ZlmApiProperties;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.dahua.callback.FRealDataCallback;
import com.ruoyi.dahua.domain.DahuaDevice;
import com.ruoyi.dahua.lib.NetSDKLib;
import com.ruoyi.dahua.service.impl.DahuaDeviceServiceImpl;
import com.ruoyi.isup.common.HandleStream;
import com.ruoyi.isup.config.IsupConfig;
import com.ruoyi.isup.service.cmsService.CMS;
import com.ruoyi.isup.service.cmsService.HCISUPCMS;
import com.ruoyi.isup.service.smsService.SMS;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static com.ruoyi.common.service.ZlmService.ZLM_API;

/** Dedicated SDK sessions: stopping a cloud task never stops a device-management preview. */
@Service
public class CloudAiNativeStreams {
    private final IsupConfig isup;
    private final ObjectProvider<DahuaDeviceServiceImpl> dahua;
    private final ZlmApiProperties ports;
    private final Set<Stream> pendingClose = ConcurrentHashMap.newKeySet();
    @Value("${media.ip}") private String mediaHost;
    @Value("${media.http-port}") private int mediaHttpPort;
    @Value("${media.secret}") private String mediaSecret;
    public CloudAiNativeStreams(IsupConfig isup, ObjectProvider<DahuaDeviceServiceImpl> dahua, ZlmApiProperties ports) {
        this.isup = isup; this.dahua = dahua; this.ports = ports;
    }
    public static class Stream implements AutoCloseable {
        public final String url;
        private final Runnable[] cleanup;
        private int next;
        public Stream(String url, Runnable... cleanup) { this.url = url; this.cleanup = cleanup; }
        public synchronized void close() {
            while (next < cleanup.length) { cleanup[next].run(); next++; }
        }
    }
    private void closeOrRetry(Stream stream) {
        try { stream.close(); pendingClose.remove(stream); }
        catch (RuntimeException failure) { pendingClose.add(stream); }
    }
    @Scheduled(fixedDelay=15000)
    public void retryCleanup() { for (Stream stream : pendingClose) closeOrRetry(stream); }
    @PreDestroy public void shutdown() { retryCleanup(); }
    private void requireCleanupCapacity() {
        if (pendingClose.size() >= 64) throw new ServiceException("设备连接正在回收，请稍后重试");
    }
    public List<Map<String,Object>> isupChannels(int loginId) {
        if(CMS.hCEhomeCMS==null)throw new ServiceException("ISUP 接入服务未启动");
        HCISUPCMS.NET_EHOME_DEVICE_INFO info=new HCISUPCMS.NET_EHOME_DEVICE_INFO();info.dwSize=info.size();info.write();
        HCISUPCMS.NET_EHOME_CONFIG query=new HCISUPCMS.NET_EHOME_CONFIG();query.pOutBuf=info.getPointer();query.dwOutSize=info.size();query.write();
        if(!CMS.hCEhomeCMS.NET_ECMS_GetDevConfig(loginId,1,query.getPointer(),query.size()))throw new ServiceException("ISUP 设备通道查询失败");
        info.read();int first=info.dwStartChannel,count=info.dwChannelAmount;
        if(first<1||count<0||count>1024||first+count>65536)throw new ServiceException("ISUP 设备返回无效通道范围");
        List<Map<String,Object>> result=new ArrayList<>();
        for(int i=first;i<first+count;i++)result.add(CloudAiProtocolSources.choice((long)i,"通道 "+i,"PLATFORM"));
        return result;
    }
    public Stream isup(int loginId, int channel) {
        requireCleanupCapacity();
        if (CMS.hCEhomeCMS == null || SMS.hcISUPSMS == null || SMS.listenHandle < 0)
            throw new ServiceException("ISUP 接入或取流服务未启动");
        HCISUPCMS.NET_EHOME_PREVIEWINFO_IN_V11 input = new HCISUPCMS.NET_EHOME_PREVIEWINFO_IN_V11();
        input.iChannel = channel; input.dwStreamType = 0; input.dwLinkMode = 0;
        com.ruoyi.isup.ehome.EhomeNative.copy(isup.getIp(), input.struStreamSever.szIP);
        input.struStreamSever.wPort = (short) isup.getSmsServerPort(); input.write();
        HCISUPCMS.NET_EHOME_PREVIEWINFO_OUT output = new HCISUPCMS.NET_EHOME_PREVIEWINFO_OUT(); output.write();
        if (!CMS.hCEhomeCMS.NET_ECMS_StartGetRealStreamV11(loginId, input, output))
            throw new ServiceException("ISUP 设备拒绝开流，请检查注册状态与通道");
        output.read(); final int session = output.lSessionID;
        String stream = "CLOUD_AI_" + UUID.randomUUID().toString().replace("-", "");
        CompletableFuture<String> ready = new CompletableFuture<>();
        String sign = org.springframework.util.DigestUtils.md5DigestAsHex(mediaSecret.getBytes(StandardCharsets.UTF_8));
        AtomicReference<HandleStream> pipe = new AtomicReference<>();
        Stream owned = new Stream("http://" + mediaHost + ":" + mediaHttpPort + "/haikang/" + stream + ".live.flv", () -> {
            if (!CMS.hCEhomeCMS.NET_ECMS_StopGetRealStream(loginId, session))
                throw new ServiceException("ISUP 停流尚未确认，将继续回收");
        }, () -> {
            Integer handle = SMS.SessionIDAndPreviewHandleMap.get(session);
            if (handle != null && !SMS.hcISUPSMS.NET_ESTREAM_StopPreview(handle))
                throw new ServiceException("ISUP 媒体连接尚未释放，将继续回收");
            SMS.SessionIDAndPreviewHandleMap.remove(session);
            if (handle != null) SMS.PreviewHandSAndSessionIDandMap.remove(handle);
        }, () -> {
            HandleStream current = pipe.get();
            if (current != null) { current.stopProcessing(); SMS.concurrentMap.remove(session, current); }
        });
        try {
            pipe.set(new HandleStream("rtmp://" + mediaHost + ":1935/haikang/" + stream + "?sign=" + sign, ready));
            SMS.concurrentMap.put(session, pipe.get());
            HCISUPCMS.NET_EHOME_PUSHSTREAM_IN push = new HCISUPCMS.NET_EHOME_PUSHSTREAM_IN();
            push.dwSize = push.size(); push.lSessionID = session; push.write();
            HCISUPCMS.NET_EHOME_PUSHSTREAM_OUT reply = new HCISUPCMS.NET_EHOME_PUSHSTREAM_OUT();
            reply.dwSize = reply.size(); reply.write();
            if (!CMS.hCEhomeCMS.NET_ECMS_StartPushRealStream(loginId, push, reply)
                    || !"true".equals(ready.get(20, TimeUnit.SECONDS))) throw new ServiceException("ISUP 视频流建立失败");
            return owned;
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            closeOrRetry(owned);
            throw new ServiceException("ISUP 开流超时或媒体服务不可用");
        }
    }
    public Stream dahua(Long rowId) {
        requireCleanupCapacity();
        DahuaDeviceServiceImpl service = dahua.getIfAvailable();
        if (service == null) throw new ServiceException("大华 SDK 接入服务未启用");
        DahuaDevice device = service.getLoginHandle(rowId);
        if (device.getM_hLoginHandle() == null || device.getM_hLoginHandle().longValue() == 0)
            throw new ServiceException("大华设备登录失败，请检查设备凭据与网络");
        NetSDKLib.NET_IN_REALPLAY_BY_DATA_TYPE input = new NetSDKLib.NET_IN_REALPLAY_BY_DATA_TYPE();
        input.nChannelID = device.getChannel(); input.rType = 0; input.emDataType = 4;
        NetSDKLib.LLong handle = DahuaDeviceServiceImpl.netsdk.CLIENT_RealPlayByDataType(device.getM_hLoginHandle(), input, new NetSDKLib.NET_OUT_REALPLAY_BY_DATA_TYPE(), 3000);
        if (handle == null || handle.longValue() == 0) throw new ServiceException("大华设备拒绝开流");
        String stream = "cloud_ai_" + UUID.randomUUID().toString().replace("-", "");
        AtomicReference<MK_MEDIA> media = new AtomicReference<>();
        AtomicReference<FRealDataCallback> callback = new AtomicReference<>();
        Stream owned = new Stream("rtsp://" + mediaHost + ":" + ports.getDhRtspPort() + "/dh/" + stream, () -> {
            if (!DahuaDeviceServiceImpl.netsdk.CLIENT_StopRealPlayEx(handle)) throw new ServiceException("大华停流尚未确认，将继续回收");
        }, () -> {
            if (callback.get() != null) callback.get().shutdown();
        }, () -> {
            if (media.get() != null) ZLM_API.mk_media_release(media.get());
        });
        try {
            MK_INI options = ZLM_API.mk_ini_create();
            try {
                ZLM_API.mk_ini_set_option(options, "enable_rtsp", "1");
                media.set(ZLM_API.mk_media_create2("__defaultVhost__", "dh", stream, 0, options));
            } finally { ZLM_API.mk_ini_release(options); }
            if (media.get() == null) throw new ServiceException("大华媒体通道建立失败");
            ZLM_API.mk_media_init_video(media.get(), 0, 1, 1, 25.0f, 2500); ZLM_API.mk_media_init_complete(media.get());
            callback.set(new FRealDataCallback(media.get(), 25.0));
            if (!DahuaDeviceServiceImpl.netsdk.CLIENT_SetRealDataCallBackEx(handle, callback.get(), null, 31))
                throw new ServiceException("大华媒体回调建立失败");
            return owned;
        } catch (RuntimeException failure) {
            closeOrRetry(owned); throw new ServiceException("大华媒体通道建立失败，请检查 SDK 与媒体服务");
        }
    }
}
