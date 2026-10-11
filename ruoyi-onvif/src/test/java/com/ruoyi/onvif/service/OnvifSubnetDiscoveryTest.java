package com.ruoyi.onvif.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.dto.WSDiscoveryDeviceDTO;
import org.junit.Test;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class OnvifSubnetDiscoveryTest {
    @Test public void subnetNormalizesHostAndExcludesNetworkAndBroadcast() {
        List<String> addresses = OnvifSubnetDiscovery.targets("192.168.1.11/24");
        assertEquals(254, addresses.size());
        assertEquals("192.168.1.1", addresses.get(0));
        assertEquals("192.168.1.254", addresses.get(253));
    }
    @Test public void narrowSubnetsAndHostDiscoveryRemainUsable() {
        assertEquals(2, OnvifSubnetDiscovery.targets("10.2.3.4/30").size());
        assertEquals(2, OnvifSubnetDiscovery.targets("172.16.1.4/31").size());
        assertEquals("192.168.1.11", OnvifSubnetDiscovery.targets("192.168.1.11/32").get(0));
    }
    @Test public void publicOversizedAndMalformedTargetsAreRejectedBeforeNetworkAccess() {
        for (String cidr : new String[]{null, "", "0.0.0.0/24", "8.8.8.0/24", "127.0.0.1/32", "169.254.1.0/24",
                "172.32.1.0/24", "192.168.1.0/16", "192.168.1.0/23", "192.168.1.0/33",
                "192.168.1000.0/24", "192.168.1/24", "192.168.001.0/24", "camera/24", "::1/32"}) {
            try { OnvifSubnetDiscovery.targets(cidr); fail("accepted " + cidr); }
            catch (ServiceException expected) { assertNotNull(expected.getMessage()); }
        }
    }
    private DatagramPacket packet(String xml) throws Exception {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(bytes, bytes.length, InetAddress.getByName("192.168.1.11"), 3702);
    }
    private String response(String relates, String addresses) {
        return "<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:a='http://schemas.xmlsoap.org/ws/2004/08/addressing'"
                + " xmlns:d='http://schemas.xmlsoap.org/ws/2005/04/discovery'><s:Header><a:RelatesTo>" + relates
                + "</a:RelatesTo></s:Header><s:Body><d:ProbeMatches><d:ProbeMatch><d:XAddrs>" + addresses
                + "</d:XAddrs></d:ProbeMatch></d:ProbeMatches></s:Body></s:Envelope>";
    }
    @Test public void ipv6AndIpv4AddressesSelectActualRespondingCamera() throws Exception {
        WSDiscoveryDeviceDTO dto = OnvifSubnetDiscovery.response(OnvifSubnetDiscovery.parser(), packet(response("uuid:test",
                "http://[fe80::1]/onvif/device_service http://192.168.1.11:8080/onvif/device_service")), "uuid:test");
        assertNotNull(dto);
        assertEquals("192.168.1.11", dto.getIp());
        assertEquals("http://192.168.1.11:8080/onvif/device_service", dto.getHostName());
    }
    @Test public void unrelatedResponseIsIgnored() throws Exception {
        assertNull(OnvifSubnetDiscovery.response(OnvifSubnetDiscovery.parser(), packet(response("uuid:other",
                "http://192.168.1.11/onvif/device_service")), "uuid:test"));
    }
    @Test public void unexpectedHostProtocolsAndEmbeddedCredentialsAreIgnored() throws Exception {
        for (String address : new String[]{"http://10.0.0.1/onvif", "http://camera/onvif", "file:///tmp/a", "http://u:p@192.168.1.11/onvif"}) {
            assertNull(OnvifSubnetDiscovery.response(OnvifSubnetDiscovery.parser(), packet(response("uuid:test", address)), "uuid:test"));
        }
    }
    @Test public void malformedXmlDoesNotAbortDiscovery() throws Exception {
        assertNull(OnvifSubnetDiscovery.response(OnvifSubnetDiscovery.parser(), packet("<broken"), "uuid:test"));
    }
    @Test public void externalEntitiesAreRejected() throws Exception {
        String xml = "<!DOCTYPE s [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + response("uuid:test", "&x;");
        assertNull(OnvifSubnetDiscovery.response(OnvifSubnetDiscovery.parser(), packet(xml), "uuid:test"));
    }
    @Test public void probeContainsOnvifTypeAndCorrelationId() {
        String xml = new String(OnvifSubnetDiscovery.probe("uuid:test"), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<a:MessageID>uuid:test</a:MessageID>"));
        assertTrue(xml.contains("<d:Types>dn:NetworkVideoTransmitter</d:Types>"));
    }
}
