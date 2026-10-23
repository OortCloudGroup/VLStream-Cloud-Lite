package com.ruoyi.vlstream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import static org.junit.Assert.*;

public class VlStreamCameraRtcMessagePolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void clientMessagesArePinnedToAuthorizedCameraAndCannotCarryDeviceCredentials() throws Exception {
        JsonNode request = mapper.readTree("{\"eventName\":\"__call\",\"data\":{" +
            "\"sessionId\":\"PCWEB-123\",\"to\":\"attacker-camera\",\"from\":\"spoofed\"," +
            "\"user\":\"admin\",\"pwd\":\"device-secret\",\"audio\":\"sendrecv\",\"video\":\"sendrecv\"," +
            "\"mode\":\"record\",\"source\":\"SubStream\",\"iceservers\":\"{\\\"iceServers\\\":[" +
            "{\\\"urls\\\":\\\"turn:turn.example.com:3478\\\",\\\"username\\\":\\\"turn-user\\\",\\\"credential\\\":\\\"turn-secret\\\"}," +
            "{\\\"urls\\\":\\\"stun:stun.example.com:3478\\\"}]}\"}}");

        ObjectNode relayed = VlStreamCameraRtcMessagePolicy.clientMessage(mapper, request, "camera-a", "relay-client", "PCWEB-123");
        assertNotNull(relayed);
        JsonNode data = relayed.path("data");
        assertEquals("camera-a", data.path("to").asText());
        assertEquals("relay-client", data.path("from").asText());
        assertEquals("recvonly", data.path("audio").asText());
        assertEquals("recvonly", data.path("video").asText());
        assertEquals("live", data.path("mode").asText());
        assertFalse(relayed.toString().contains("device-secret"));
        assertFalse(relayed.toString().contains("turn-secret"));
        assertFalse(relayed.toString().contains("turn-user"));
        assertTrue(relayed.toString().contains("stun:stun.example.com:3478"));
        assertFalse(relayed.toString().contains("turn:turn.example.com"));
    }

    @Test public void upstreamMessagesDropPrivateCandidatesAndIceCredentials() throws Exception {
        JsonNode create = mapper.readTree("{\"eventName\":\"_create\",\"data\":{" +
            "\"sessionId\":\"PCWEB-123\",\"state\":\"online\",\"user\":\"admin\",\"pwd\":\"secret\"," +
            "\"iceServers\":\"{\\\"iceServers\\\":[{\\\"urls\\\":[\\\"stun:192.168.1.8:3478\\\",\\\"stun:stun.example.com:3478\\\"]," +
            "\\\"username\\\":\\\"private-user\\\",\\\"credential\\\":\\\"private-secret\\\"},{\\\"urls\\\":\\\"turn:turn.example.com\\\",\\\"credential\\\":\\\"turn-secret\\\"}]}\"}}");
        ObjectNode safeCreate = VlStreamCameraRtcMessagePolicy.serverMessage(mapper, create, "PCWEB-123");
        assertNotNull(safeCreate);
        assertTrue(safeCreate.toString().contains("stun:stun.example.com:3478"));
        assertFalse(safeCreate.toString().contains("192.168.1.8"));
        assertFalse(safeCreate.toString().contains("turn:turn.example.com"));
        assertFalse(safeCreate.toString().contains("private-user"));
        assertFalse(safeCreate.toString().contains("private-secret"));
        assertFalse(safeCreate.toString().contains("admin"));

        JsonNode privateCandidate = mapper.readTree("{\"eventName\":\"_ice_candidate\",\"data\":{" +
            "\"sessionId\":\"PCWEB-123\",\"candidate\":\"{\\\"candidate\\\":\\\"candidate:1 1 udp 100 10.1.2.3 5000 typ host\\\",\\\"sdpMid\\\":\\\"0\\\"}\"}}");
        assertNull(VlStreamCameraRtcMessagePolicy.serverMessage(mapper, privateCandidate, "PCWEB-123"));
        JsonNode publicCandidate = mapper.readTree("{\"eventName\":\"_ice_candidate\",\"data\":{" +
            "\"sessionId\":\"PCWEB-123\",\"candidate\":\"{\\\"candidate\\\":\\\"candidate:2 1 udp 100 8.8.8.8 5000 typ srflx\\\",\\\"sdpMid\\\":\\\"0\\\"}\"}}");
        assertNotNull(VlStreamCameraRtcMessagePolicy.serverMessage(mapper, publicCandidate, "PCWEB-123"));
    }

    @Test public void sdpFilterRemovesPrivateAndLinkLocalCandidatesButPreservesPublicRelayCandidate() {
        String sdp = "v=0\r\no=- 1 1 IN IP4 192.168.1.5\r\nc=IN IP4 192.168.1.5\r\n" +
            "a=candidate:1 1 UDP 100 10.0.0.4 5000 typ host\r\n" +
            "a=candidate:2 1 UDP 90 169.254.1.4 5001 typ host\r\n" +
            "a=candidate:3 1 UDP 80 8.8.8.8 5002 typ relay\r\n";
        String safe = VlStreamCameraRtcMessagePolicy.sanitizeSdp(sdp);
        assertFalse(safe.contains("192.168.1.5"));
        assertFalse(safe.contains("10.0.0.4"));
        assertFalse(safe.contains("169.254.1.4"));
        assertTrue(safe.contains("8.8.8.8"));
        assertTrue(safe.contains("0.0.0.0"));
    }

    @Test public void candidateTargetAndSessionMustBeKnown() throws Exception {
        JsonNode request = mapper.readTree("{\"eventName\":\"__connectto\",\"data\":{" +
            "\"sessionId\":\"PCWEB-123\",\"to\":\"another-camera\"}}");
        ObjectNode relayed = VlStreamCameraRtcMessagePolicy.clientMessage(mapper, request, "authorized-camera", "relay", "PCWEB-123");
        assertEquals("authorized-camera", relayed.path("data").path("to").asText());
        assertFalse(VlStreamCameraRtcMessagePolicy.validSessionId("../another-session"));
        assertTrue(VlStreamCameraRtcMessagePolicy.validSessionId("PCWEB-123"));
    }

    @Test public void candidateFilterClassifiesIpv6BytesAndRejectsMalformedCandidates() {
        for (String address : new String[]{"0:0:0:0:0:0:0:1", "::ffff:ac10:0001", "::ffff:172.16.0.1",
                "fd12::1234", "fe80::1234", "2001:db8::1", "::ffff:7f00:1", "2002:ac10:1::1"}) {
            assertTrue("Must not relay non-public address " + address,
                VlStreamCameraRtcMessagePolicy.isPrivateCandidate("candidate:1 1 udp 100 " + address + " 5000 typ host"));
        }
        assertFalse(VlStreamCameraRtcMessagePolicy.isPrivateCandidate(
            "candidate:1 1 udp 100 2606:4700:4700::1111 5000 typ srflx"));
        for (String malformed : new String[]{"not-a-candidate", "candidate:1 1 udp 100",
                "candidate:1 1 udp 100 public.example.com 5000 typ host",
                "candidate:1 1 udp 100 8.8.8.8 invalid typ host",
                "candidate:1 1 udp 100 8.8.8.8 5000 typ host raddr",
                "candidate:1 1 udp 100 8.8.8.8 5000 typ host rport 5001",
                "candidate:1 1 tcp 100 8.8.8.8 5000 typ host",
                "candidate:1 1 udp 100 8.8.8.8 5000 typ host raddr ::ffff:ac10:1 rport 5001"}) {
            assertTrue("Malformed/unsafe candidate must fail closed: " + malformed,
                VlStreamCameraRtcMessagePolicy.isPrivateCandidate(malformed));
        }
    }

    @Test public void sdpFilterHandlesIpv6ConnectionAndOriginLines() {
        String sdp = "v=0\r\no=- 1 1 IN IP6 0:0:0:0:0:0:0:1\r\nc=IN IP6 fd12::1234\r\na=rtcp:9 IN IP6 fd12::5678\r\n" +
            "a=candidate:1 1 udp 100 ::ffff:ac10:0001 5000 typ host\r\n" +
            "a=candidate:2 1 udp 100 2606:4700:4700::1111 5001 typ srflx\r\n";
        String safe = VlStreamCameraRtcMessagePolicy.sanitizeSdp(sdp);
        assertFalse(safe.contains("0:0:0:0:0:0:0:1"));
        assertFalse(safe.contains("fd12::1234"));
        assertFalse(safe.contains("fd12::5678"));
        assertFalse(safe.contains("::ffff:ac10:0001"));
        assertTrue(safe.contains("2606:4700:4700::1111"));
        assertTrue(safe.contains("c=IN IP6 ::"));
    }

    @Test public void sharingDisablesDataChannelsAndRejectsSctpNegotiation() throws Exception {
        JsonNode call = mapper.readTree("{\"eventName\":\"__call\",\"data\":{\"sessionId\":\"S\",\"datachannel\":\"true\"}}");
        ObjectNode relayed = VlStreamCameraRtcMessagePolicy.clientMessage(mapper, call, "camera", "relay", "S");
        assertEquals("false", relayed.path("data").path("datachannel").asText());
        String sctp = "v=0\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\na=sctp-port:5000\r\n";
        assertEquals("", VlStreamCameraRtcMessagePolicy.sanitizeSdp(sctp));
        ObjectNode answer = mapper.createObjectNode();
        answer.put("eventName", "__answer");
        answer.putObject("data").put("sessionId", "S").put("type", "answer").put("sdp", sctp);
        assertNull(VlStreamCameraRtcMessagePolicy.clientMessage(mapper, answer, "camera", "relay", "S"));
        ObjectNode offer = mapper.createObjectNode();
        offer.put("eventName", "_offer");
        offer.putObject("data").put("sessionId", "S").put("type", "offer").put("sdp", sctp);
        assertNull(VlStreamCameraRtcMessagePolicy.serverMessage(mapper, offer, "S"));
        String mediaOnly = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=recvonly\r\n";
        ((ObjectNode) offer.path("data")).put("sdp", mediaOnly);
        assertNotNull(VlStreamCameraRtcMessagePolicy.serverMessage(mapper, offer, "S"));
    }
}
