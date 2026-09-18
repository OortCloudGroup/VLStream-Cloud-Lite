package com.ruoyi.isup.service.cmsService;

import cn.hutool.core.util.ObjectUtil;
import com.ruoyi.common.utils.uuid.UUID;
import com.ruoyi.isup.common.SdkFunctionWrapUtil;
import com.ruoyi.isup.common.osSelect;
import com.ruoyi.isup.config.IsupConfig;
import com.ruoyi.isup.domain.IsupDevice;
import com.ruoyi.isup.service.IIsupDeviceService;
import com.ruoyi.isup.service.streamService.HCNet;
import com.ruoyi.isup.service.streamService.HCNetSDK;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Author: fengcheng
 */
@Slf4j
@Component("cmsService")
public class CMS {

    @Autowired
    private com.ruoyi.isup.ehome.EhomeRegistration ehomeRegistration;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.ruoyi.isup.ehome.EhomePreviews> ehomePreviews;

    public static HCISUPCMS hCEhomeCMS = null;

    /**
     * CMS监听句柄
     */
    public static int CmsHandle = -1;

    /**
     * 注册回调函数实现
     */
    static FRegisterCallBack fRegisterCallBack;

    HCISUPCMS.NET_EHOME_CMS_LISTEN_PARAM struCMSListenPara = new HCISUPCMS.NET_EHOME_CMS_LISTEN_PARAM();

    @Autowired
    private IsupConfig isupConfig;

    @Autowired
    private IIsupDeviceService isupDeviceService;

    /**
     * 根据不同操作系统选择不同的库文件和库路径
     *
     * @return
     */
    public void cMS_Init() throws IOException {
        hCEhomeCMS = com.ruoyi.isup.ehome.HikSdkLibraries.load("CMS", HCISUPCMS.class);
        String[] pair = com.ruoyi.isup.ehome.HikSdkLibraries.cryptoPair();
        for (int index = 0; index < pair.length; index++) {
            HCISUPCMS.BYTE_ARRAY path = new HCISUPCMS.BYTE_ARRAY(256);
            path.byValue = com.ruoyi.isup.ehome.HikSdkLibraries.pathBuffer(com.ruoyi.isup.ehome.HikSdkLibraries.required(pair[index]).getAbsolutePath());
            path.write();
            if (!hCEhomeCMS.NET_ECMS_SetSDKInitCfg(index, path.getPointer())) throw new IOException("CMS dependency configuration failed: " + index);
        }
        HCISUPCMS.BYTE_ARRAY components = new HCISUPCMS.BYTE_ARRAY(256);
        components.byValue = com.ruoyi.isup.ehome.HikSdkLibraries.pathBuffer(new java.io.File(com.ruoyi.isup.ehome.HikSdkLibraries.directory(), "HCAapSDKCom").getAbsolutePath());
        components.write();
        if (!hCEhomeCMS.NET_ECMS_Init()) throw new IOException("CMS initialization failed: " + hCEhomeCMS.NET_ECMS_GetLastError());
        if (!hCEhomeCMS.NET_ECMS_SetSDKLocalCfg(5, components.getPointer())) {
            throw new IOException("CMS component path configuration failed: " + hCEhomeCMS.NET_ECMS_GetLastError());
        }
        String sdkLog = System.getProperty("ehome.sdk.log-path");
        if (sdkLog != null) hCEhomeCMS.NET_ECMS_SetLogToFile(3, sdkLog, false);
        log.info("Hikvision CMS SDK {} initialized", com.ruoyi.isup.ehome.HikSdkLibraries.version(hCEhomeCMS.NET_ECMS_GetBuildVersion()));
    }
    public void startCmsListen() {
        if (hCEhomeCMS == null) throw new IllegalStateException("CMS SDK has not been initialized");
        ehomeRegistration.resetOnlineState();
        if (fRegisterCallBack == null) {
            fRegisterCallBack = new FRegisterCallBack();
        }
        //设置CMS监听参数
        System.arraycopy(isupConfig.getIp().getBytes(), 0, struCMSListenPara.struAddress.szIP, 0, isupConfig.getIp().length());
        struCMSListenPara.struAddress.wPort = (short) isupConfig.getCmsServerPort();
        struCMSListenPara.fnCB = fRegisterCallBack;
        struCMSListenPara.write();
        //启动监听，接收设备注册信息
        CmsHandle = hCEhomeCMS.NET_ECMS_StartListen(struCMSListenPara);
        if (CmsHandle < 0) {
            log.error("CMS注册中心监听失败, 错误码:" + hCEhomeCMS.NET_ECMS_GetLastError());
            hCEhomeCMS.NET_ECMS_Fini();
            throw new IllegalStateException("CMS listener failed");
        }
        String CmsListenInfo = new String(struCMSListenPara.struAddress.szIP).trim() + "_" + struCMSListenPara.struAddress.wPort;
        log.info("CMS注册服务器:" + CmsListenInfo + "监听成功!");
    }

