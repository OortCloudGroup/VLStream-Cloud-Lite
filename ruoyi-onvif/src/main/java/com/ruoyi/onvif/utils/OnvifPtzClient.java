package com.ruoyi.onvif.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.OnvifPZT;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** PTZ commands use the device-advertised service, with bounded movement and explicit SOAP results. */
public class OnvifPtzClient {
    private static final String DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl";
    private static final String PTZ_NS = "http://www.onvif.org/ver20/ptz/wsdl";
    private static final String SCHEMA_NS = "http://www.onvif.org/ver10/schema";
    private static final OnvifPtzClient INSTANCE = new OnvifPtzClient();
    private final ConcurrentHashMap<String, Endpoint> endpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Endpoint> pendingStops = new ConcurrentHashMap<>();
    private final Transport transport;
    private final OnvifPtzCapabilities capabilities;

    interface Transport { Response post(String url, String soap); }
    static class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
    private static class Endpoint {
        final String url;
        final long expires;
        Endpoint(String url) { this.url = url; this.expires = System.currentTimeMillis() + 60000; }
    }

    public static OnvifPtzClient getInstance() { return INSTANCE; }
    OnvifPtzClient(Transport transport) {
        this.transport = transport;
        this.capabilities = new OnvifPtzCapabilities(transport);
    }
    public OnvifPtzClient() {
        this((url, soap) -> {
            try (HttpResponse response = HttpRequest.post(url).timeout(4000)
                    .header("Content-Type", "application/soap+xml; charset=utf-8").body(soap).execute()) {
                return new Response(response.getStatus(), response.body());
            } catch (Exception e) {
                throw new ServiceException("ONVIF PTZ 服务连接失败或超时");
            }
        });
    }

    public void start(OnvifDevice device, OnvifPZT command) {
        validate(device);
        if (command == null) throw new ServiceException("缺少云台控制参数");
        double[] velocity = velocity(command.getDirection(), command.getControSpeed());
        capabilities.requireContinuous(device);
        String body = "<p:ContinuousMove xmlns:p=\"" + PTZ_NS + "\" xmlns:tt=\"" + SCHEMA_NS + "\">"
                + "<p:ProfileToken>" + escape(device.getChannel()) + "</p:ProfileToken><p:Velocity>"
                + "<tt:PanTilt x=\"" + velocity[0] + "\" y=\"" + velocity[1] + "\"/>"
                + "</p:Velocity><p:Timeout>PT2S</p:Timeout></p:ContinuousMove>";
        call(device, "ContinuousMove", body);
    }

    public void stop(OnvifDevice device) {
        validate(device);
        // A Stop for an existing movement must not depend on a fresh capability read.
        if (!pendingStops.containsKey(device.getIp() + "|" + device.getUserName() + "|" + device.getChannel())) {
            capabilities.requireContinuous(device);
        }
        call(device, "Stop", "<p:Stop xmlns:p=\"" + PTZ_NS + "\"><p:ProfileToken>"
                + escape(device.getChannel()) + "</p:ProfileToken><p:PanTilt>true</p:PanTilt>"
                + "<p:Zoom>true</p:Zoom></p:Stop>");
    }

    private void call(OnvifDevice device, String operation, String body) {
        String key = device.getIp() + "|" + device.getUserName();
        String movementKey = key + "|" + device.getChannel();
        try {
            Endpoint pending = "Stop".equals(operation) ? pendingStops.get(movementKey) : null;
            String url = pending == null ? null : pending.url;
            if (url == null) url = endpoint(device, key);
            // A timeout or malformed reply may occur after movement has started.
            if ("ContinuousMove".equals(operation)) {
                if (pendingStops.size() >= 128) pendingStops.entrySet().removeIf(entry -> entry.getValue().expires <= System.currentTimeMillis());
                if (pendingStops.size() >= 128 && !pendingStops.containsKey(movementKey))
                    throw new ServiceException("活跃云台控制数量已满，请稍后重试");
                pendingStops.put(movementKey, new Endpoint(url));
            }
            checked(transport.post(url, envelope(device, body)), operation, PTZ_NS);
            if ("Stop".equals(operation)) pendingStops.remove(movementKey);
        } catch (ServiceException e) {
            endpoints.remove(key);
            throw e;
        }
    }

