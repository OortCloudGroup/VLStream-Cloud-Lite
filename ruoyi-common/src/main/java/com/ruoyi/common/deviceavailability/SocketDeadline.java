package com.ruoyi.common.deviceavailability;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.*;

/** Absolute I/O deadline. Closing the actual socket aborts blocked reads, writes and TLS handshake. */
public final class SocketDeadline implements AutoCloseable {
    private static final ScheduledThreadPoolExecutor CLOCK=new ScheduledThreadPoolExecutor(1,task->{
        Thread thread=new Thread(task,"source-socket-deadline");thread.setDaemon(true);return thread;
    });
    static {CLOCK.setRemoveOnCancelPolicy(true);}
    private final ScheduledFuture<?> expiry;
    public SocketDeadline(Socket socket,long deadline){
        expiry=CLOCK.schedule(()->{try{socket.close();}catch(IOException ignored){}},Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
    }
    @Override public void close(){expiry.cancel(false);}
}
