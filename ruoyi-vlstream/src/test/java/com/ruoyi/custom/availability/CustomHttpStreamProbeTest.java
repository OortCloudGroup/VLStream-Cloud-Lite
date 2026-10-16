package com.ruoyi.custom.availability;

import org.junit.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.ruoyi.custom.availability.ProtocolFixture.*;

public class CustomHttpStreamProbeTest {
    CustomStreamAddressPolicy policy;
    @Before public void setup(){policy=loopbackPolicy();}
    @After public void close(){policy.close();}
    private String check(byte[] body)throws Exception{return response("200 OK","",body);}
    private String response(String status,String headers,byte[] body)throws Exception{
        try(ProtocolFixture server=new ProtocolFixture(socket->{header(socket.getInputStream());socket.getOutputStream().write(("HTTP/1.1 "+status+"\r\nContent-Length: "+body.length+"\r\n"+headers+"\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();assertClosed(socket);})){
            String result=new CustomHttpStreamProbe(policy).check(server.uri("http","/video"),deadline(1500));assertTrue(server.disconnected.await(1,TimeUnit.SECONDS));assertTrue(server.errors.toString(),server.errors.isEmpty());return result;
        }
    }
    private void failure(String expected,String status,String headers,byte[] body)throws Exception{CustomStreamSpec.ProbeFailure error=assertThrows(CustomStreamSpec.ProbeFailure.class,()->response(status,headers,body));assertEquals(expected,error.reason);assertFalse(error.toString().contains("password"));}
    @Test public void flvRequiresConfigurationAndEncodedVideoAndCloses()throws Exception{assertEquals("AVAILABLE",check(flv()));}
    @Test public void flvAudioOnlyIsNotVideo()throws Exception{assertEquals("UNSUPPORTED_CONTENT",check(concat(new byte[]{'F','L','V',1,4,0,0,0,9},new byte[4],tag(8,new byte[]{(byte)0xaf,1,1,2}))));}
    @Test public void flvVideoHeaderWithoutConfigurationIsNotVideo()throws Exception{assertEquals("UNSUPPORTED_CONTENT",check(concat(new byte[]{'F','L','V',1,1,0,0,0,9},new byte[4],tag(9,video()))));}
    @Test public void corruptFlvTagLengthIsNotVideo()throws Exception{byte[] b=flv();b[b.length-1]++;assertEquals("UNSUPPORTED_CONTENT",check(b));}
    @Test public void mp4VideoSampleIsAvailable()throws Exception{assertEquals("AVAILABLE",check(mp4("vide")));}
    @Test public void mp4AudioAndFakeHdlrAreNotVideo()throws Exception{assertEquals("UNSUPPORTED_VIDEO_METADATA",check(mp4("soun")));assertEquals("UNSUPPORTED_CONTENT",check("fake hdlr vide mdat".getBytes(StandardCharsets.US_ASCII)));}
    @Test public void mp4MetadataWithoutSamplesIsUnknown()throws Exception{assertEquals("UNSUPPORTED_VIDEO_METADATA",check(init()));}
    @Test public void mp4SeiWithoutCodedPictureIsNotVideo()throws Exception{byte[] b=mp4("vide");b[b.length-3]=6;assertEquals("UNSUPPORTED_VIDEO_METADATA",check(b));}
    @Test public void html200IsNotAvailable()throws Exception{assertEquals("UNSUPPORTED_CONTENT",check("<html>online</html>".getBytes(StandardCharsets.UTF_8)));}
    @Test public void authenticationIsDistinct()throws Exception{failure("AUTHENTICATION_FAILED","401 Unauthorized","",new byte[0]);failure("AUTHENTICATION_FAILED","403 Forbidden","",new byte[0]);}
    @Test public void missingStreamAndCompressionAreDistinct()throws Exception{failure("STREAM_UNAVAILABLE","404 Missing","",new byte[0]);failure("UNSUPPORTED_CONTENT","200 OK","Content-Encoding: gzip\r\n",flv());}
    @Test public void chunkedFlvClosesWithoutPermanentPull()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{header(socket.getInputStream());byte[] b=flv();socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"+Integer.toHexString(b.length)+"\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(b);socket.getOutputStream().write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();assertClosed(socket);})){assertEquals("AVAILABLE",new CustomHttpStreamProbe(policy).check(s.uri("http","/v"),deadline(1500)));assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));assertTrue(s.errors.toString(),s.errors.isEmpty());}}
    @Test public void truncatedDeclaredBodyIsRejected()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{header(socket.getInputStream());socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\nFLV".getBytes(StandardCharsets.US_ASCII));})){assertEquals("STREAM_UNAVAILABLE",assertThrows(CustomStreamSpec.ProbeFailure.class,()->new CustomHttpStreamProbe(policy).check(s.uri("http","/v"),deadline(500))).reason);}}
    @Test public void slowBodyRespectsTotalDeadlineAndCloses()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{header(socket.getInputStream());socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();assertClosed(socket);})){long started=System.nanoTime();assertEquals("STREAM_TIMEOUT",assertThrows(CustomStreamSpec.ProbeFailure.class,()->new CustomHttpStreamProbe(policy).check(s.uri("http","/v"),deadline(180))).reason);assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<650);assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));}}
    @Test public void contentSizeIsBoundedAndHasNoPlatformHeaders()throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{String request=header(socket.getInputStream());assertFalse(request.toLowerCase().contains("authorization"));assertFalse(request.toLowerCase().contains("cookie"));byte[] b=new byte[131072];socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2147483647\r\n\r\n".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(b);socket.getOutputStream().flush();assertClosed(socket);})){assertEquals("UNSUPPORTED_CONTENT",new CustomHttpStreamProbe(policy).check(s.uri("http","/secret?token=source-only"),deadline(1500)));assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));assertTrue(s.errors.toString(),s.errors.isEmpty());}}
    @Test public void hlsMasterAndTsAndFragmentedVideoCheckActualChild()throws Exception{hls(false);hls(true);}
    private void hls(boolean fragmented)throws Exception{try(ProtocolFixture s=new ProtocolFixture(socket->{String request=header(socket.getInputStream());byte[] body;
        if(request.startsWith("GET /master"))body="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nmedia\n".getBytes(StandardCharsets.US_ASCII);
        else if(request.startsWith("GET /media"))body=(fragmented?"#EXTM3U\n#EXT-X-MAP:URI=\"init\"\n#EXTINF:1,\nsegment\n":"#EXTM3U\n#EXTINF:1,\nsegment\n").getBytes(StandardCharsets.US_ASCII);
        else body=request.startsWith("GET /init")?init():fragmented?fragment():ts();
        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);
    })){assertEquals("AVAILABLE",new CustomHttpStreamProbe(policy).check(s.uri("http","/master"),deadline(1500)));}}
    @Test public void encryptedOrEmptyHlsIsNotSuccess()throws Exception{assertEquals("UNSUPPORTED_ENCRYPTED",check("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128\na.ts\n".getBytes(StandardCharsets.US_ASCII)));assertEquals("STREAM_UNAVAILABLE",check("#EXTM3U\n".getBytes(StandardCharsets.US_ASCII)));}
    @Test public void barePesWithoutProgramIsNotVideo(){assertFalse(CustomHttpStreamProbe.transportVideo(concat(packet(257,new byte[]{0,0,1,(byte)0xe0,0,0,(byte)0x80,0,0,0,0,1,0x65,1}),new byte[376])));}
    @Test public void redirectLimitAndUserInfoAreRejected()throws Exception{failure("REDIRECT_LIMIT","302 Found","Location: /again\r\n",new byte[0]);failure("ADDRESS_BLOCKED","302 Found","Location: http://user:password@fixture.invalid/video\r\n",new byte[0]);}
    @Test public void redirectToBlockedLoopbackIsNeverConnected()throws Exception{
        policy.close();policy=new CustomStreamAddressPolicy(""){@Override protected InetAddress[] resolve(String host)throws Exception{return new InetAddress[]{InetAddress.getLoopbackAddress()};}@Override public InetAddress pin(URI u,long d){if("fixture.invalid".equals(u.getHost()))return InetAddress.getLoopbackAddress();return super.pin(u,d);}};
        failure("ADDRESS_BLOCKED","302 Found","Location: http://127.0.0.1/private\r\n",new byte[0]);
    }
    @Test public void publicToPrivateRedirectAndManifestAreBlocked()throws Exception{
        policy.close();policy=new CustomStreamAddressPolicy("10.0.0.0/8"){@Override public InetAddress pin(URI u,long d){try{return InetAddress.getByName("fixture.invalid".equals(u.getHost())?"127.0.0.1":"10.111.222.30");}catch(Exception e){throw new CustomStreamSpec.ProbeFailure("ADDRESS_BLOCKED");}}};
        failure("ADDRESS_BLOCKED","302 Found","Location: http://private.invalid/video\r\n",new byte[0]);
        assertEquals("ADDRESS_BLOCKED",assertThrows(CustomStreamSpec.ProbeFailure.class,()->check("#EXTM3U\n#EXTINF:1,\nhttp://private.invalid/video.ts\n".getBytes(StandardCharsets.US_ASCII))).reason);
    }
}
