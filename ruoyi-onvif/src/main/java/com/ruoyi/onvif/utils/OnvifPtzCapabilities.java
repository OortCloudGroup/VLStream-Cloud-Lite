package com.ruoyi.onvif.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.crypto.digest.DigestUtil;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only, profile-specific capability checks. A transport/auth failure is not lack of PTZ. */
public class OnvifPtzCapabilities {
    private static final String DEVICE = "http://www.onvif.org/ver10/device/wsdl";
    private static final String MEDIA = "http://www.onvif.org/ver10/media/wsdl";
    private static final String MEDIA2 = "http://www.onvif.org/ver20/media/wsdl";
    private static final String PTZ = "http://www.onvif.org/ver20/ptz/wsdl";
    private static final String SCHEMA = "http://www.onvif.org/ver10/schema";
    private static final OnvifPtzCapabilities INSTANCE = new OnvifPtzCapabilities();
    private final OnvifPtzClient.Transport transport;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    private static class Cached {
        final Map<String, Object> result;
        final long expires;
        Cached(Map<String, Object> result) {
            this.result = result;
            expires = System.currentTimeMillis() + ("unknown".equals(result.get("status")) ? 5000 : 60000);
        }
    }

    public static OnvifPtzCapabilities getInstance() { return INSTANCE; }
    OnvifPtzCapabilities(OnvifPtzClient.Transport transport) { this.transport = transport; }
    public OnvifPtzCapabilities() {
        this((url, soap) -> {
            try (HttpResponse response = HttpRequest.post(url).timeout(4000)
                    .header("Content-Type", "application/soap+xml; charset=utf-8").body(soap).execute()) {
                return new OnvifPtzClient.Response(response.getStatus(), response.body());
            } catch (Exception error) { throw new ServiceException("ONVIF PTZ 服务连接失败或超时"); }
        });
    }

    public Map<String, Object> inspect(OnvifDevice device) { return inspect(device, false); }
    public Map<String, Object> inspect(OnvifDevice device, boolean refresh) {
        OnvifPtzClient.validate(device);
        String key = device.getIp() + "|" + device.getUserName() + "|" + device.getChannel()
                + "|" + DigestUtil.sha256Hex(device.getPassword());
        Cached cached = cache.get(key);
        if (!refresh && cached != null && cached.expires > System.currentTimeMillis()) return new LinkedHashMap<>(cached.result);
        Map<String, Object> result;
        try { result = probe(device); }
        catch (ServiceException error) {
            String message = error.getMessage();
            String reason = message.contains("NotAuthorized") || message.contains("authenticated or authorized") || message.contains("HTTP 401") || message.contains("HTTP 403")
                    ? "设备云台认证未通过，请检查 ONVIF 账号权限或设备时间"
                    : "无法确认设备云台能力，请检查连接后重试：" + message;
            result = result(device, "unknown", reason, false, false);
        }
        if (cache.size() >= 128) cache.entrySet().removeIf(entry -> entry.getValue().expires <= System.currentTimeMillis());
        if (cache.size() < 128) cache.put(key, new Cached(result));
        return new LinkedHashMap<>(result);
    }

    public void requireContinuous(OnvifDevice device) {
        Map<String, Object> capabilities = inspect(device);
        if (!Boolean.TRUE.equals(capabilities.get("continuousPanTilt")))
            throw new ServiceException("云台控制不可用：" + capabilities.get("reason"));
    }

    public void requirePresets(OnvifDevice device) {
        Map<String, Object> capabilities = inspect(device);
        if (!Boolean.TRUE.equals(capabilities.get("presets")))
            throw new ServiceException("预置点不可用：" + capabilities.get("reason"));
    }

