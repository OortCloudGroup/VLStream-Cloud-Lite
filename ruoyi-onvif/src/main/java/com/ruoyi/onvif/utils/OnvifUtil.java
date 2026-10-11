package com.ruoyi.onvif.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.onvif.domain.FetchMainAndSubStreamUris;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.AbsoluteMoveBo;
import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import com.ruoyi.onvif.domain.bo.OnvifPZT;
import com.ruoyi.onvif.domain.bo.PresetsBo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * onvif工具类
 *
 * @Author: 陈江灿
 * @CreateTime: 2025-04-09
 */
public class OnvifUtil {

    /**
     * 添加预置点
     *
     * @param bo
     */
    public static void setPreset(PresetsBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = SetPresetRequest(bo.getUsername(), nonce, created, passwordDigest, bo.getProfileToken(), bo.getPresetToken());
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() != 200) {
            throw new RuntimeException("删除预置点失败");
        }
    }

    /**
     * 删除预置点
     *
     * @param bo
     */
    public static void removePreset(PresetsBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = RemovePresetRequest(bo.getUsername(), nonce, created, passwordDigest, bo.getProfileToken(), bo.getPresetToken());
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() != 200) {
            throw new RuntimeException("删除预置点失败");
        }
    }

    /**
     * gotoPreset
     *
     * @param bo
     */
    public static void gotoPreset(PresetsBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = GotoPresetsStopRequest(bo.getUsername(), nonce, created, passwordDigest, bo.getProfileToken(), bo.getPresetToken());
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() != 200) {
            throw new RuntimeException("gotoPreset失败");
        }
    }

    /**
     * 获取预置点列表
     *
     * @param bo
     * @return
     */
    public static List<Map<String, String>> getPresets(AbsoluteMoveBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = GetPresetsRequest(bo.getUsername(), nonce, created, passwordDigest, bo.getProfileToken());
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() == 200) {
            try {
                return parseSoapResponseGetPresets(response.body());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        throw new RuntimeException("获取预置点列表失败");
    }

    // 获取预置点列表 --- 解析方法
    private static List<Map<String, String>> parseSoapResponseGetPresets(String responseBody) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(responseBody.getBytes()));
        NodeList presetNodes = doc.getElementsByTagName("tptz:Preset");
        List<Map<String, String>> presets = new ArrayList<>();
        for (int i = 0; i < presetNodes.getLength(); i++) {
            Node presetNode = presetNodes.item(i);
            if (presetNode.getNodeType() == Node.ELEMENT_NODE) {
                Element presetElement = (Element) presetNode;
                String token = presetElement.getAttribute("token");
                NodeList nameNodes = presetElement.getElementsByTagName("tt:Name");
                String name = "";
                if (nameNodes.getLength() > 0) {
                    name = nameNodes.item(0).getTextContent();
                }
                Map<String, String> preset = new HashMap<>();
                preset.put("token", token);
                preset.put("name", name);
                presets.add(preset);
            }
        }
        return presets;
    }

    /**
     * 获取数字通道
     */
    public static Set<String> getDigitalChannel(FetchMainAndSubStreamUrisBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = GetDigitalChannel(bo.getUsername(), nonce, created, passwordDigest);
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() == 200) {
            FetchMainAndSubStreamUris info = getOnvifDeviceInfo(bo);
            if (info.getFirm().equals("Dahua")) {
                try {
                    return parseSoapResponseDigitalChannelDahua(response.body());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                return parseSoapResponseDigitalChannelHikvision(response.body());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        throw new RuntimeException("获取数字通道失败");
    }

    //  获取数字通道 --- 海康/水星解析方法
    public static Set<String> parseSoapResponseDigitalChannelHikvision(String responseBody) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(responseBody.getBytes()));
        NodeList videoSourceConfigNodes = doc.getElementsByTagNameNS("http://www.onvif.org/ver10/schema", "SourceToken");
        Set<String> digitalChannels = new HashSet<>();
        Pattern pattern = Pattern.compile("\\d+");
        for (int i = 0; i < videoSourceConfigNodes.getLength(); i++) {
            Node node = videoSourceConfigNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String sourceTokenValue = node.getTextContent().trim();
                Matcher matcher = pattern.matcher(sourceTokenValue);
                if (matcher.find()) {
                    digitalChannels.add(matcher.group());
                }
            }
        }
        return digitalChannels;
    }

    //  获取数字通道 --- 大华解析方法
    public static Set<String> parseSoapResponseDigitalChannelDahua(String responseBody) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(responseBody.getBytes()));
        NodeList videoSourceConfigNodes = doc.getElementsByTagNameNS("http://www.onvif.org/ver10/schema", "VideoSourceConfiguration");
        Set<String> uniqueNumbers = new HashSet<>();
        Pattern pattern = Pattern.compile("\\d+"); // Regex to match digits
        for (int i = 0; i < videoSourceConfigNodes.getLength(); i++) {
            Node videoSourceConfigNode = videoSourceConfigNodes.item(i);
            if (videoSourceConfigNode.getNodeType() == Node.ELEMENT_NODE) {
                Element videoSourceConfigElement = (Element) videoSourceConfigNode;
                NodeList nameNodes = videoSourceConfigElement.getElementsByTagNameNS("http://www.onvif.org/ver10/schema", "Name");
                if (nameNodes.getLength() > 0) {
                    String nameValue = nameNodes.item(0).getTextContent().trim();
                    Matcher matcher = pattern.matcher(nameValue);
                    if (matcher.find()) {
                        uniqueNumbers.add(matcher.group());
                    }
                }
            }
        }
        return uniqueNumbers;
    }

    /**
     * 云台连续位置移动停止
     *
     * @param bo
     * @return
     */
    public static R continuousMoveStop(AbsoluteMoveBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = GenerateStopRequest(bo.getUsername(), nonce, created, passwordDigest, bo.getProfileToken());
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() == 200) {
            return R.ok();
        } else if (response.getStatus() == 500) {
            throw new RuntimeException("该命名空间设备不支持");
        } else {
            throw new RuntimeException("鉴权失败");
        }
    }

    /**
     * 云台连续位置移动
     *
     * @param bo
     * @return
     */
    public static R continuousMove(AbsoluteMoveBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = GenerateContinuousMoveRequest(bo.getUsername(), nonce, created, passwordDigest, bo.getProfileToken(), bo.getX(), bo.getY());
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() == 200) {
            return R.ok();
        } else if (response.getStatus() == 500) {
            throw new RuntimeException("该命名空间设备不支持");
        } else {
            throw new RuntimeException("鉴权失败");
        }

    }

    /**
     * 云台绝对位置移动
     *
     * @param bo
     * @return
     */
    public static R absoluteMove(AbsoluteMoveBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = GenerateAbsoluteMoveRequest(bo.getUsername(), nonce, created, passwordDigest, bo.getProfileToken(), bo.getX(), bo.getY());
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() == 200) {
            return R.ok();
        } else if (response.getStatus() == 500) {
            throw new RuntimeException("该命名空间设备不支持");
        } else {
            throw new RuntimeException("鉴权失败");
        }
    }

    /**
     * 获取设备信息
     *
     * @param bo
     * @return
     */
    public static FetchMainAndSubStreamUris getOnvifDeviceInfo(FetchMainAndSubStreamUrisBo bo) {
        return new OnvifDeviceProbe().read(bo, false);
    }

    /**
     * 获取视频流地址
     *
     * @return
     */
    public static String getProfilesUrlByToken(FetchMainAndSubStreamUrisBo bo, String profileToken) {
        return new OnvifDeviceProbe().stream(bo, profileToken);
    }

    // 获取视频流地址 -- 解析
    private static String parseSoapResponseProfilesUrlByToken(String responseBody) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new java.io.ByteArrayInputStream(responseBody.getBytes("UTF-8")));
        NodeList uriNodes = document.getElementsByTagNameNS("http://www.onvif.org/ver20/media/wsdl", "Uri");
        if (uriNodes.getLength() > 0) {
            return uriNodes.item(0).getTextContent();
        } else {
            throw new RuntimeException("Uri not found in the SOAP response");
        }
    }

    /**
     * 获取流信息token
     *
     * @param bo
     * @return
     */
    public static List<String> getProfileToken(FetchMainAndSubStreamUrisBo bo) {
        return new OnvifDeviceProbe().profiles(bo);
    }

    // 获取流信息token -- 解析
    private static List<String> parseSoapResponseProfileToken(String responseBody) throws Exception {
        List<String> profileNames = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new java.io.ByteArrayInputStream(responseBody.getBytes("UTF-8")));
        NodeList profilesNodes = document.getElementsByTagNameNS("http://www.onvif.org/ver20/media/wsdl", "Profiles");
        for (int i = 0; i < profilesNodes.getLength(); i++) {
            Element profileElement = (Element) profilesNodes.item(i);
            String token = profileElement.getAttribute("token");
            if (token != null && !token.isEmpty()) {
                profileNames.add(token);
            }
        }
        return profileNames;
    }

    /**
     * 获取基本信息
     *
     * @param bo
     * @return
     */
    private static FetchMainAndSubStreamUris getMercury(FetchMainAndSubStreamUrisBo bo) {
        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonce = Base64.encode(nonceBytes);
        String created = Instant.now().toString();
        String passwordDigest = calculatePasswordDigest(nonceBytes, created, bo.getPassword());
        String soapRequest = GetDeviceInformation(bo.getUsername(), nonce, created, passwordDigest);
        String url = "http://" + bo.getIp() + "/onvif/media_service";
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .body(soapRequest)
                .execute();
        if (response.getStatus() == 200) {
            try {
                return parseSoapResponse(response.body());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (response.getStatus() == 500) {
            throw new RuntimeException("该命名空间设备不支持");
        } else if (response.getStatus() == 401) {
            throw new RuntimeException("鉴权失败");
        }
        throw new RuntimeException("获取基本信息失败");
    }

    //获取基本信息 -- 解析
    private static FetchMainAndSubStreamUris parseSoapResponse(String responseBody) throws Exception {
        // 使用 DOM 解析器解析 XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new java.io.ByteArrayInputStream(responseBody.getBytes("UTF-8")));

        // 获取设备信息节点
        NodeList manufacturerNodes = document.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "Manufacturer");
        NodeList modelNodes = document.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "Model");
        NodeList firmwareVersionNodes = document.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "FirmwareVersion");
        NodeList serialNumberNodes = document.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "SerialNumber");
        NodeList hardwareIdNodes = document.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "HardwareId");

        // 提取信息
        String manufacturer = manufacturerNodes.item(0).getTextContent();
        String model = modelNodes.item(0).getTextContent();
        String firmwareVersion = firmwareVersionNodes.item(0).getTextContent();
        FetchMainAndSubStreamUris vo = new FetchMainAndSubStreamUris();
        vo.setFirmwareVersion(firmwareVersion);
        vo.setModel(model);
        vo.setFirm(manufacturer);
        return vo;
    }

    // 生成token
    private static String calculatePasswordDigest(byte[] nonceBytes, String created, String password) {
        byte[] createdBytes = created.getBytes(CharsetUtil.CHARSET_UTF_8);
        byte[] passwordBytes = password.getBytes(CharsetUtil.CHARSET_UTF_8);
        byte[] combinedBytes = new byte[nonceBytes.length + createdBytes.length + passwordBytes.length];
        System.arraycopy(nonceBytes, 0, combinedBytes, 0, nonceBytes.length);
        System.arraycopy(createdBytes, 0, combinedBytes, nonceBytes.length, createdBytes.length);
        System.arraycopy(passwordBytes, 0, combinedBytes, nonceBytes.length + createdBytes.length, passwordBytes.length);
        byte[] sha1Bytes = DigestUtil.sha1(combinedBytes);
        return Base64.encode(sha1Bytes);
    }

    // 获取设备信息
    private static String GetDeviceInformation(String username, String nonce, String created, String passwordDigest) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "  <s:Header xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <soap:Body>\n" +
                "    <tds:GetDeviceInformation />\n" +
                "  </soap:Body>\n" +
                "</soap:Envelope>";
    }

    // 获取流信息
    private static String GetProfiles(String username, String nonce, String created, String passwordDigest) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "    <GetProfiles xmlns=\"http://www.onvif.org/ver20/media/wsdl\" />\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    // 获取流地址
    private static String GetProfilesUrl(String username, String nonce, String created, String passwordDigest, String profileToken) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "    <GetStreamUri xmlns=\"http://www.onvif.org/ver20/media/wsdl\">\n" +
                "      <Protocol>RtspUnicast</Protocol>\n" +
                "      <ProfileToken>" + profileToken + "</ProfileToken>\n" +
                "    </GetStreamUri>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    // 云台绝对位置移动
    private static String GenerateAbsoluteMoveRequest(String username, String nonce,
                                                      String created, String passwordDigest, String profileToken, double x, double y) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <tptz:AbsoluteMove>\n" +
                "      <tptz:ProfileToken>" + profileToken + "</tptz:ProfileToken>\n" +
                "      <tptz:Position>\n" +
                "        <tt:PanTilt x=\"" + x + "\" y=\"" + y + "\" space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace\" />\n" +
                "      </tptz:Position>\n" +
                "    </tptz:AbsoluteMove>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    // 云台连续移动
    private static String GenerateContinuousMoveRequest(String username, String nonce,
                                                        String created, String passwordDigest, String profileToken, double x, double y) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <tptz:ContinuousMove>\n" +
                "      <tptz:ProfileToken>" + profileToken + "</tptz:ProfileToken>\n" +
                "      <tptz:Velocity>\n" +
                "        <tt:PanTilt x=\"" + x + "\" y=\"" + y + "\" />\n" +
                "        <tt:Zoom x=\"0\" />\n" +
                "      </tptz:Velocity>\n" +
                "    </tptz:ContinuousMove>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    // 云台连续停止
    private static String GenerateStopRequest(String username, String nonce,
                                              String created, String passwordDigest, String profileToken) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <tptz:Stop>\n" +
                "      <tptz:ProfileToken>" + profileToken + "</tptz:ProfileToken>\n" +
                "      <tptz:PanTilt>true</tptz:PanTilt>\n" +
                "      <tptz:Zoom>true</tptz:Zoom>\n" +
                "    </tptz:Stop>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    // 获取数字通道
    private static String GetDigitalChannel(String username, String nonce, String created, String passwordDigest) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "  <s:Header xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <soap:Body>\n" +
                "    <trt:GetProfiles />\n" +
                "  </soap:Body>\n" +
                "</soap:Envelope>";
    }


    // 云台-获取预置点
    private static String GetPresetsRequest(String username, String nonce,
                                            String created, String passwordDigest, String profileToken) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <tptz:GetPresets>\n" +
                "      <tptz:ProfileToken>" + profileToken + "</tptz:ProfileToken>\n" +
                "    </tptz:GetPresets>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    // 云台-goto预置点
    private static String GotoPresetsStopRequest(String username, String nonce,
                                                 String created, String passwordDigest, String profileToken, String presetToken) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <tptz:GotoPreset>\n" +
                "      <tptz:ProfileToken>" + profileToken + "</tptz:ProfileToken>\n" +
                "      <tptz:PresetToken>" + presetToken + "</tptz:PresetToken>\n" +
                "    </tptz:GotoPreset>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    // 云台-删除预置点
    private static String RemovePresetRequest(String username, String nonce,
                                              String created, String passwordDigest, String profileToken, String presetToken) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <tptz:RemovePreset>\n" +
                "      <tptz:ProfileToken>" + profileToken + "</tptz:ProfileToken>\n" +
                "      <tptz:PresetToken>" + presetToken + "</tptz:PresetToken>\n" +
                "    </tptz:RemovePreset>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }


    // 云台-添加预置点
    private static String SetPresetRequest(String username, String nonce,
                                           String created, String passwordDigest, String profileToken, String presetToken) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "  <s:Header>\n" +
                "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "      <wsse:UsernameToken>\n" +
                "        <wsse:Username>" + username + "</wsse:Username>\n" +
                "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "        <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "        <wsu:Created>" + created + "</wsu:Created>\n" +
                "      </wsse:UsernameToken>\n" +
                "    </wsse:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <tptz:SetPreset>\n" +
                "      <tptz:ProfileToken>" + profileToken + "</tptz:ProfileToken>\n" +
                "      <tptz:PresetName>" + presetToken + "</tptz:PresetName>\n" +
                "    </tptz:SetPreset>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
    }

    /**
     * 云台-开始移动
     *
     * @param onvifDevice
     * @param onvifPZT
     */
    public static void onvifPZTStart(OnvifDevice onvifDevice, OnvifPZT onvifPZT) {
        OnvifPtzClient.getInstance().start(onvifDevice, onvifPZT);
    }

    /**
     * 云台-停止移动
     *
     * @param onvifDevice
     * @param onvifPZT
     */
    public static void onvifPZTEnd(OnvifDevice onvifDevice, OnvifPZT onvifPZT) {
        OnvifPtzClient.getInstance().stop(onvifDevice);
    }
}