    @javax.annotation.PreDestroy
    public void stopCms() {
        int handle = CmsHandle;
        CmsHandle = -1;
        if (handle >= 0 && hCEhomeCMS != null) {
            hCEhomeCMS.NET_ECMS_StopListen(handle);
            hCEhomeCMS.NET_ECMS_Fini();
        }
    }

    /**
     * 云台控制（聚焦）
     *
     * @param id          设备ID
     * @param controSpeed 控制速度  [-100,100], desc:负值表示远,正值表示近,数值大小表示变化速率(最大速率的百分比),0表示停止
     */
    public boolean ptzCtrlFocus(Long id, Integer controSpeed) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);

        String PTZCtrlUrl = "PUT  /ISAPI/System/Video/inputs/channels/" + isupDevice.getChannel() + "/focus";
        String PTZCtrlStopInput = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "\n" +
                "<FocusData xmlns=\"http://www.isapi.org/ver20/XMLSchema\" version=\"2.0\">\n" +
                "  <focus>\n" +
                "    " + controSpeed + "" +
                "  </focus>\n" +
                "</FocusData>\n";
        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, PTZCtrlStopInput);
    }

    /**
     * 云台控制（3D定位）
     *
     * @param id     设备ID
     * @param startx 起始X坐标
     * @param starty 起始Y坐标
     * @param endx   结束X坐标
     * @param endy   结束Y坐标
     */
    public boolean ptz3dPositioningControl(Long id, Integer startx, Integer starty, Integer endx, Integer endy) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);

        String PTZCtrlUrl = "PUT  /ISAPI/PTZCtrl/channels/" + isupDevice.getChannel() + "/position3D";
        //发送云台运动停止请求
        String PTZCtrlStopInput = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "\n" +
                "<Position3D xmlns=\"http://www.isapi.org/ver20/XMLSchema\" version=\"2.0\">\n" +
                "  <StartPoint>\n" +
                "    <positionX>\n" +
                "      " + startx + "" +
                "    </positionX>\n" +
                "    <positionY>\n" +
                "      " + starty + "" +
                "    </positionY>\n" +
                "  </StartPoint>\n" +
                "  <EndPoint>\n" +
                "    <positionX>\n" +
                "     " + endx + "" +
                "    </positionX>\n" +
                "    <positionY>\n" +
                "     " + endy + "" +
                "    </positionY>\n" +
                "  </EndPoint>\n" +
                "</Position3D>\n";
        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, PTZCtrlStopInput);
    }

    /**
     * 获取单一预置点参数
     *
     * @param id       设备ID
     * @param presetId 预置点ID
     */
    public boolean retrievePresetPoint(Long id, Integer presetId) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
        String PTZCtrlUrl = "GET /ISAPI/PTZCtrl/channels/" + isupDevice.getChannel() + "/presets/" + id;

        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, null);
    }

    /**
     * 配置单一预置点参数
     *
     * @param id         设备ID
     * @param presetId   预置点ID
     * @param presetName 预置点名称
     * @param enabled    是否启用
     */
    public boolean configurePresetPoint(Long id, Integer presetId, String presetName, boolean enabled) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
        String PTZCtrlUrl = "PUT /ISAPI/PTZCtrl/channels/" + isupDevice.getChannel() + "/presets/" + presetId;

        String PTZCtrlStopInput = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "\n" +
                "<PTZPreset xmlns=\"http://www.isapi.org/ver20/XMLSchema\" version=\"2.0\">\n" +
                "  <enabled>\n" +
                "    " + enabled + "" +
                "  </enabled>\n" +
                "  <id>\n" +
                "    " + UUID.fastUUID() + "" +
                "  </id>\n" +
                "  <presetName>\n" +
                "    " + presetName + "" +
                "  </presetName>\n" +
                "</PTZPreset>\n";

        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, PTZCtrlStopInput);
    }

    /**
     * 删除单一预置点参数
     *
     * @param id       设备ID
     * @param presetId 预置点ID
     * @return
     */
    public boolean deletePresetPointConfiguration(Long id, Integer presetId) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
        String PTZCtrlUrl = "DELETE  /ISAPI/PTZCtrl/channels/" + isupDevice.getChannel() + "/presets/" + presetId;
        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, null);
    }

    /**
     * 配置单一预置点控制
     *
     * @param id       设备ID
     * @param presetId 预置点ID
     */
    public boolean gotoPresetPoint(Long id, Integer presetId) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
        String PTZCtrlUrl = "PUT  /ISAPI/PTZCtrl/channels/" + isupDevice.getChannel() + "/presets/" + presetId + "/goto";
        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, null);
    }

    /**
     * 获取所有数字通道状态
     *
     * @param id
     */
    public void getAllDigitalChannelStatus(Long id) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);

        // 设备登录信息
        HCNetSDK.NET_DVR_USER_LOGIN_INFO m_strLoginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        m_strLoginInfo.sDeviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        System.arraycopy("192.168.2.212".getBytes(), 0, m_strLoginInfo.sDeviceAddress, 0, "192.168.2.212".length());
        m_strLoginInfo.wPort =8000 ;
        m_strLoginInfo.sUserName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        System.arraycopy("admin".getBytes(), 0, m_strLoginInfo.sUserName, 0, "admin".length());
        m_strLoginInfo.sPassword = new byte[HCNetSDK.NET_DVR_LOGIN_PASSWD_MAX_LEN];
        System.arraycopy("hx147258".getBytes(), 0, m_strLoginInfo.sPassword, 0, "hx147258".length());
        // 是否异步登录：false- 否，true- 是
        m_strLoginInfo.bUseAsynLogin = false;
        // write()调用后数据才写入到内存中
        m_strLoginInfo.write();


        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceinfoV40 = new HCNetSDK.NET_DVR_DEVICEINFO_V40();


        int lUserID = HCNet.hCNetSDK.NET_DVR_Login_V40(m_strLoginInfo, deviceinfoV40);
        if (lUserID < 0) {
            log.info("Login error, error code: " + HCNet.hCNetSDK.NET_DVR_GetLastError());
            HCNet.hCNetSDK.NET_DVR_Cleanup();
            return;
        }

        System.out.println("获取模拟通道个数: " + deviceinfoV40.struDeviceV30.byChanNum);
        System.out.println("模拟通道起始通道号: " + deviceinfoV40.struDeviceV30.byStartChan);
        System.out.println("数字通道起始通道号: " + deviceinfoV40.struDeviceV30.byStartDChan);
        int ipChanNum = deviceinfoV40.struDeviceV30.byIPChanNum + deviceinfoV40.struDeviceV30.byHighDChanNum * 256;
        System.out.println("设备支持的最大IP通道数: " + ipChanNum);

        // 获取 IP 通道参数
        HCNetSDK.NET_DVR_IPPARACFG_V40 ipAccessCfgV40 = new HCNetSDK.NET_DVR_IPPARACFG_V40();
        ipAccessCfgV40.dwSize = ipAccessCfgV40.size();
        int iGroupNO = 0;
        IntByReference bytesReturned = new IntByReference(0);

        if (!HCNet.hCNetSDK.NET_DVR_GetDVRConfig(lUserID, HCNetSDK.NET_DVR_GET_IPPARACFG_V40, iGroupNO,
                ipAccessCfgV40.getPointer(), ipAccessCfgV40.size(), bytesReturned)) {
            System.err.println("NET_DVR_GET_IPPARACFG_V40 error, " + HCNet.hCNetSDK.NET_DVR_GetLastError());
            return;
        } else {
            for (int i = 0; i < ipAccessCfgV40.dwDChanNum; i++) {
                byte streamType = ipAccessCfgV40.struStreamMode[i].byGetStreamType;
                switch (streamType) {
                    case 0:
                        if (ipAccessCfgV40.struStreamMode[i].uGetStream.struChanInfo.byEnable != 0) {
                            byte byIPID = ipAccessCfgV40.struStreamMode[i].uGetStream.struChanInfo.byIPID;
                            byte byIPIDHigh = ipAccessCfgV40.struStreamMode[i].uGetStream.struChanInfo.byIPIDHigh;
                            int iDevInfoIndex = (byIPIDHigh & 0xFF) * 256 + (byIPID & 0xFF) - 1 - iGroupNO * 64;
                            String ip = new String(ipAccessCfgV40.struIPDevInfo[iDevInfoIndex].struIP.sIpV4).trim();
                            System.out.printf("IP channel no.%d is online, IP: %s%n", i + 1, ip);
                        }
                        break;
                    case 1:
                        System.out.println("====================="+ipAccessCfgV40.struStreamMode[i].uGetStream);
                        break;
                }
            }
        }

