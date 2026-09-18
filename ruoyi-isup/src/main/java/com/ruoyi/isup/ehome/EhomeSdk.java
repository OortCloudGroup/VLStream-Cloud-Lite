package com.ruoyi.isup.ehome;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.isup.service.cmsService.CMS;
import com.ruoyi.isup.service.cmsService.HCISUPCMS;
import com.ruoyi.isup.service.smsService.SMS;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** SDK adapter. Device info command and native layouts follow ruoyi-qs-nvr (MIT). */
@Component
@lombok.extern.slf4j.Slf4j
public class EhomeSdk {
    private final EhomeConfig config;
    private final EhomeRuntime runtime;
    private final com.ruoyi.isup.config.IsupConfig isupConfig;
    public EhomeSdk(EhomeConfig config, EhomeRuntime runtime, com.ruoyi.isup.config.IsupConfig isupConfig) {
        this.config = config; this.runtime = runtime; this.isupConfig = isupConfig;
    }

    public boolean ready() { return runtime.ready(); }
    public boolean streamReady() { return runtime.streamReady(); }
    public String publicHost() { return config.getPublicHost(); }
    public int registrationPort() { return isupConfig.getCmsServerPort(); }
    public int streamPort() { return isupConfig.getSmsServerPort(); }
    public String statusMessage() { return runtime.message(); }

    public void requireReady() {
        if (!ready()) throw new ServiceException("EHome 接入服务未启动，请检查监听地址及海康 SDK 运行环境");
    }

    public List<Channel> channels(int loginId) {
        requireReady();
        HCISUPCMS.NET_EHOME_DEVICE_INFO info = new HCISUPCMS.NET_EHOME_DEVICE_INFO();
        info.dwSize = info.size();
        info.write();
        HCISUPCMS.NET_EHOME_CONFIG request = new HCISUPCMS.NET_EHOME_CONFIG();
        request.pOutBuf = info.getPointer();
        request.dwOutSize = info.size();
        request.write();
        if (!CMS.hCEhomeCMS.NET_ECMS_GetDevConfig(loginId, 1, request.getPointer(), request.size())) {
            throw new ServiceException("设备暂不支持通道查询或响应超时，SDK 错误码：" + CMS.hCEhomeCMS.NET_ECMS_GetLastError());
        }
        info.read();
        return channelRange(info.dwStartChannel, info.dwChannelAmount);
    }

    static List<Channel> channelRange(int start, int count) {
        if (count < 0 || count > 1024 || (count > 0 && (start < 1 || start > 65536 - count))) {
            throw new ServiceException("设备返回的通道范围无效");
        }
        List<Channel> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new Channel(start + i, "通道 " + (start + i)));
        return result;
    }

    public int open(int loginId, int channel, int streamType, String deviceIp) {
        if (!streamReady()) throw new ServiceException("EHome 取流服务未启动");
        HCISUPCMS.NET_EHOME_PREVIEWINFO_IN_V11 input = new HCISUPCMS.NET_EHOME_PREVIEWINFO_IN_V11();
        input.iChannel = channel;
        input.dwStreamType = streamType;
        input.dwLinkMode = 0;
        String address = EhomeAddressResolver.resolve(config.getPublicHost(), deviceIp);
        EhomeNative.copy(address, input.struStreamSever.szIP);
        log.info("EHome preview request login={}, channel={}, type={}, destination={}:{}", loginId, channel, streamType, address, streamPort());
        input.struStreamSever.wPort = (short) streamPort();
        input.write();
        HCISUPCMS.NET_EHOME_PREVIEWINFO_OUT output = new HCISUPCMS.NET_EHOME_PREVIEWINFO_OUT();
        output.write();
        if (!CMS.hCEhomeCMS.NET_ECMS_StartGetRealStreamV11(loginId, input, output)) {
            throw new ServiceException("设备取流失败，SDK 错误码：" + CMS.hCEhomeCMS.NET_ECMS_GetLastError());
        }
        output.read();
        log.info("EHome preview session={}", output.lSessionID);
        return output.lSessionID;
    }

    public void push(int loginId, int sessionId, String version) {
        // Official SDK: versions below 4 start immediately, 4 and above require the second command.
        if (!EhomeNative.requiresPush(version)) return;
        HCISUPCMS.NET_EHOME_PUSHSTREAM_IN input = new HCISUPCMS.NET_EHOME_PUSHSTREAM_IN();
        input.dwSize = input.size();
        input.lSessionID = sessionId;
        input.write();
        HCISUPCMS.NET_EHOME_PUSHSTREAM_OUT output = new HCISUPCMS.NET_EHOME_PUSHSTREAM_OUT();
        output.dwSize = output.size();
        output.write();
        if (!CMS.hCEhomeCMS.NET_ECMS_StartPushRealStream(loginId, input, output)) {
            throw new ServiceException("设备推流失败，SDK 错误码：" + CMS.hCEhomeCMS.NET_ECMS_GetLastError());
        }
        log.info("EHome push accepted login={}, session={}", loginId, sessionId);
    }

    public void close(int loginId, int sessionId) {
        if (CMS.hCEhomeCMS != null) CMS.hCEhomeCMS.NET_ECMS_StopGetRealStream(loginId, sessionId);
    }
    public void closeLink(int handle) {
        if (SMS.hcISUPSMS != null) SMS.hcISUPSMS.NET_ESTREAM_StopPreview(handle);
    }

    @Data @AllArgsConstructor
    public static class Channel { private int id; private String name; }
}
