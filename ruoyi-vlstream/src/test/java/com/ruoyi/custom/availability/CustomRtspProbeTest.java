package com.ruoyi.custom.availability;

import org.junit.*;
import com.ruoyi.common.deviceavailability.RtspDescribeProbe;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.custom.availability.ProtocolFixture.*;

public class CustomRtspProbeTest {
    private static final String SDP="v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\ns=fixture\r\nt=0 0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\na=control:track1\r\n";
    private static void reply(java.net.Socket s,int code,String body,String headers)throws Exception{s.getOutputStream().write(("RTSP/1.0 "+code+" Fixture\r\nContent-Length: "+body.getBytes(StandardCharsets.UTF_8).length+"\r\n"+headers+"\r\n"+body).getBytes(StandardCharsets.UTF_8));s.getOutputStream().flush();}
    private String result(String body)throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{String request=header(socket.getInputStream());assertTrue(request.startsWith("DESCRIBE"));assertFalse(request.contains("accessToken"));reply(socket,200,body,"");assertEquals(-1,socket.getInputStream().read());})){String r=new RtspDescribeProbe().check(s.uri("rtsp","/video").toString(),500,InetAddress.getLoopbackAddress());assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));assertTrue(s.errors.toString(),s.errors.isEmpty());return r;}}
    @Test public void validVideoSdpClosesAndAudioOrFakeTextNeverSucceeds()throws Exception{assertEquals("AVAILABLE",result(SDP));assertEquals("NO_VIDEO_SDP",result(SDP.replace("video","audio")));assertEquals("NO_VIDEO_SDP",result("m=video 0 RTP/AVP 96\r\n"));}
    @Test public void codecMustMatchDeclaredVideoPayloadAndMediaSection()throws Exception{
        assertEquals("NO_VIDEO_SDP",result(SDP.replace("rtpmap:96","rtpmap:97")));
        assertEquals("NO_VIDEO_SDP",result("v=0\r\nm=video 0 RTP/AVP 96\r\na=control:video\r\nm=audio 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\na=control:audio\r\n"));
        assertEquals("NO_VIDEO_SDP",result("v=0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\nm=audio 0 RTP/AVP 97\r\na=control:audio\r\n"));
    }
    @Test public void validPortZeroMultiplePayloadsStaticCodecAndSessionControlRemainCompatible()throws Exception{
        assertEquals("AVAILABLE",result(SDP.replace("RTP/AVP 96","RTP/AVP 97 96")));
        assertEquals("AVAILABLE",result("v=0\r\na=control:*\r\nm=video 0 RTP/AVP 26\r\n"));
        assertEquals("AVAILABLE",result("v=0\r\nm=audio 0 RTP/AVP 97\r\na=rtpmap:97 OPUS/48000\r\na=control:audio\r\n"+SDP.substring(SDP.indexOf("m=video"))));
    }
    @Test public void sourceBasicChallengeUsesOnlySavedCredentials()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{header(socket.getInputStream());reply(socket,401,"","WWW-Authenticate: Basic realm=\"fixture\"\r\n");String auth=header(socket.getInputStream());assertTrue(auth.contains("Authorization: Basic "+Base64.getEncoder().encodeToString("source:password".getBytes(StandardCharsets.UTF_8))));assertFalse(auth.contains("platform-token"));reply(socket,200,SDP,"");assertEquals(-1,socket.getInputStream().read());})){String url=s.uri("rtsp","/v").toString().replace("rtsp://","rtsp://source:password@");assertEquals("AVAILABLE",new RtspDescribeProbe().check(url,500,InetAddress.getLoopbackAddress()));assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));assertTrue(s.errors.toString(),s.errors.isEmpty());}}
    @Test public void totalDeadlineIncludesDnsAndDoesNotResetAtRtsp()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{header(socket.getInputStream());assertEquals(-1,socket.getInputStream().read());})){CustomStreamAddressPolicy policy=new CustomStreamAddressPolicy(""){@Override protected InetAddress[] resolve(String h)throws Exception{Thread.sleep(350);return new InetAddress[]{InetAddress.getLoopbackAddress()};}@Override public boolean allowed(InetAddress a){return true;}};try{CustomMediaBindings bindings=mock(CustomMediaBindings.class);when(bindings.matching(any())).thenReturn(Collections.emptyList());CustomStreamProbe probe=new CustomStreamProbe(policy,new CustomHttpStreamProbe(policy),new CustomRtmpStreamProbe(policy),bindings,mock(CustomMediaPacketReader.class));long started=System.nanoTime();assertEquals("UNREACHABLE",probe.check(new CustomStreamSpec(CustomAvailabilityEvidenceTest.device(1,s.uri("rtsp","/v").toString())),600).reason);long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);assertTrue("total ms "+elapsed,elapsed<850);assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));}finally{policy.close();}}}
    @Test public void tinyPinnedDeadlineIsNotRaisedToFiveHundredMilliseconds()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{header(socket.getInputStream());assertEquals(-1,socket.getInputStream().read());})){long started=System.nanoTime();assertEquals("UNREACHABLE",new RtspDescribeProbe().check(s.uri("rtsp","/v").toString(),80,InetAddress.getLoopbackAddress()));assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<350);}}
}