    private String endpoint(OnvifDevice device, String key) {
        Endpoint cached = endpoints.get(key);
        if (cached != null && cached.expires > System.currentTimeMillis()) return cached.url;
        String base = "http://" + device.getIp();
        Document capabilities = checked(transport.post(base + "/onvif/device_service", envelope(device,
                "<GetCapabilities xmlns=\"" + DEVICE_NS + "\"><Category>PTZ</Category></GetCapabilities>")),
                "GetCapabilities", DEVICE_NS);
        NodeList ptz = capabilities.getElementsByTagNameNS(SCHEMA_NS, "PTZ");
        if (ptz.getLength() == 0) throw new ServiceException("设备未公布 ONVIF PTZ 服务，不支持云台控制");
        NodeList addresses = ((Element) ptz.item(0)).getElementsByTagNameNS(SCHEMA_NS, "XAddr");
        if (addresses.getLength() == 0) throw new ServiceException("设备未返回 ONVIF PTZ 服务地址");
        String url = validateEndpoint(base, addresses.item(0).getTextContent().trim());
        if (endpoints.size() >= 128) endpoints.entrySet().removeIf(entry -> entry.getValue().expires <= System.currentTimeMillis());
        if (endpoints.size() < 128) endpoints.put(key, new Endpoint(url));
        return url;
    }

    static String validateEndpoint(String base, String address) {
        try {
            URI uri = new URI(address);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getHost() == null
                    || !uri.getHost().equalsIgnoreCase(new URI(base).getHost())) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (Exception e) {
            throw new ServiceException("设备返回无效或不属于本设备的 PTZ 服务地址");
        }
    }

    static double[] velocity(String direction, Double speed) {
        if (speed == null || !Double.isFinite(speed) || speed < 0.1 || speed > 1) {
            throw new ServiceException("云台速度必须在0.1～1之间");
        }
        if ("right".equals(direction)) return new double[]{speed, 0};
        if ("left".equals(direction)) return new double[]{-speed, 0};
        if ("upper".equals(direction)) return new double[]{0, speed};
        if ("below".equals(direction)) return new double[]{0, -speed};
        throw new ServiceException("不支持的云台方向");
    }

    static void validate(OnvifDevice device) {
        if (device == null) throw new ServiceException("ONVIF 设备不存在");
        if (blank(device.getIp()) || blank(device.getUserName()) || blank(device.getPassword())) {
            throw new ServiceException("设备缺少 ONVIF 地址或认证信息");
        }
        if (blank(device.getChannel())) throw new ServiceException("设备缺少 ONVIF 通道 ProfileToken，请重新获取通道");
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    static String envelope(OnvifDevice device, String body) {
        byte[] nonce = RandomUtil.randomBytes(16);
        String created = Instant.now().toString();
        byte[] suffix = (created + device.getPassword()).getBytes(StandardCharsets.UTF_8);
        byte[] digestInput = new byte[nonce.length + suffix.length];
        System.arraycopy(nonce, 0, digestInput, 0, nonce.length);
        System.arraycopy(suffix, 0, digestInput, nonce.length, suffix.length);
        String digest = Base64.encode(DigestUtil.sha1(digestInput));
        return "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Header>"
                + "<w:Security xmlns:w=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\""
                + " xmlns:u=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">"
                + "<w:UsernameToken><w:Username>" + escape(device.getUserName()) + "</w:Username>"
                + "<w:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">"
                + digest + "</w:Password><w:Nonce>" + Base64.encode(nonce) + "</w:Nonce><u:Created>" + created
                + "</u:Created></w:UsernameToken></w:Security></s:Header><s:Body>" + body + "</s:Body></s:Envelope>";
    }

    static Document checked(Response response, String operation, String namespace) {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            document = builder.parse(new ByteArrayInputStream(response.body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ServiceException("ONVIF " + operation + "失败：HTTP " + response.status + "，无有效 SOAP 响应");
        }
        NodeList faults = document.getElementsByTagNameNS("*", "Fault");
        if (faults.getLength() > 0) {
            Element fault = (Element) faults.item(0);
            NodeList reasons = fault.getElementsByTagNameNS("*", "Text");
            if (reasons.getLength() == 0) reasons = fault.getElementsByTagNameNS("*", "faultstring");
            String reason = reasons.getLength() == 0 ? "设备返回 SOAP Fault" : reasons.item(0).getTextContent().trim();
            throw new ServiceException("ONVIF " + operation + "失败：" + reason.substring(0, Math.min(200, reason.length())));
        }
        if (response.status != 200 || document.getElementsByTagNameNS(namespace, operation + "Response").getLength() == 0) {
            throw new ServiceException("ONVIF " + operation + "失败：HTTP " + response.status + "，未收到操作成功应答");
        }
        return document;
    }
}
