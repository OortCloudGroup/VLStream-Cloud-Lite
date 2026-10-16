package com.ruoyi.custom.availability;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Local synthetic wire fixtures. No cameras, business database or platform credentials. */
final class ProtocolFixture implements AutoCloseable {
    interface Handler {void handle(Socket socket)throws Exception;}
    final ServerSocket server;final ExecutorService threads;final List<Socket> sockets=new CopyOnWriteArrayList<>();
    final CountDownLatch disconnected=new CountDownLatch(1);final List<Throwable> errors=new CopyOnWriteArrayList<>();
    ProtocolFixture(Handler handler)throws IOException {
        this(new ServerSocket(0,32,InetAddress.getLoopbackAddress()),handler);
    }
    ProtocolFixture(ServerSocket listener,Handler handler)throws IOException {
        server=listener;threads=Executors.newCachedThreadPool(task->{Thread t=new Thread(task,"custom-protocol-fixture");t.setDaemon(true);return t;});
        threads.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();socket.setSoTimeout(2500);sockets.add(socket);threads.submit(()->{try(Socket connection=socket){handler.handle(connection);}catch(Exception e){if(!server.isClosed())errors.add(e);}finally{disconnected.countDown();}});}catch(IOException stopped){break;}});
    }
    URI uri(String scheme,String path){return URI.create(scheme+"://fixture.invalid:"+server.getLocalPort()+path);}
    static CustomStreamAddressPolicy loopbackPolicy(){return new CustomStreamAddressPolicy(""){
        @Override protected InetAddress[] resolve(String host)throws Exception{return new InetAddress[]{InetAddress.getLoopbackAddress()};}
        @Override public boolean allowed(InetAddress address){return true;}
    };}
    static long deadline(int millis){return System.nanoTime()+millis*1_000_000L;}
    static String header(InputStream input)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int ending=0;while(ending!=0x0d0a0d0a){int n=input.read();if(n<0)throw new EOFException();b.write(n);ending=(ending<<8)|n;if(b.size()>32768)throw new IOException();}return new String(b.toByteArray(),StandardCharsets.US_ASCII);}
    static byte[] bytes(InputStream in,int size)throws IOException{byte[] b=new byte[size];new DataInputStream(in).readFully(b);return b;}
    static void assertClosed(Socket socket)throws IOException{
        try{org.junit.Assert.assertEquals(-1,socket.getInputStream().read());}
        catch(SocketException reset){/* Windows may send RST when a bounded subscriber closes with unread trailer bytes. */}
    }
    static byte[] concat(byte[]... parts){ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] p:parts)b.write(p,0,p.length);return b.toByteArray();}
    static byte[] number(int n){return new byte[]{(byte)(n>>24),(byte)(n>>16),(byte)(n>>8),(byte)n};}
    static byte[] box(String type,byte[]... data){byte[] payload=concat(data);return concat(number(payload.length+8),type.getBytes(StandardCharsets.US_ASCII),payload);}
    static final byte[] SAMPLE={0,0,0,3,0x65,(byte)0x88,(byte)0x84};
    static final byte[] CONFIG={0x17,0,0,0,0,1,0x42,0,0x1e,(byte)0xff,(byte)0xe1,0,4,0x67,0x42,0,0x1e,1,0,2,0x68,(byte)0xce};
    static byte[] video(){return concat(new byte[]{0x17,1,0,0,0},SAMPLE);}
    static byte[] tag(int type,byte[] data){byte[] head=new byte[11];head[0]=(byte)type;head[1]=(byte)(data.length>>16);head[2]=(byte)(data.length>>8);head[3]=(byte)data.length;return concat(head,data,number(11+data.length));}
    static byte[] flv(){return concat(new byte[]{'F','L','V',1,1,0,0,0,9},new byte[4],tag(9,CONFIG),tag(9,video()));}
    static byte[] track(String kind,int sampleOffset,boolean fragmented){
        byte[] handler=new byte[24];System.arraycopy(kind.getBytes(StandardCharsets.US_ASCII),0,handler,8,4);
        byte[] tkhd=new byte[24];System.arraycopy(number(1),0,tkhd,12,4);
        byte[] stsd=box("stsd",new byte[4],number(1),box("avc1",new byte[78],box("avcC",Arrays.copyOfRange(CONFIG,5,CONFIG.length))));
        byte[] stbl=box("stbl",stsd,box("stsz",new byte[4],number(fragmented?0:SAMPLE.length),number(fragmented?0:1)),box("stco",new byte[4],number(1),number(sampleOffset)));
        return box("trak",box("tkhd",tkhd),box("mdia",box("hdlr",handler),box("minf",stbl)));
    }
    static byte[] mp4(String kind){byte[] ftyp=box("ftyp","isom".getBytes(StandardCharsets.US_ASCII),new byte[4]);byte[] moov=box("moov",track(kind,0,false));int offset=ftyp.length+moov.length+8;
        return concat(ftyp,box("moov",track(kind,offset,false)),box("mdat",SAMPLE));}
    static byte[] init(){return concat(box("ftyp",new byte[8]),box("moov",track("vide",0,true)));}
    static byte[] fragment(){byte[] tfhd=box("tfhd",number(0x020000),number(1));byte[] moof=box("moof",box("traf",tfhd,box("trun",number(0x201),number(1),number(0),number(SAMPLE.length))));
        return concat(box("moof",box("traf",tfhd,box("trun",number(0x201),number(1),number(moof.length+8),number(SAMPLE.length)))),box("mdat",SAMPLE));}
    static byte[] ts(){byte[] pat={0,0,(byte)0xb0,13,0,1,(byte)0xc1,0,0,0,1,(byte)0xe1,0,0,0,0,0};
        byte[] pmt={0,2,(byte)0xb0,18,0,1,(byte)0xc1,0,0,(byte)0xe1,1,(byte)0xf0,0,0x1b,(byte)0xe1,1,(byte)0xf0,0,0,0,0,0};
        byte[] pes={0,0,1,(byte)0xe0,0,0,(byte)0x80,0,0,0,0,1,0x65,(byte)0x88,(byte)0x84};
        return concat(packet(0,pat),packet(256,pmt),packet(257,pes));}
    static byte[] packet(int pid,byte[] payload){byte[] p=new byte[188];Arrays.fill(p,(byte)0xff);p[0]=0x47;p[1]=(byte)(0x40|(pid>>8));p[2]=(byte)pid;p[3]=0x10;System.arraycopy(payload,0,p,4,payload.length);return p;}
    @Override public void close()throws IOException {server.close();for(Socket s:sockets)s.close();threads.shutdownNow();}
}
