package com.ruoyi.web.service;

import org.junit.Test;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CloudAiMediaSessionsTest {
    @Test public void retriesAndRepeatedPollsShareOneOwnedSession() {
        CloudAiMediaSessions leases=new CloudAiMediaSessions();AtomicInteger opens=new AtomicInteger(),renews=new AtomicInteger(),closes=new AtomicInteger();
        String run=UUID.randomUUID().toString();
        java.util.function.Supplier<CloudAiMediaSessions.Session> open=()->{opens.incrementAndGet();return new CloudAiMediaSessions.Session("http://fixture/live",renews::incrementAndGet,closes::incrementAndGet);};
        leases.acquire("grant",run,open);leases.acquire("grant",run,open);
        assertEquals(1,opens.get());assertEquals(1,renews.get());leases.release("other-grant",run);assertEquals(0,closes.get());
        leases.release("grant",run);leases.release("grant",run);assertEquals(1,closes.get());
    }
    @Test public void cleanupFailureIsRetainedForRetry() {
        CloudAiMediaSessions leases=new CloudAiMediaSessions();AtomicInteger closes=new AtomicInteger();String run=UUID.randomUUID().toString();
        leases.acquire("grant",run,()->new CloudAiMediaSessions.Session("rtsp://fixture/live",()->{},()->{if(closes.incrementAndGet()==1)throw new IllegalStateException("retry");}));
        assertThrows(IllegalStateException.class,()->leases.release("grant",run));leases.release("grant",run);assertEquals(2,closes.get());
    }
    @Test public void expiredLeaseClosesButFreshLeaseRemains() throws Exception {
        CloudAiMediaSessions leases=new CloudAiMediaSessions();AtomicInteger closed=new AtomicInteger();String run=UUID.randomUUID().toString();
        CloudAiMediaSessions.Session session=new CloudAiMediaSessions.Session("rtsp://fixture/live",()->{},closed::incrementAndGet);
        leases.acquire("grant",run,()->session);leases.expire();assertEquals(0,closed.get());
        java.lang.reflect.Field touched=session.getClass().getDeclaredField("touched");touched.setAccessible(true);touched.setLong(session,System.currentTimeMillis()-121000);
        leases.expire();assertEquals(1,closed.get());
    }
    @Test public void invalidRunNeverOpensMedia() {
        CloudAiMediaSessions leases=new CloudAiMediaSessions();AtomicInteger opens=new AtomicInteger();
        assertThrows(RuntimeException.class,()->leases.acquire("grant","../../outside",()->{opens.incrementAndGet();return null;}));assertEquals(0,opens.get());
    }
    @Test public void concurrentPollsCannotCreateDuplicateNativeSessions() throws Exception {
        CloudAiMediaSessions leases=new CloudAiMediaSessions();AtomicInteger opens=new AtomicInteger();String run=UUID.randomUUID().toString();ExecutorService pool=Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<String> action=()->leases.acquire("grant",run,()->{opens.incrementAndGet();return new CloudAiMediaSessions.Session("http://fixture/live",()->{},()->{});});
        try { Future<String> first=pool.submit(action),second=pool.submit(action);assertEquals(first.get(3,TimeUnit.SECONDS),second.get(3,TimeUnit.SECONDS));assertEquals(1,opens.get()); }
        finally { pool.shutdownNow();leases.shutdown(); }
    }
}
