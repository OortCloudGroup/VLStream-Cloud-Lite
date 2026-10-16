package com.ruoyi.custom.availability;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Only media created by CUSTOM itself, bound to the exact saved source revision. No URLs/secrets. */
@Component
public class CustomMediaBindings {
    @org.springframework.beans.factory.annotation.Autowired private com.ruoyi.custom.mapper.CustomDeviceMapper devices;
    @org.springframework.beans.factory.annotation.Autowired private com.ruoyi.wvp.media.service.IMediaServerService media;
    private final Map<String,Binding> bindings=new ConcurrentHashMap<>();
    private final Set<Long> changed=ConcurrentHashMap.newKeySet();
    public void add(CustomStreamSpec source,String serverId,String app,String stream,long expiresAt) {
        if(!Arrays.asList("custom","custom_preview","custom_record").contains(app))throw new IllegalArgumentException("Custom app required");
        bindings.put(app+":"+stream,new Binding(source.device.getId(),source.fingerprint,serverId,app,stream,expiresAt));
        changed.add(source.device.getId());
    }
    public void renew(String app,String stream,long expiresAt) {Binding binding=bindings.get(app+":"+stream);if(binding!=null)binding.expiresAt=expiresAt;}
    public void remove(String app,String stream) {Binding old=bindings.remove(app+":"+stream);if(old!=null)changed.add(old.id);}
    public Set<Long> consumeChanged(){Set<Long> result=new HashSet<>(changed);changed.removeAll(result);return result;}
    public boolean known(CustomStreamSpec source,String serverId,String app,String stream){Binding binding=bindings.get(app+":"+stream);return binding!=null&&source.device.getId().equals(binding.id)&&source.fingerprint.equals(binding.fingerprint)&&serverId.equals(binding.serverId);}
    public List<Binding> matching(CustomStreamSpec source) {
        List<Binding> result=new ArrayList<>();long now=System.currentTimeMillis();
        bindings.forEach((key,binding)->{if(binding.expiresAt>0&&binding.expiresAt<=now)bindings.remove(key,binding);
            else if(source.device.getId().equals(binding.id)&&source.fingerprint.equals(binding.fingerprint))result.add(binding);});
        if(media!=null&&devices!=null) {
            com.ruoyi.wvp.media.bean.MediaServer server=media.getDefaultMediaServer();
            if(server!=null&&server.isStatus()) {
                addDiscovered(result,source,server.getId(),"custom","device_"+source.device.getId());
                com.ruoyi.custom.domain.CustomRecordingSession recording=devices.selectActiveSession(source.device.getId());
                if(recording!=null&&server.getId().equals(recording.getMediaServerId())&&"custom_record".equals(recording.getApp()))
                    addDiscovered(result,source,server.getId(),recording.getApp(),recording.getStream());
            }
        }
        return result;
    }
    private void addDiscovered(List<Binding> result,CustomStreamSpec source,String server,String app,String stream){
        if(result.stream().noneMatch(binding->binding.serverId.equals(server)&&binding.app.equals(app)&&binding.stream.equals(stream))) {
            Binding binding=new Binding(source.device.getId(),source.fingerprint,server,app,stream,0);binding.originHash=addressHash(source.device.getStreamUrl());result.add(binding);
        }
    }
    static String addressHash(String address){try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((address==null?"":address.trim()).getBytes(java.nio.charset.StandardCharsets.UTF_8));return java.util.Base64.getEncoder().encodeToString(bytes);}catch(Exception impossible){throw new IllegalStateException();}}
    public static final class Binding {
        public final Long id;public final String fingerprint,serverId,app,stream;private volatile long expiresAt;
        String originHash;
        Binding(Long id,String fingerprint,String serverId,String app,String stream,long expiresAt){this.id=id;this.fingerprint=fingerprint;this.serverId=serverId;this.app=app;this.stream=stream;this.expiresAt=expiresAt;}
    }
}