//        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
//        String PTZCtrlUrl = "GET /ISAPI/ContentMgmt/InputProxy/channels/status";
//        SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, null);
    }

    /**
     * 配置单个数字通道接入参数
     *
     * @param id
     */
    public void configureDigitalChannel(Long id) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
        String PTZCtrlUrl = "PUT /ISAPI/ContentMgmt/InputProxy/channels/" + isupDevice.getChannel();

        String PTZCtrlStopInput = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "\n" +
                "<InputProxyChannel xmlns=\"http://www.isapi.org/ver20/XMLSchema\" version=\"2.0\">\n" +
                "  <!--req, object, 通道信息, attr:version{req, string, 协议版本}-->\n" +
                "  <id>\n" +
                "    <!--req, string, 索引-->test\n" +
                "  </id>\n" +
                "  <name>\n" +
                "    <!--opt, string, 名称-->test\n" +
                "  </name>\n" +
                "  <sourceInputPortDescriptor>\n" +
                "    <!--req, object, 输入通道源描述-->\n" +
                "    <adminProtocol>\n" +
                "      <!--opt, enum, 协议类型, subType:string, [HIKVISION#HIKVISION,SONY#SONY,ISAPI#ISAPI,ONVIF#ONVIF,GB28181#GB28181,OTAP#OTAP], desc:相同的含义设备也会返回proxyProtocol，建议集成用户同时解析<proxyProtocol>和<adminProtocol>，设备以adminProtocol字段值为准；-->ONVIF\n" +
                "    </adminProtocol>\n" +
                "    <proxyProtocol>\n" +
                "      <!--opt, enum, 协议类型, subType:string, [HIKVISION#HIKVISION,SONY#SONY,ISAPI#ISAPI,ONVIF#ONVIF,GB28181#GB28181,cloudStrage#云存储,GMT0028TLS#GMT0028国密TLS], desc:cloudStrage是指接入云存储(武汉云)服务器,这里复用视频通道的接入方式,但不并能把它当作设备,不能进行预览,回放,等操作,主要是用于从云存储获取历史流进行压缩,视为视频通道;GMT0028TLS是指在GMT0028国密模式下,接入设备的协议类型。GMT0028TLS下TLS采用国密加密算法，证书采用国密HTTPS证书（作为客户端）和接入设备进行双向认证-->ONVIF\n" +
                "    </proxyProtocol>\n" +
                "    <addressingFormatType>\n" +
                "      <!--req, enum, 地址类型, subType:string, [hostname#hostname,ipaddress#ipaddress], desc:协议类型为GB28181时本字段无效-->hostname\n" +
                "    </addressingFormatType>\n" +
                "    <hostName>\n" +
                "      <!--opt, string, 域名, range:[1,64], desc:协议类型为GB28181时本字段无效-->test\n" +
                "    </hostName>\n" +
                "    <ipAddress>\n" +
                "      <!--opt, string, IPV4地址, range:[1,32], desc:协议类型为GB28181时本字段无效-->test\n" +
                "    </ipAddress>\n" +
                "    <ipv6Address>\n" +
                "      <!--opt, string, IPV6地址, range:[1,128], desc:协议类型为GB28181时本字段无效-->test\n" +
                "    </ipv6Address>\n" +
                "    <managePortNo>\n" +
                "      <!--req, int, 管理的端口号, range:[1,65535]-->1\n" +
                "    </managePortNo>\n" +
                "    <srcInputPort>\n" +
                "      <!--req, string, 接入设备的通道号, range:[1,16]-->test\n" +
                "    </srcInputPort>\n" +
                "    <userName>\n" +
                "      <!--req, string, 用户名, range:[1,32]-->test\n" +
                "    </userName>\n" +
                "    <password>\n" +
                "      <!--req, string, 密码, range:[1,16]-->test\n" +
                "    </password>\n" +
                "    <connMode>\n" +
                "      <!--opt, enum, 连接模式, subType:string, [plugplay#即插即用,manual#手动]-->plugplay\n" +
                "    </connMode>\n" +
                "    <streamType>\n" +
                "      <!--opt, enum, 取流协议类型, subType:string, [auto#自动,tcp#tcp,udp#udp]-->auto\n" +
                "    </streamType>\n" +
                "    <deviceID>\n" +
                "      <!--opt, string, 设备索引, range:[1,20], desc:协议类型为GB28181时才会存在-->test\n" +
                "    </deviceID>\n" +
                "    <getStreamMode>\n" +
                "      <!--opt, enum, 取流模式, subType:string, [active#主动模式,passive#被动模式], desc:协议类型为GB28181时才会存在-->active\n" +
                "    </getStreamMode>\n" +
                "    <cameraType>\n" +
                "      <!--opt, enum, 相机类型, subType:string, [teacherTrack#教师跟随,studentTrack#学生跟随,teacherLocation#教师定位,studentLocation#学生定位,teacherFull#教师全景,studentFull#学生全景,media#多媒体,blackboard#板书相机,blackboardLocation#板书定位,IPCamera#网络摄像机,IPDome#网络球机,turnSignal#机动车转向灯监测专用相机,none#无]-->none\n" +
                "    </cameraType>\n" +
                "    <isDoubleAdd>\n" +
                "      <!--opt, bool, 添加同一个相机两路码流-->true\n" +
                "    </isDoubleAdd>\n" +
                "    <streamUrl>\n" +
                "      <!--opt, string, RTSP取流URL, range:[1,256], desc:当有此节点时, 取流使用本url, 没有按照原有规则-->test\n" +
                "    </streamUrl>\n" +
                "    <getStream>\n" +
                "      <!--opt, bool, 是否取流, desc:本字段不传,默认为true,表示设备接入后仅获取设备信息,不进行取流录像-->true\n" +
                "    </getStream>\n" +
                "    <RTSP>\n" +
                "      <!--opt, object, RTSP相关-->\n" +
                "      <userName>\n" +
                "        <!--req, string, 用户名, range:[1,64], desc:默认admin-->test\n" +
                "      </userName>\n" +
                "      <password>\n" +
                "        <!--req, string, 密码, range:[0,64], desc:默认空-->test\n" +
                "      </password>\n" +
                "      <portNo>\n" +
                "        <!--ro, opt, int, rtsp取流端口, range:[1,65535], step:1, desc:默认端口554-->554\n" +
                "      </portNo>\n" +
                "    </RTSP>\n" +
                "    <customChanID>\n" +
                "      <!--opt, string, 自定义监控点通道号, range:[1,64], \n" +
                "desc:支持远程定义配置,PJ01PR20191022065_基于H5平台1拖8产品5.5.110版本预研项目引入,\n" +
                "      添加从设备时,平台下发数字通道接入的自定义ID,该ID为唯一标识,63字节,平台下发后,数字通道触发报警时上报,平台通过该字段确认对应设备-->test\n" +
                "    </customChanID>\n" +
                "    <streamMode>\n" +
                "      <!--ro, opt, enum, 码流类型, subType:string, [main#主码流,sub#子码流]-->main\n" +
                "    </streamMode>\n" +
                "    <getSubStream>\n" +
                "      <!--opt, bool, 是否取子码流, desc:本字段不传,默认为true,表示设备接入取流时，是否同时取子码流-->true\n" +
                "    </getSubStream>\n" +
                "    <cloudStorageCfg>\n" +
                "      <!--opt, object, 云存储接入参数, dep:or,{$.InputProxyChannel.sourceInputPortDescriptor.proxyProtocol,eq,cloudStrage}-->\n" +
                "      <accessKey>\n" +
                "        <!--req, string, 密码, range:[1,64], desc:AccessKey是云存储访问Key;Key都由云存储管理系统生成,资源池用户对应秘钥;武汉云2.0引入-->test\n" +
                "      </accessKey>\n" +
                "      <secretKey>\n" +
                "        <!--req, string, 加密密码, range:[1,64], desc:SecretKey是云存储协议加密Key,Key都由云存储管理系统生成,资源池用户对应秘钥;武汉云2.0引入-->test\n" +
                "      </secretKey>\n" +
                "      <encoderID>\n" +
                "        <!--opt, string, 编码器ID, range:[1,64], desc:编码器ID是接入到云存储的相机的编号,云存储给接入的相机分配的管理ID,通过云存储平台获取编码器ID,在接入云存储服务器时添加编码器ID-->test\n" +
                "      </encoderID>\n" +
                "    </cloudStorageCfg>\n" +
                "  </sourceInputPortDescriptor>\n" +
                "  <certificateValidationEnabled>\n" +
                "    <!--opt, bool, 启用证书验证-->true\n" +
                "  </certificateValidationEnabled>\n" +
                "  <defaultAdminPortEnabled>\n" +
                "    <!--opt, bool, 默认通信端口使能-->true\n" +
                "  </defaultAdminPortEnabled>\n" +
                "  <enableAnr>\n" +
                "    <!--opt, bool, 是否启用录像断网续传, desc:相机掉线，重新上线后开始回传掉线期间的全部录像-->true\n" +
                "  </enableAnr>\n" +
                "  <enableTiming>\n" +
                "    <!--opt, bool, 是否对设备校时-->true\n" +
                "  </enableTiming>\n" +
                "  <streamID>\n" +
                "    <!--opt, string, 流ID, range:[1,32], \n" +
                "desc:该字段传入则可以指定当前接入通道对应的流ID;一体机项目使用，NVR作为一体机存储单元采用通道管理接入前端设备进行取流;\n" +
                "    而HC平台内部使用流ID方式进行通道管理,为了保证设备内部实现统一,当前在添加设备时支持指定通道号对应的流ID-->test\n" +
                "  </streamID>\n" +
                "  <enabled>\n" +
                "    <!--opt, bool, 启用通道, desc:此节点后补,默认为true，无该节点表示true-->true\n" +
                "  </enabled>\n" +
                "  <anrInterval>\n" +
                "    <!--ro, opt, int, 录像断网续传间隔时间, range:[1,24], unit:h, unitType:时间, dep:and,{$.InputProxyChannel.enableAnr,eq,true}, desc:NVR根据该间隔时间自动进行录像回传-->1\n" +
                "  </anrInterval>\n" +
                "  <streamTypeReflectList>\n" +
                "    <!--opt, array, 码流类型映射列表, subType:object, range:[0,1], desc:IPC接入NVR时,NVR默认是按照IPC的码流顺序定义该通道的码流类型,码流类型映射是指当NVR的码流资源受限,比如只能选择两路码流,但是想在NVR上只关注相机的第三码流类型,配置该节点将IPC的指定码流类型映射到NVR的指定码流类型上-->\n" +
                "    <streamTypeReflect>\n" +
                "      <!--opt, object, 码流类型映射-->\n" +
                "      <streamType>\n" +
                "        <!--req, enum, IPC码流类型, subType:string, [sub#子码流,third#三码流], desc:IPC码流类型指IPC的通道下的码流类型-->sub\n" +
                "      </streamType>\n" +
                "      <streamReflectType>\n" +
                "        <!--req, enum, NVR码流映射类型, subType:string, [sub#子码流], desc:映射码流类型指NVR该通道下的码流类型-->sub\n" +
                "      </streamReflectType>\n" +
                "    </streamTypeReflect>\n" +
                "  </streamTypeReflectList>\n" +
                "</InputProxyChannel>";
        SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, PTZCtrlStopInput);
    }

    /**
     * 注册回调函数
     */
    public class FRegisterCallBack implements HCISUPCMS.DEVICE_REGISTER_CB {
        @Override
        public boolean invoke(int lUserID, int dwDataType, Pointer pOutBuffer, int dwOutLen, Pointer pInBuffer, int dwInLen, Pointer pUser) {
            // One SDK listener dispatches protocol families into independent services and device tables.
            if ((dwDataType == 0 || dwDataType == 2 || dwDataType == 7)
                    && com.ruoyi.isup.ehome.EhomeRegistration.isLegacy(pOutBuffer, dwOutLen)) {
                try {
                    return dwDataType == 0
                            ? ehomeRegistration.online(lUserID, pOutBuffer, dwOutLen, pInBuffer, dwInLen)
                            : ehomeRegistration.register(lUserID, pOutBuffer, dwOutLen);
                } catch (RuntimeException e) { log.error("EHome 注册回调失败", e); return false; }
            }
            if (dwDataType == 1 && ehomeRegistration.owns(lUserID)) {
                ehomeRegistration.offline(lUserID);
                // Do not synchronously re-enter the vendor SDK from its registration callback.
                java.util.concurrent.CompletableFuture.runAsync(() -> ehomePreviews.getObject().offline(lUserID))
                        .exceptionally(error -> { log.error("释放 EHome 离线预览失败", error); return null; });
                return true;
            }
            switch (dwDataType) {
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_ON: {
                    HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 strDevRegInfo = new HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12();
                    strDevRegInfo.write();
                    Pointer pDevRegInfo = strDevRegInfo.getPointer();
                    pDevRegInfo.write(0, pOutBuffer.getByteArray(0, strDevRegInfo.size()), 0, strDevRegInfo.size());
                    strDevRegInfo.read();

                    log.info("设备上线==========>,DeviceID:" + new String(strDevRegInfo.struRegInfo.byDeviceID).trim());
                    log.info("设备上线==========>,dwSize:" + strDevRegInfo.struRegInfo.dwSize);
                    log.info("设备上线==========>,dwNetUnitType:" + strDevRegInfo.struRegInfo.dwNetUnitType);
                    log.info("设备上线==========>,byFirmwareVersion:" + new String(strDevRegInfo.struRegInfo.byFirmwareVersion).trim());
                    log.info("设备上线==========>,struDevAdd:" + new String(strDevRegInfo.struRegInfo.struDevAdd.szIP).trim(), strDevRegInfo.struRegInfo.struDevAdd.wPort, new String(strDevRegInfo.struRegInfo.struDevAdd.byRes).trim());
                    log.info("设备上线==========>,dwDevType:" + strDevRegInfo.struRegInfo.dwDevType);
                    log.info("设备上线==========>,dwManufacture:" + strDevRegInfo.struRegInfo.dwManufacture);
                    log.info("设备上线==========>,byPassWord:" + new String(strDevRegInfo.struRegInfo.byPassWord).trim());
                    log.info("设备上线==========>,sDeviceSerial:" + new String(strDevRegInfo.struRegInfo.sDeviceSerial).trim());
                    log.info("设备上线==========>,byReliableTransmission:" + strDevRegInfo.struRegInfo.byReliableTransmission);
                    log.info("设备上线==========>,byWebSocketTransmission:" + strDevRegInfo.struRegInfo.byWebSocketTransmission);
                    log.info("设备上线==========>,bySupportRedirect:" + strDevRegInfo.struRegInfo.bySupportRedirect);
                    log.info("设备上线==========>,byDevProtocolVersion:" + new String(strDevRegInfo.struRegInfo.byDevProtocolVersion).trim());
                    log.info("设备上线==========>,bySessionKey:" + new String(strDevRegInfo.struRegInfo.bySessionKey).trim());
                    log.info("设备上线==========>,byRes:" + new String(strDevRegInfo.struRegInfo.byRes).trim());
                    log.info("设备上线==========>,byMarketType:" + strDevRegInfo.struRegInfo.byMarketType);
                    log.info("设备上线==========>,lUserID:" + lUserID);

                    if (ObjectUtil.isNotNull(isupDeviceService.selectIsupDeviceByLuserIdAndIp(lUserID, new String(strDevRegInfo.struRegInfo.struDevAdd.szIP).trim()))) {
                        isupDeviceService.updateIsupDeviceStatusByLuserId(lUserID, new String(strDevRegInfo.struRegInfo.struDevAdd.szIP).trim(), "ON");
                    } else {
                        IsupDevice isupDevice = new IsupDevice();
                        isupDevice.setDeviceId(new String(strDevRegInfo.struRegInfo.byDeviceID).trim());
                        isupDevice.setDwSize(strDevRegInfo.struRegInfo.dwSize);
                        isupDevice.setDwNetUnitType(strDevRegInfo.struRegInfo.dwNetUnitType);
                        isupDevice.setFirmwareVersion(new String(strDevRegInfo.struRegInfo.byFirmwareVersion).trim());
                        isupDevice.setIpAddress(new String(strDevRegInfo.struRegInfo.struDevAdd.szIP).trim());
                        isupDevice.setPort(strDevRegInfo.struRegInfo.struDevAdd.wPort);
                        isupDevice.setDeviceRes(new String(strDevRegInfo.struRegInfo.struDevAdd.byRes).trim());
                        isupDevice.setDevType(strDevRegInfo.struRegInfo.dwDevType);
                        isupDevice.setManufacture(strDevRegInfo.struRegInfo.dwManufacture);
                        isupDevice.setPassword(new String(strDevRegInfo.struRegInfo.byPassWord).trim());
                        isupDevice.setDeviceSerial(new String(strDevRegInfo.struRegInfo.sDeviceSerial).trim());
                        isupDevice.setReliableTransmission(strDevRegInfo.struRegInfo.byReliableTransmission);
                        isupDevice.setWebsocketTransmission(strDevRegInfo.struRegInfo.byWebSocketTransmission);
                        isupDevice.setSupportRedirect(strDevRegInfo.struRegInfo.bySupportRedirect);
                        isupDevice.setDevProtocolVersion(new String(strDevRegInfo.struRegInfo.byDevProtocolVersion).trim());
                        isupDevice.setSessionKey(new String(strDevRegInfo.struRegInfo.bySessionKey).trim());
                        isupDevice.setRes(new String(strDevRegInfo.struRegInfo.byRes).trim());
                        isupDevice.setMarketType(strDevRegInfo.struRegInfo.byMarketType);
                        isupDevice.setLuserId(lUserID);
                        isupDevice.setStatus("ON");
                        isupDeviceService.insertIsupDevice(isupDevice);
                    }
                    return true;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_AUTH: {
                    HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 strDevRegInfo = new HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12();
                    strDevRegInfo.write();
                    Pointer pDevRegInfo = strDevRegInfo.getPointer();
                    pDevRegInfo.write(0, pOutBuffer.getByteArray(0, strDevRegInfo.size()), 0, strDevRegInfo.size());
                    strDevRegInfo.read();
                    byte[] bs = new byte[0];
                    //ISUP5.0登录校验值
                    String szEHomeKey = isupConfig.getIsupKey();
                    bs = szEHomeKey.getBytes();
                    pInBuffer.write(0, bs, 0, szEHomeKey.length());
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_SESSIONKEY: {
                    HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 strDevRegInfo = new HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12();
                    strDevRegInfo.write();
                    Pointer pDevRegInfo = strDevRegInfo.getPointer();
                    pDevRegInfo.write(0, pOutBuffer.getByteArray(0, strDevRegInfo.size()), 0, strDevRegInfo.size());
                    strDevRegInfo.read();
                    HCISUPCMS.NET_EHOME_DEV_SESSIONKEY struSessionKey = new HCISUPCMS.NET_EHOME_DEV_SESSIONKEY();
                    System.arraycopy(strDevRegInfo.struRegInfo.byDeviceID, 0, struSessionKey.sDeviceID, 0, strDevRegInfo.struRegInfo.byDeviceID.length);
                    System.arraycopy(strDevRegInfo.struRegInfo.bySessionKey, 0, struSessionKey.sSessionKey, 0, strDevRegInfo.struRegInfo.bySessionKey.length);
                    struSessionKey.write();
                    Pointer pSessionKey = struSessionKey.getPointer();
                    hCEhomeCMS.NET_ECMS_SetDeviceSessionKey(pSessionKey);
//                    AlarmDemo.hcEHomeAlarm.NET_EALARM_SetDeviceSessionKey(pSessionKey);
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_DAS_REQ: {
                    String dasInfo = "{\n" +
                            "    \"Type\":\"DAS\",\n" +
                            "    \"DasInfo\": {\n" +
                            "        \"Address\":\"" + isupConfig.getIp() + "\",\n" +
                            "        \"Domain\":\"\",\n" +
                            "        \"ServerID\":\"\",\n" +
                            "        \"Port\":" + isupConfig.getCmsServerPort() + ",\n" +
                            "        \"UdpPort\":\n" +
                            "    }\n" +
                            "}";
                    log.info(dasInfo);
                    byte[] bs1 = dasInfo.getBytes();
                    pInBuffer.write(0, bs1, 0, dasInfo.length());
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_OFF: {
                    HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 strDevRegInfo = new HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12();
                    // 设备掉线
                    // 输出设备信息
                    log.info("[设备掉线] DeviceID is " + lUserID + "\n" + new String(strDevRegInfo.struRegInfo.struDevAdd.szIP).trim());
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_DAS_REREGISTER: {
                    // 设备重新注册
                    log.info("[设备重新注册] DeviceID is " + lUserID);
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_ADDRESS_CHANGED: {
                    // 设备地址发生变化
                    log.info("[设备 IP 地址变化] DeviceID is " + lUserID);
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_SESSIONKEY_REQ: {
                    // EHome5.0设备sessionkey请求回调
                    log.info("[设备地址发生变化] DeviceID is " + lUserID);
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_DAS_PINGREO: {
                    // 设备注册心跳
                    log.info("[设备注册心跳] DeviceID is " + lUserID);
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_DAS_EHOMEKEY_ERROR: {
                    // 校验密码失败
                    log.info("[校验密码失败] DeviceID is " + lUserID);
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_SESSIONKEY_ERROR: {
                    // Sessionkey交互异常
                    log.info("[Sessionkey交互异常] DeviceID is " + lUserID);
                    break;
                }
                case HCISUPCMS.EHOME_REGISTER_TYPE.ENUM_DEV_SLEEP: {
                    // 设备进入休眠状态
                    log.info("[设备进入休眠状态] DeviceID is " + lUserID);
                    break;
                }
                default:
                    log.info("回调类型为:" + dwDataType);
                    break;
            }
            return true;
        }
    }


    /**
     * 获取设备信息（型号、版本、序列号等）
     *
     * @param lUserID
     */
    public boolean getDevInfo(int lUserID) {
        String getDevInfoURL = "GET /ISAPI/System/deviceInfo";
        return SdkFunctionWrapUtil.isapiPassThrough(lUserID, getDevInfoURL, "");
    }

    /**
     * 云台控制 ISUP5.0透传接口
     *
     * @param id          设备id
     * @param direction   方向 1-右 2-左 3-上 4-下
     * @param controSpeed 速度
     */
    public boolean ptzCtrlStart(Long id, Integer direction, Integer controSpeed) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
        String pan = "0";
        String tilt = "0";
        if (1 == direction) {
            pan = controSpeed.toString();
            tilt = "0";
        } else if (2 == direction) {
            pan = "-" + controSpeed.toString();
            tilt = "0";
        } else if (3 == direction) {
            pan = "0";
            tilt = controSpeed.toString();
        } else if (4 == direction) {
            pan = "0";
            tilt = "-" + controSpeed.toString();
        }

        String PTZCtrlUrl = "PUT /ISAPI/PTZCtrl/channels/" + isupDevice.getChannel() + "/continuous";
        String PTZCtrlInput = "<?xml version: \"1.0\" encoding=\"UTF-8\"?>\n" +
                "<PTZData>\n" +
                "    <pan>" + pan + "</pan>\n" +
                "    <tilt>" + tilt + "</tilt>\n" +
                "</PTZData>";
        //接口调用成功
        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, PTZCtrlInput);
    }

    /**
     * 云台控制（结束）
     *
     * @param id 设备id
     * @return
     */
    public boolean ptzCtrlEnd(Long id) {
        IsupDevice isupDevice = isupDeviceService.selectIsupDeviceById(id);
        String PTZCtrlUrl = "PUT /ISAPI/PTZCtrl/channels/" + isupDevice.getChannel() + "/continuous";
        String PTZCtrlStopInput = "<?xml version: \"1.0\" encoding=\"UTF-8\"?>\n" +
                "<PTZData>\n" +
                "    <pan>0</pan>\n" +
                "    <tilt>0</tilt>\n" +
                "</PTZData>";
        return SdkFunctionWrapUtil.isapiPassThrough(isupDevice.getLuserId(), PTZCtrlUrl, PTZCtrlStopInput);
    }
}
