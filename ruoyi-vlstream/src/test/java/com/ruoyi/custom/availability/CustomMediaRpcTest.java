package com.ruoyi.custom.availability;

import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.custom.availability.ProtocolFixture.*;

public class CustomMediaRpcTest {
    private MediaServer media(ProtocolFixture s){MediaServer server=new MediaServer();server.setId("owned");server.setIp("127.0.0.1");server.setHttpPort(s.server.getLocalPort());server.setSecret("owned-zlm-secret");server.setStatus(true);return server;}
    private CustomMediaPacketReader reader(MediaServer server){IMediaServerService media=mock(IMediaServerService.class);when(media.getOne("owned")).thenReturn(server);return new CustomMediaPacketReader(media);}
    private CustomMediaBindings.Binding binding(){return new CustomMediaBindings.Binding(1L,"revision","owned","custom_preview","owned-test",0);}
    @Test public void increasingFramesFromOwnedRpcAreActualReceivingEvidenceAndClose()throws Exception{AtomicInteger frames=new AtomicInteger(10);try(ProtocolFixture s=new ProtocolFixture(socket->{String request=header(socket.getInputStream());assertTrue(request.startsWith("POST /index/api/getMediaInfo"));assertFalse(request.toLowerCase().contains("authorization:"));assertFalse(request.contains("accessToken"));int length=Integer.parseInt(java.util.Arrays.stream(request.split("\r\n")).filter(line->line.startsWith("Content-Length:")).findFirst().get().split(":",2)[1].trim());String body=new String(bytes(socket.getInputStream(),length),StandardCharsets.UTF_8);assertTrue(body.contains("secret=owned-zlm-secret"));assertTrue(body.contains("app=custom_preview"));assertFalse(body.contains("platform"));byte[] response=CustomAvailabilityEvidenceTest.sample(frames.incrementAndGet(),true).toJSONString().getBytes(StandardCharsets.UTF_8);socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+response.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(response);socket.getOutputStream().flush();assertEquals(-1,socket.getInputStream().read());})){assertTrue(reader(media(s)).receiving(binding(),deadline(1000)));assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));assertTrue(s.errors.toString(),s.errors.isEmpty());assertEquals(12,frames.get());}}
    @Test public void slowRpcBodyDoesNotResetAbsoluteDeadlineAndCloses()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{String h=header(socket.getInputStream());int length=Integer.parseInt(java.util.Arrays.stream(h.split("\r\n")).filter(line->line.startsWith("Content-Length:")).findFirst().get().split(":",2)[1].trim());bytes(socket.getInputStream(),length);Thread.sleep(110);socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();assertEquals(-1,socket.getInputStream().read());})){long now=System.nanoTime();assertFalse(reader(media(s)).receiving(binding(),deadline(200)));assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-now)<450);assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));}}
    @Test public void rpcRedirectDoesNotForwardZlmSecretToLocation()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{header(socket.getInputStream());socket.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:1/steal\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));})){assertFalse(reader(media(s)).receiving(binding(),deadline(500)));assertEquals(1,s.sockets.size());}}
}
