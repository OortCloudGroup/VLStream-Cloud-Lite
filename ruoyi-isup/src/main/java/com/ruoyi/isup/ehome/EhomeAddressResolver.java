package com.ruoyi.isup.ehome;

import com.ruoyi.common.exception.ServiceException;
import java.net.*;
import java.util.*;
import java.util.function.Function;

/** Ask the OS routing table for the source address; UDP connect sends no payload. */
public final class EhomeAddressResolver {
    private EhomeAddressResolver() { }

    public static String resolve(String override, String deviceIp) {
        return resolve(override, deviceIp, peer -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName(peer), 7660);
                return socket.getLocalAddress();
            } catch (Exception e) { throw new ServiceException("无法确定通往设备的本机地址，请检查网络或配置 ehome.public-host"); }
        });
    }

    static String resolve(String override, String deviceIp, Function<String, InetAddress> route) {
        try {
            if (override != null && !override.trim().isEmpty()) {
                for (InetAddress address : InetAddress.getAllByName(override.trim())) {
                    if (usable(address)) return address.getHostAddress();
                }
                throw new ServiceException("ehome.public-host 必须为设备可达的 IPv4 地址或域名，不能使用回环或通配地址");
            }
            if (deviceIp == null || deviceIp.trim().isEmpty()) throw new ServiceException("设备未上报 IP，无法自动选择取流地址");
            InetAddress peer = InetAddress.getByName(deviceIp.trim());
            if (!usable(peer)) throw new ServiceException("设备 IP 不是有效的远程 IPv4 地址");
            InetAddress local = route.apply(peer.getHostAddress());
            if (!usable(local)) throw new ServiceException("没有找到设备可达的本机 IPv4 地址，请配置 ehome.public-host");
            return local.getHostAddress();
        } catch (UnknownHostException e) { throw new ServiceException("无法解析设备或取流服务器地址"); }
    }

    private static boolean usable(InetAddress address) {
        return address instanceof Inet4Address && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress() && !address.isMulticastAddress() && !address.isLinkLocalAddress();
    }

    public static List<String> localAddresses() {
        Set<String> addresses = new TreeSet<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (usable(address)) addresses.add(address.getHostAddress());
                }
            }
        } catch (SocketException ignored) { }
        return new ArrayList<>(addresses);
    }
}
