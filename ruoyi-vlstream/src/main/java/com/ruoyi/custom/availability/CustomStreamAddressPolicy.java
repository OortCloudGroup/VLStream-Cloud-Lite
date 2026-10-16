package com.ruoyi.custom.availability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure;

/** Camera LAN ranges are configurable. Pin validated DNS results; never follow an unchecked redirect. */
@Component
public class CustomStreamAddressPolicy {
    private final List<String> privateNetworks;
    private final Set<InetAddress> local = new HashSet<>();
    private final ThreadPoolExecutor dns = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new SynchronousQueue<>(), task -> {
        Thread thread = new Thread(task,"custom-source-dns"); thread.setDaemon(true); return thread;
    });
    public CustomStreamAddressPolicy(@Value("${video.custom-availability.allowed-private-networks:10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7}") String networks) {
        privateNetworks = new ArrayList<>();
        for(String network:networks.split(",")){network=network.trim();if(network.isEmpty())continue;if(!validNetwork(network))throw new IllegalArgumentException("Invalid private source network");privateNetworks.add(network);}
        try { Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) local.addAll(Collections.list(interfaces.nextElement().getInetAddresses()));
        } catch (SocketException ignored) { throw new IllegalStateException("Cannot establish source address boundary"); }
    }
    protected InetAddress[] resolve(String host) throws Exception { return InetAddress.getAllByName(host); }
    public InetAddress pin(URI uri, long deadline) {
        if (uri.getHost() == null || uri.getPort() == 0 || uri.getPort() > 65535) throw new ProbeFailure("CONFIGURATION_ERROR");
        Future<InetAddress[]> lookup;
        try { lookup = dns.submit(() -> resolve(uri.getHost())); }
        catch (RejectedExecutionException busy) { throw new ProbeFailure("MONITOR_BUSY"); }
        try {
            InetAddress[] addresses = lookup.get(Math.min(1000, remaining(deadline)),TimeUnit.MILLISECONDS);
            if (addresses.length == 0) throw new ProbeFailure("UNREACHABLE");
            for (InetAddress address : addresses) if (!allowed(address)) throw new ProbeFailure("ADDRESS_BLOCKED");
            return addresses[0];
        } catch (ProbeFailure failure) { throw failure; }
        catch (Exception unavailable) { throw new ProbeFailure("UNREACHABLE"); }
        finally { lookup.cancel(true); }
    }
    public boolean allowed(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
            address.isMulticastAddress() || local.contains(address)) return false;
        for (String blocked : Arrays.asList("0.0.0.0/8","127.0.0.0/8","169.254.0.0/16","224.0.0.0/4","240.0.0.0/4",
            "100.64.0.0/10","192.0.0.0/24","192.0.2.0/24","198.51.100.0/24","203.0.113.0/24","168.63.129.16/32","fd00:ec2::254/128")) if (inNetwork(address,blocked)) return false;
        boolean privateIp = address.isSiteLocalAddress() || inNetwork(address,"fc00::/7");
        return !privateIp || privateNetworks.stream().anyMatch(network -> inNetwork(address,network.trim()));
    }
    private static boolean validNetwork(String network){
        try{String[] parts=network.split("/",-1);if(parts.length!=2||!parts[0].matches("[0-9a-fA-F:.]+")||(!parts[0].contains(":")&&!parts[0].matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")))return false;
            if(!parts[0].contains(":"))for(String octet:parts[0].split("\\."))if(Integer.parseInt(octet)>255)return false;
            byte[] bytes=InetAddress.getByName(parts[0]).getAddress();int prefix=Integer.parseInt(parts[1]);return prefix>=0&&prefix<=bytes.length*8;
        }catch(Exception invalid){return false;}
    }
    static boolean inNetwork(InetAddress address, String network) {
        if(!validNetwork(network))return false;
        try {
            String[] parts = network.split("/",2); byte[] subnet = InetAddress.getByName(parts[0]).getAddress(), bytes = address.getAddress();
            int prefix = Integer.parseInt(parts[1]); if (subnet.length != bytes.length || prefix < 0 || prefix > bytes.length*8) return false;
            for (int i=0;i<bytes.length;i++) { int bits = Math.max(0,Math.min(8,prefix-i*8)), mask = bits == 0 ? 0 : 255 << (8-bits);
                if ((bytes[i]&mask)!=(subnet[i]&mask)) return false; }
            return true;
        } catch (Exception invalid) { return false; }
    }
    public static int remaining(long deadline) {
        long millis = (deadline-System.nanoTime())/1_000_000L;
        if (millis <= 0 || Thread.currentThread().isInterrupted()) throw new ProbeFailure("UNREACHABLE");
        return (int)Math.min(Integer.MAX_VALUE,millis);
    }
    @PreDestroy public void close() { dns.shutdownNow(); }
}