    private Map<String, Object> probe(OnvifDevice device) {
        String base = "http://" + device.getIp();
        Document caps = request(device, base + "/onvif/device_service", "GetCapabilities", DEVICE,
                "<GetCapabilities xmlns=\"" + DEVICE + "\"><Category>All</Category></GetCapabilities>");
        NodeList ptzNodes = caps.getElementsByTagNameNS(SCHEMA, "PTZ");
        if (ptzNodes.getLength() == 0)
            return result(device, "unsupported", "设备未公布 ONVIF PTZ 服务，不支持云台控制", false, false);
        String ptz = OnvifPtzClient.validateEndpoint(base, required((Element) ptzNodes.item(0), "XAddr"));
        NodeList mediaNodes = caps.getElementsByTagNameNS(SCHEMA, "Media");
        String media = null, mediaNamespace = MEDIA;
        if (mediaNodes.getLength() > 0) media = OnvifPtzClient.validateEndpoint(base, required((Element) mediaNodes.item(0), "XAddr"));
        else {
            Document services = request(device, base + "/onvif/device_service", "GetServices", DEVICE,
                    "<GetServices xmlns=\"" + DEVICE + "\"><IncludeCapability>false</IncludeCapability></GetServices>");
            NodeList list = services.getElementsByTagNameNS(DEVICE, "Service");
            for (int i = 0; i < list.getLength(); i++) {
                Element service = (Element) list.item(i);
                String namespace = OnvifDeviceProbe.text(service, "Namespace");
                if (MEDIA.equals(namespace) || MEDIA2.equals(namespace)) {
                    media = OnvifPtzClient.validateEndpoint(base, required(service, "XAddr")); mediaNamespace = namespace;
                    if (MEDIA.equals(namespace)) break;
                }
            }
            if (media == null) throw new ServiceException("设备未公布可读取通道的 Media 服务");
        }
        Document profiles = request(device, media, "GetProfiles", mediaNamespace, "<GetProfiles xmlns=\"" + mediaNamespace + "\"/>");
        Element profile = null;
        NodeList list = profiles.getElementsByTagNameNS(mediaNamespace, "Profiles");
        for (int i = 0; i < list.getLength(); i++) {
            Element candidate = (Element) list.item(i);
            if (device.getChannel().equals(candidate.getAttribute("token"))) { profile = candidate; break; }
        }
        if (profile == null) throw new ServiceException("当前通道不存在，请重新选择 ONVIF 通道");
        NodeList configs = profile.getElementsByTagNameNS("*", "PTZConfiguration");
        if (configs.getLength() == 0 && MEDIA2.equals(mediaNamespace)) configs = profile.getElementsByTagNameNS(SCHEMA, "PTZ");
        if (configs.getLength() == 0) return result(device, "unsupported", "当前视频通道未配置云台，不支持云台控制", false, false);
        Element config = (Element) configs.item(0);
        String configToken = config.getAttribute("token").trim();
        if (configToken.isEmpty()) throw new ServiceException("设备未返回有效云台配置 Token");
        Document options = request(device, ptz, "GetConfigurationOptions", PTZ,
                "<GetConfigurationOptions xmlns=\"" + PTZ + "\"><ConfigurationToken>"
                        + OnvifPtzClient.escape(configToken) + "</ConfigurationToken></GetConfigurationOptions>");
        // GetStatus is read-only and verifies that PTZ access actually authenticates.
        request(device, ptz, "GetStatus", PTZ, "<GetStatus xmlns=\"" + PTZ + "\"><ProfileToken>"
                + OnvifPtzClient.escape(device.getChannel()) + "</ProfileToken></GetStatus>");
        Document nodes = request(device, ptz, "GetNodes", PTZ, "<GetNodes xmlns=\"" + PTZ + "\"/>");
        String nodeToken = required(config, "NodeToken");
        Element node = null;
        NodeList allNodes = nodes.getElementsByTagNameNS(PTZ, "PTZNode");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Element candidate = (Element) allNodes.item(i);
            if (nodeToken.equals(candidate.getAttribute("token"))) { node = candidate; break; }
        }
        if (node == null) return result(device, "unknown", "设备云台能力信息不一致，暂无法确认是否可控", false, false);
        boolean continuous = options.getElementsByTagNameNS(SCHEMA, "ContinuousPanTiltVelocitySpace").getLength() > 0
                && node.getElementsByTagNameNS(SCHEMA, "ContinuousPanTiltVelocitySpace").getLength() > 0;
        String presetCount = OnvifDeviceProbe.text(node, "MaximumNumberOfPresets");
        boolean presets = false;
        try { presets = presetCount != null && Integer.parseInt(presetCount) > 0; } catch (NumberFormatException ignored) { }
        return result(device, continuous || presets ? "supported" : "unsupported",
                continuous ? "" : "当前视频通道不支持连续方向控制", continuous, presets);
    }

    private Document request(OnvifDevice device, String url, String operation, String namespace, String body) {
        return OnvifPtzClient.checked(transport.post(url, OnvifPtzClient.envelope(device, body)), operation, namespace);
    }
    private static String required(Element node, String name) {
        String value = OnvifDeviceProbe.text(node, name);
        if (value == null) throw new ServiceException("设备未返回云台能力字段：" + name);
        return value;
    }
    private static Map<String, Object> result(OnvifDevice device, String status, String reason, boolean continuous, boolean presets) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status); result.put("reason", reason); result.put("continuousPanTilt", continuous);
        result.put("presets", presets); result.put("profileToken", device.getChannel());
        return result;
    }
}
