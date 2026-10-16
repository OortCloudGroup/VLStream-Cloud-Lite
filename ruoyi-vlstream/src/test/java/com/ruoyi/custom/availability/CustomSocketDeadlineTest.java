package com.ruoyi.custom.availability;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.ruoyi.custom.availability.ProtocolFixture.*;

public class CustomSocketDeadlineTest {
    @Test public void blockedPongOutputIsAbortedByClosingItsSocket()throws Exception{
        byte[] ping={3,0,0,0,0,0,6,4,0,0,0,0,0,6,0,0,0,1};CountDownLatch entered=new CountDownLatch(1),closed=new CountDownLatch(1);
        Socket controlled=new Socket(){
            @Override public InputStream getInputStream(){return new ByteArrayInputStream(ping);}
            @Override public OutputStream getOutputStream(){return new OutputStream(){@Override public void write(int value)throws IOException{entered.countDown();try{closed.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new SocketException("Socket closed");}};}
            @Override public void setSoTimeout(int timeout){}
            @Override public void close(){closed.countDown();}
        };
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try(CustomRtmpStreamProbe.Wire wire=new CustomRtmpStreamProbe.Wire(controlled,deadline(100))){
            Future<?> running=caller.submit(()->{try{wire.next();fail("No response expected");}catch(Exception expected){}});
            assertTrue(entered.await(1,TimeUnit.SECONDS));running.get(500,TimeUnit.MILLISECONDS);assertEquals(0,closed.getCount());
        }finally{controlled.close();caller.shutdownNow();}
    }
    @Test public void actualSocketBackpressureWriteEndsAtDeadline()throws Exception{
        CountDownLatch accepted=new CountDownLatch(1);ExecutorService caller=Executors.newSingleThreadExecutor();
        try(ProtocolFixture server=new ProtocolFixture(socket->{socket.setReceiveBufferSize(1024);accepted.countDown();Thread.sleep(1800);});Socket socket=new Socket(){
            private OutputStream buffered;
            @Override public OutputStream getOutputStream()throws IOException{if(buffered==null)buffered=new BufferedOutputStream(super.getOutputStream(),65536);return buffered;}
        }){
            socket.setSendBufferSize(1024);socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),server.server.getLocalPort()));assertTrue(accepted.await(1,TimeUnit.SECONDS));
            try(CustomRtmpStreamProbe.Wire wire=new CustomRtmpStreamProbe.Wire(socket,deadline(300))){
                long start=System.nanoTime();Future<Boolean> write=caller.submit(()->{try{wire.send(20,1,new byte[4*1024*1024]);return false;}catch(Exception stopped){return true;}});
                assertTrue(write.get(1200,TimeUnit.MILLISECONDS));assertTrue(socket.isClosed());assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1200);
            }
        }finally{caller.shutdownNow();}
    }
    @Test public void canceledDeadlineDoesNotCloseACompletedCallerSocket()throws Exception{
        Socket socket=new Socket();try{try(com.ruoyi.common.deviceavailability.SocketDeadline guard=new com.ruoyi.common.deviceavailability.SocketDeadline(socket,deadline(30))){}Thread.sleep(70);assertFalse(socket.isClosed());}finally{socket.close();}
    }
}
