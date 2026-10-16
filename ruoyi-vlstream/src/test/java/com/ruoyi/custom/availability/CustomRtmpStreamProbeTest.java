package com.ruoyi.custom.availability;

import org.junit.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.ruoyi.custom.availability.ProtocolFixture.*;

public class CustomRtmpStreamProbeTest {
    CustomStreamAddressPolicy policy;
    @Before public void setup(){policy=loopbackPolicy();}
    @After public void close(){policy.close();}
    private static byte[] command(Object... values)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
        for(Object value:values){if(value==null)out.writeByte(5);else if(value instanceof String){out.writeByte(2);out.writeUTF((String)value);}else if(value instanceof Number){out.writeByte(0);out.writeDouble(((Number)value).doubleValue());}else{out.writeByte(3);out.writeUTF("code");out.writeByte(2);out.writeUTF(((Map<?,?>)value).get("code").toString());out.writeShort(0);out.writeByte(9);}}return b.toByteArray();}
    private static void send(OutputStream out,int type,byte[] body)throws IOException{send(out,type,1,body);}
    private static void send(OutputStream out,int type,int stream,byte[] body)throws IOException{for(int at=0;at<body.length;at+=128){if(at==0){out.write(new byte[]{3,0,0,0,(byte)(body.length>>16),(byte)(body.length>>8),(byte)body.length,(byte)type,(byte)stream,(byte)(stream>>8),(byte)(stream>>16),(byte)(stream>>24)});}else out.write(0xc3);out.write(body,at,Math.min(128,body.length-at));}out.flush();}
    private static byte[] readCommand(InputStream input)throws IOException{byte[] header=bytes(input,12);assertEquals(3,header[0]);int length=((header[4]&255)<<16)|((header[5]&255)<<8)|(header[6]&255);assertEquals(20,header[7]);ByteArrayOutputStream body=new ByteArrayOutputStream();while(length>0){int size=Math.min(128,length);body.write(bytes(input,size));length-=size;if(length>0)assertEquals(0xc3,input.read());}return body.toByteArray();}
    private static void handshake(Socket socket)throws Exception{InputStream in=socket.getInputStream();byte[] c=bytes(in,1537);assertEquals(3,c[0]);byte[] s=new byte[3073];s[0]=3;System.arraycopy(c,1,s,1537,1536);socket.getOutputStream().write(s);socket.getOutputStream().flush();bytes(in,1536);}
    private static void begin(Socket socket)throws Exception{handshake(socket);byte[] connect=readCommand(socket.getInputStream());String text=new String(connect,java.nio.charset.StandardCharsets.ISO_8859_1);assertTrue(text.contains("connect"));assertTrue(text.contains("tcUrl"));assertFalse(text.contains("platform-token"));send(socket.getOutputStream(),20,command("_result",1d,null,null));assertTrue(new String(readCommand(socket.getInputStream()),java.nio.charset.StandardCharsets.ISO_8859_1).contains("createStream"));send(socket.getOutputStream(),20,command("_result",2d,null,1d));assertTrue(new String(readCommand(socket.getInputStream()),java.nio.charset.StandardCharsets.ISO_8859_1).contains("play"));}
    private String result(ProtocolFixture.Handler handler,int timeout)throws Exception{try(ProtocolFixture s=new ProtocolFixture(handler)){String result=new CustomRtmpStreamProbe(policy).check(s.uri("rtmp","/live/video?key=source-only"),deadline(timeout));assertTrue(s.disconnected.await(1,TimeUnit.SECONDS));assertTrue(s.errors.toString(),s.errors.isEmpty());return result;}}
    @Test public void genuineHandshakeCommandsAndCodedVideoCloseSubscriber()throws Exception{assertEquals("AVAILABLE",result(socket->{begin(socket);send(socket.getOutputStream(),9,CONFIG);send(socket.getOutputStream(),9,video());assertEquals(-1,socket.getInputStream().read());},1500));}
    @Test public void metadataAudioAndSequenceHeaderAreNotVideo()throws Exception{assertEquals("STREAM_TIMEOUT",result(socket->{begin(socket);send(socket.getOutputStream(),18,command("onMetaData",null));send(socket.getOutputStream(),8,new byte[]{(byte)0xaf,1,1,2});send(socket.getOutputStream(),9,CONFIG);assertEquals(-1,socket.getInputStream().read());},250));}
    @Test public void nalWithoutConfigIsNotSuccess()throws Exception{assertEquals("STREAM_TIMEOUT",result(socket->{begin(socket);send(socket.getOutputStream(),9,video());assertEquals(-1,socket.getInputStream().read());},250));}
    @Test public void missingAndAuthenticationAreDistinct()throws Exception{assertEquals("STREAM_UNAVAILABLE",result(socket->{begin(socket);send(socket.getOutputStream(),20,command("onStatus",0d,null,Collections.singletonMap("code","NetStream.Play.StreamNotFound")));assertEquals(-1,socket.getInputStream().read());},1500));assertEquals("AUTHENTICATION_FAILED",result(socket->{handshake(socket);readCommand(socket.getInputStream());send(socket.getOutputStream(),20,command("_error",1d,null,Collections.singletonMap("code","NetConnection.Connect.Rejected")));assertEquals(-1,socket.getInputStream().read());},1500));}
    @Test public void slowHandshakeHonorsDeadlineAndCloses()throws Exception{long started=System.nanoTime();assertEquals("UNREACHABLE",result(socket->{bytes(socket.getInputStream(),1537);assertEquals(-1,socket.getInputStream().read());},180));assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<700);}
    @Test public void oversizeMessageIsUnknownAndClosesWithinTheBound()throws Exception{assertEquals("UNSUPPORTED_CONTENT_LIMIT",result(socket->{begin(socket);socket.getOutputStream().write(new byte[]{3,0,0,0,0x20,0,0,9,1,0,0,0});socket.getOutputStream().flush();assertEquals(-1,socket.getInputStream().read());},1500));}
    @Test public void enhancedCodecIsUnknownAndKnownAggregateRequiresVideo()throws Exception{assertEquals("UNSUPPORTED_CODEC",result(socket->{begin(socket);send(socket.getOutputStream(),9,new byte[]{(byte)0x90,'h','v','c','1'});assertEquals(-1,socket.getInputStream().read());},1500));assertEquals("AVAILABLE",result(socket->{begin(socket);send(socket.getOutputStream(),9,CONFIG);send(socket.getOutputStream(),22,tag(9,video()));assertEquals(-1,socket.getInputStream().read());},1500));}
    @Test public void fragmentedVideoMessageIsHandled()throws Exception{byte[] large=concat(video(),new byte[20000]);assertEquals("AVAILABLE",result(socket->{begin(socket);send(socket.getOutputStream(),9,CONFIG);send(socket.getOutputStream(),9,large);assertEquals(-1,socket.getInputStream().read());},1500));}
    @Test public void vendorUserInfoIsUnknownWithoutConnect()throws Exception{assertEquals("AUTHENTICATION_UNSUPPORTED",new CustomRtmpStreamProbe(policy).check(URI.create("rtmp://user:password@fixture.invalid/live/v"),deadline(100)));}
    @Test public void badNalAndSequenceLengthsAreRejected(){assertFalse(CustomRtmpStreamProbe.videoPacket(new byte[]{0x17,1,0,0,0,0,0,0,100,0x65}));byte[] config=CONFIG.clone();config[12]=100;assertFalse(CustomRtmpStreamProbe.videoConfig(config));assertTrue(CustomRtmpStreamProbe.videoConfig(CONFIG));}
    @Test public void anotherStreamCannotSupplyConfigurationOrVideo()throws Exception{
        assertEquals("STREAM_TIMEOUT",result(socket->{begin(socket);send(socket.getOutputStream(),9,99,CONFIG);send(socket.getOutputStream(),9,99,video());assertClosed(socket);},600));
        assertEquals("STREAM_TIMEOUT",result(socket->{begin(socket);send(socket.getOutputStream(),9,99,CONFIG);send(socket.getOutputStream(),9,1,video());assertClosed(socket);},600));
    }
    @Test public void otherStreamAggregateAndStatusCannotChangeSelectedStream()throws Exception{
        assertEquals("AVAILABLE",result(socket->{begin(socket);send(socket.getOutputStream(),20,99,command("onStatus",0d,null,Collections.singletonMap("code","NetStream.Play.StreamNotFound")));send(socket.getOutputStream(),22,99,concat(tag(9,CONFIG),tag(9,video())));send(socket.getOutputStream(),9,1,CONFIG);send(socket.getOutputStream(),9,1,video());assertClosed(socket);},2000));
        assertEquals("STREAM_TIMEOUT",result(socket->{begin(socket);send(socket.getOutputStream(),22,99,concat(tag(9,CONFIG),tag(9,video())));assertClosed(socket);},600));
    }
}
