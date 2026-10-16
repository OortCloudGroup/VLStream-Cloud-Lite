package com.ruoyi.custom.availability;

import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.custom.mapper.CustomDeviceMapper;
import com.ruoyi.common.deviceavailability.AvailabilitySnapshot;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

/** Infrastructure scanner + authorized preview preflight. Ordinary list reads only cached evidence. */
@Component
public class CustomAvailabilityMonitor {
    private static final Logger LOG=LoggerFactory.getLogger(CustomAvailabilityMonitor.class);
    private final CustomDeviceMapper devices;private final CustomAvailabilityCache cache;private final CustomStreamProbe probe;
    private volatile boolean closing;
    private final boolean enabled;private final long interval,maxBackoff;private final int timeout;
    private final Map<Long,Long> due=new ConcurrentHashMap<>();private final Map<Long,String> fingerprints=new ConcurrentHashMap<>();
    private final Map<Long,Future<CustomAvailabilityCache.Evidence>> running=new ConcurrentHashMap<>();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new SynchronousQueue<>(),task->{Thread thread=new Thread(task,"custom-source-availability");thread.setDaemon(true);return thread;});
    public CustomAvailabilityMonitor(CustomDeviceMapper devices,CustomAvailabilityCache cache,CustomStreamProbe probe,
        @Value("${video.availability.enabled:true}") boolean enabled,@Value("${video.availability.interval-seconds:30}") long interval,
        @Value("${video.availability.max-backoff-seconds:300}") long maxBackoff,@Value("${video.availability.timeout-millis:3000}") int timeout) {
        this.devices=devices;this.cache=cache;this.probe=probe;this.enabled=enabled;this.interval=Math.max(30,interval)*1000;
        this.maxBackoff=Math.max(this.interval,maxBackoff*1000);this.timeout=Math.max(500,Math.min(5000,timeout));
    }
    public CustomAvailabilityCache.Evidence cached(CustomDevice device){CustomStreamSpec source=new CustomStreamSpec(device);CustomAvailabilityCache.Evidence evidence=cache.get(source);if("CAMERA_RTC".equals(source.kind)&&!probe.supportsCameraPresence()&&evidence.getSnapshot().getCheckedAt()==0)evidence.getSnapshot().setReason("UNSUPPORTED_PRESENCE");return evidence;}
    public String proxyUrl(CustomDevice device){return probe.proxyUrl(new CustomStreamSpec(device),timeout);}
    public void validateDirect(CustomDevice device){probe.validateDirect(new CustomStreamSpec(device),timeout);}
    public CustomAvailabilityCache.Evidence preflight(CustomDevice device) {
        CustomStreamSpec source=new CustomStreamSpec(device);CustomAvailabilityCache.Evidence existing=cache.get(source);
        if("CAMERA_RTC".equals(source.kind)&&!probe.supportsCameraPresence()||"ONLINE".equals(existing.getSnapshot().getState())||
            System.currentTimeMillis()-existing.getSnapshot().getCheckedAt()<3000)return existing;
        Future<CustomAvailabilityCache.Evidence> work=submit(source);
        if(work==null)return existing;
        try{work.get(timeout+1500L,TimeUnit.MILLISECONDS);return cache.get(source);}
        catch(TimeoutException expired){work.cancel(true);return cache.get(source);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return existing;}
        catch(Exception unavailable){return cache.get(source);}
    }
    @Scheduled(fixedDelay=5000) public void tick() {
        if(!enabled||closing)return;
        try {
            List<CustomStreamSpec> sources=new ArrayList<>();Set<Long> present=new HashSet<>();long now=System.currentTimeMillis();
            for(Long id:probe.consumeMediaChanges())due.put(id,now);
            for(CustomDevice device:devices.selectDeviceList(new CustomDevice())) {
                present.add(device.getId());CustomStreamSpec source;
                try{source=new CustomStreamSpec(device);}catch(CustomStreamSpec.ProbeFailure invalid){continue;}
                String old=fingerprints.put(device.getId(),source.fingerprint);
                if(old!=null&&!old.equals(source.fingerprint))due.put(device.getId(),now);
                due.putIfAbsent(device.getId(),now+Math.floorMod(device.getId()*7919,interval));
                if(!"CAMERA_RTC".equals(source.kind)||probe.supportsCameraPresence())sources.add(source);
            }
            due.keySet().retainAll(present);fingerprints.keySet().retainAll(present);
            running.keySet().retainAll(present);
            sources.sort(Comparator.comparingLong(source->due.getOrDefault(source.device.getId(),now)));
            for(CustomStreamSpec source:sources)if(due.getOrDefault(source.device.getId(),0L)<=now&&submit(source)==null)break;
        } catch(RuntimeException unavailable){LOG.warn("Custom source monitor unavailable ({})",unavailable.getClass().getSimpleName());}
    }
    private synchronized Future<CustomAvailabilityCache.Evidence> submit(CustomStreamSpec source) {
        if(closing)return null;
        Long id=source.device.getId();Future<CustomAvailabilityCache.Evidence> existing=running.get(id);
        if(existing!=null&&!existing.isDone())return existing;
        FutureTask<CustomAvailabilityCache.Evidence> task=new FutureTask<>(()->run(source));
        try{running.put(id,task);workers.execute(task);return task;}
        catch(RejectedExecutionException busy){running.remove(id,task);return null;}
    }
    private CustomAvailabilityCache.Evidence run(CustomStreamSpec source) {
        String owner=null;long delay=interval;boolean current=true;
        try {
            if(closing)return cache.get(source);
            owner=cache.claim(source);if(owner==null)return cache.get(source);
            CustomStreamProbe.Result result=probe.check(source,timeout);
            CustomDevice latest=devices.selectDeviceById(source.device.getId());
            if(closing||Thread.currentThread().isInterrupted()||latest==null||!source.fingerprint.equals(new CustomStreamSpec(latest).fingerprint)){current=false;return cache.get(source);}
            CustomAvailabilityCache.Evidence evidence=cache.record(source,result);AvailabilitySnapshot snapshot=evidence.getSnapshot();
            if(snapshot.getFailureCount()>=3)delay=Math.min(maxBackoff,interval*(1L<<Math.min(4,snapshot.getFailureCount()-2)));
            return evidence;
        } catch(RuntimeException unavailable){LOG.warn("Custom source {} evidence unavailable ({})",source.device.getId(),unavailable.getClass().getSimpleName());return cache.get(source);}
        finally {try{cache.release(source,owner);}catch(RuntimeException ignored){}
            if(current&&!closing)due.put(source.device.getId(),System.currentTimeMillis()+delay);}
    }
    public void invalidate(CustomDevice device){try{cache.invalidate(new CustomStreamSpec(device));}catch(CustomStreamSpec.ProbeFailure invalid){}due.put(device.getId(),0L);fingerprints.remove(device.getId());}
    @PreDestroy public synchronized void close(){closing=true;workers.shutdownNow();running.values().forEach(work->work.cancel(true));}
}
