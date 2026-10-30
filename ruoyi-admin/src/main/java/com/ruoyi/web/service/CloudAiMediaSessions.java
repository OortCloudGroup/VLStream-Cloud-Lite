package com.ruoyi.web.service;

import com.ruoyi.common.exception.ServiceException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Short, renewable source leases; abandoned tasks cannot hold native streams indefinitely. */
@Service
public class CloudAiMediaSessions {
    public static class Session {
        public final String url;
        private final Runnable renew, close;
        private long touched = System.currentTimeMillis();
        public Session(String url, Runnable renew, Runnable close) { this.url=url; this.renew=renew; this.close=close; }
    }
    private final Map<String,Session> sessions = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[64];
    public CloudAiMediaSessions() { Arrays.setAll(locks, i -> new Object()); }
    private Object lock(String key) { return locks[(key.hashCode() & Integer.MAX_VALUE) % locks.length]; }
    private String key(String grant, String run) {
        try { if (!UUID.fromString(run).toString().equals(run)) throw new IllegalArgumentException(); }
        catch (RuntimeException invalid) { throw new ServiceException("检测运行标识无效", 400); }
        return grant + ":" + run;
    }
    public String acquire(String grant, String run, Supplier<Session> open) {
        String key = key(grant,run);
        synchronized (lock(key)) {
            Session session = sessions.get(key);
            if (session != null) {
                try { session.renew.run(); session.touched=System.currentTimeMillis(); return session.url; }
                catch (RuntimeException ended) { session.close.run(); sessions.remove(key,session); }
            }
            if (sessions.size() >= 256) throw new ServiceException("后台取流会话已达容量上限，请稍后重试");
            session = open.get(); sessions.put(key,session); return session.url;
        }
    }
    public void release(String grant, String run) { releaseKey(key(grant,run),false); }
    private void releaseKey(String key, boolean expiredOnly) {
        synchronized (lock(key)) {
            Session session = sessions.get(key);
            if (session == null || expiredOnly && System.currentTimeMillis()-session.touched<=120000) return;
            session.close.run(); sessions.remove(key,session);
        }
    }
    @Scheduled(fixedDelay=15000)
    public void expire() { for(String key:sessions.keySet()) try { releaseKey(key,true); } catch(RuntimeException retry) { /* retain for cleanup retry */ } }
    @PreDestroy public void shutdown() { for(String key:sessions.keySet()) try { releaseKey(key,false); } catch(RuntimeException retry) { } }
}
