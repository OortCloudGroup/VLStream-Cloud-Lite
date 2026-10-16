package com.ruoyi.common.deviceavailability;

import org.junit.Test;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class RtspDescribeProbeTest {
    @Test public void digestChallengeIsRetriedWithoutStreamingOrCredentialsInRequestUri() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<String> wire = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String first = headers(reader);
                    socket.getOutputStream().write(("RTSP/1.0 401 Unauthorized\r\nCSeq: 1\r\nWWW-Authenticate: Digest realm=\"camera\", nonce=\"fixture\", qop=\"auth\", opaque=\"test\"\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    String second = headers(reader);
                    byte[] body = "v=0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\n".getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("RTSP/1.0 200 OK\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(body);
                    return first + second;
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            assertEquals("AVAILABLE", new RtspDescribeProbe().check("rtsp://tester:fake%2Bpass@127.0.0.1:" + server.getLocalPort() + "/profile", 2000));
            String requests = wire.get(2, TimeUnit.SECONDS);
            assertFalse(requests.contains("fake"));
            assertFalse(requests.contains("SETUP"));
            assertFalse(requests.contains("PLAY"));
            assertTrue(requests.contains("CSeq: 2"));
            assertTrue(requests.contains("qop=auth"));
            assertTrue(requests.contains("opaque=\"test\""));
        }
    }
    @Test public void reachablePortAloneNeverMeansAvailable() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> peer = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    headers(new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
                    socket.getOutputStream().write("RTSP/1.0 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            assertEquals("NO_VIDEO_SDP", new RtspDescribeProbe().check("rtsp://127.0.0.1:" + server.getLocalPort() + "/", 1000));
            peer.get(2, TimeUnit.SECONDS);
        }
    }
    @Test public void refusesInvalidUrlsAndUnsupportedDigestSafely() throws Exception {
        assertEquals("UNSUPPORTED", new RtspDescribeProbe().check("https://camera", 1000));
        assertEquals("CONFIGURATION_ERROR", new RtspDescribeProbe().check("rtsp://", 1000));
        assertNull(RtspDescribeProbe.authorization("Digest realm=\"x\", nonce=\"y\", qop=\"auth-int\"", "u", "fake", "rtsp://camera"));
    }
    @Test public void omittedChallengeAlgorithmUsesMd5WithoutAddingAnUnadvertisedParameter() throws Exception {
        String auth = RtspDescribeProbe.authorization("Digest realm=\"camera\", nonce=\"fixture\"", "u", "fake", "rtsp://camera/profile");
        assertFalse(auth.contains("algorithm="));
        assertTrue(auth.contains("response=\"8352dbeefaba101f557839d469d4bc04\""));
        assertTrue(RtspDescribeProbe.authorization("Digest realm=\"camera\", nonce=\"fixture\", algorithm=MD5", "u", "fake", "rtsp://camera/profile").contains("algorithm=MD5"));
    }
    private static String headers(BufferedReader reader) throws Exception {
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) result.append(line).append('\n');
        return result.toString();
    }
}
