package com.ruoyi.onvif.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.dto.WSDiscoveryDeviceDTO;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Bounded, read-only WS-Discovery unicast probes for routed camera networks. */
@Service
public class OnvifSubnetDiscovery {
    private static final String DISCOVERY = "http://schemas.xmlsoap.org/ws/2005/04/discovery";
    private static final String ADDRESSING = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
    private static final Semaphore SCANS = new Semaphore(2);

    public List<WSDiscoveryDeviceDTO> discover(String cidr) {
        List<String> targets = targets(cidr);
        if (!SCANS.tryAcquire()) {
            throw new ServiceException("设备发现正在进行，请稍后重试");
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setReceiveBufferSize(262144);
            socket.setSoTimeout(200);
            String messageId = "uuid:" + UUID.randomUUID();
            byte[] probe = probe(messageId);
            DocumentBuilder parser = parser();
            Map<String, WSDiscoveryDeviceDTO> found = new LinkedHashMap<>();
            long started = System.nanoTime();
            long nextSend = 0;
            byte[] buffer = new byte[65507];
            // Two probe rounds, bounded to six seconds including send and receive.
            while (System.nanoTime() - started < 6_000_000_000L) {
                long elapsed = System.nanoTime() - started;
                if (nextSend <= elapsed && nextSend < 4_000_000_000L) {
                    for (String target : targets) {
                        socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName(target), 3702));
                    }
                    nextSend += 3_000_000_000L;
                }
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    String source = packet.getAddress().getHostAddress();
                    if (targets.contains(source)) {
                        WSDiscoveryDeviceDTO device = response(parser, packet, messageId);
                        if (device != null) found.putIfAbsent(device.getIp(), device);
                    }
                } catch (SocketTimeoutException ignored) {
                    // Keep collecting the complete subnet, rather than stopping at the first reply.
                }
            }
            List<WSDiscoveryDeviceDTO> result = new ArrayList<>(found.values());
            result.sort(Comparator.comparingLong(device -> ipv4(device.getIp())));
            return result;
        } catch (Exception e) {
            throw new ServiceException("ONVIF 网段探测失败：" + e.getMessage());
        } finally {
            SCANS.release();
        }
    }

    static List<String> targets(String cidr) {
        if (cidr == null || !cidr.trim().matches("[0-9.]+/(24|25|26|27|28|29|30|31|32)")) {
            throw new ServiceException("请输入内网 IPv4 网段，掩码范围 /24～/32，例如 192.168.1.0/24");
        }
        String[] parts = cidr.trim().split("/");
        long ip = ipv4(parts[0]);
        boolean privateIp = (ip >>> 24) == 10 || (ip >>> 20) == 0xac1 || (ip >>> 16) == 0xc0a8;
        if (!privateIp) throw new ServiceException("仅支持扫描内网 IPv4 网段");
        int prefix = Integer.parseInt(parts[1]);
        long size = 1L << (32 - prefix);
        long start = ip & (0xffffffffL ^ (size - 1));
        long first = prefix <= 30 ? start + 1 : start;
        long end = prefix <= 30 ? start + size - 1 : start + size;
        List<String> result = new ArrayList<>();
        for (long value = first; value < end; value++) {
            result.add(((value >>> 24) & 255) + "." + ((value >>> 16) & 255) + "."
                    + ((value >>> 8) & 255) + "." + (value & 255));
        }
        return result;
    }

    static long ipv4(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) throw new ServiceException("IPv4 网段格式不正确");
        long value = 0;
        for (String octet : octets) {
            if (!octet.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(octet) > 255) {
                throw new ServiceException("IPv4 网段格式不正确");
            }
            value = (value << 8) | Integer.parseInt(octet);
        }
        return value;
    }

    static byte[] probe(String messageId) {
        return ("<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\""
                + " xmlns:a=\"" + ADDRESSING + "\" xmlns:d=\"" + DISCOVERY
                + "\" xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\"><s:Header>"
                + "<a:Action>" + DISCOVERY + "/Probe</a:Action><a:MessageID>" + messageId
                + "</a:MessageID><a:ReplyTo><a:Address>" + ADDRESSING
                + "/role/anonymous</a:Address></a:ReplyTo><a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery"
                + "</a:To></s:Header><s:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types>"
                + "</d:Probe></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
    }

    static DocumentBuilder parser() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder parser = factory.newDocumentBuilder();
        parser.setErrorHandler(new DefaultHandler());
        return parser;
    }

    static WSDiscoveryDeviceDTO response(DocumentBuilder parser, DatagramPacket packet, String messageId) {
        try {
            Document doc = parser.parse(new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()));
            NodeList relates = doc.getElementsByTagNameNS(ADDRESSING, "RelatesTo");
            if (relates.getLength() != 1 || !messageId.equals(relates.item(0).getTextContent().trim())) return null;
            NodeList matches = doc.getElementsByTagNameNS(DISCOVERY, "ProbeMatch");
            String source = packet.getAddress().getHostAddress();
            for (int i = 0; i < matches.getLength(); i++) {
                NodeList addresses = ((Element) matches.item(i)).getElementsByTagNameNS(DISCOVERY, "XAddrs");
                for (int j = 0; j < addresses.getLength(); j++) {
                    for (String address : addresses.item(j).getTextContent().trim().split("\\s+")) {
                        URI uri = new URI(address);
                        if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                                && source.equals(uri.getHost()) && uri.getUserInfo() == null) {
                            WSDiscoveryDeviceDTO dto = new WSDiscoveryDeviceDTO();
                            dto.setIp(source);
                            dto.setHostName(address);
                            return dto;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // A malformed or unrelated UDP packet must not abort discovery of other devices.
        }
        return null;
    }
}
