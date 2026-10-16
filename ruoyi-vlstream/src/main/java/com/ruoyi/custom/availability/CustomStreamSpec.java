package com.ruoyi.custom.availability;

import com.ruoyi.custom.domain.CustomDevice;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Arrays;

/** Only a persisted, authorized custom row can supply a probe target. Never log this object/URL. */
public final class CustomStreamSpec {
    public final CustomDevice device;
    public final URI uri;
    public final String kind, fingerprint;
    public CustomStreamSpec(CustomDevice device) {
        this.device = device;
        try {
            String address = device.getStreamUrl() == null ? "" : device.getStreamUrl().trim();
            uri = new URI(address);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (uri.getHost() == null || uri.getFragment() != null || address.contains("\r") || address.contains("\n"))
                throw new ProbeFailure("CONFIGURATION_ERROR");
            if (!(scheme.equals("rtsp") || scheme.equals("rtmp") || scheme.equals("http") || scheme.equals("https")))
                throw new ProbeFailure("UNSUPPORTED");
            if (scheme.startsWith("http") && uri.getUserInfo() != null) throw new ProbeFailure("CONFIGURATION_ERROR");
            kind = cameraTarget(address)!=null ? "CAMERA_RTC" : scheme.toUpperCase(Locale.ROOT);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((address + "\n" + (device.getUpdateTime()==null?"":device.getUpdateTime().getTime())).getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(); for (byte value : digest) hash.append(String.format("%02x", value & 255));
            fingerprint = hash.toString();
        } catch (ProbeFailure failure) { throw failure; }
        catch (Exception invalid) { throw new ProbeFailure("CONFIGURATION_ERROR"); }
    }
    public static String cameraTarget(String address) {
        try{URI uri=URI.create(address==null?"":address.trim());if(!Arrays.asList("http","https").contains(uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getUserInfo()!=null)return null;
            String[] parts=uri.getRawPath().split("/");for(int i=0;i+1<parts.length;i++)if("videocall".equalsIgnoreCase(parts[i]))return parts[i+1].matches("[A-Za-z0-9_-]{1,100}")?parts[i+1]:null;
        }catch(RuntimeException invalid){}return null;
    }
    public static final class ProbeFailure extends RuntimeException {
        public final String reason;
        public ProbeFailure(String reason) { super(reason); this.reason = reason; }
    }
}
