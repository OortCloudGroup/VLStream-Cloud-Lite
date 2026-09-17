package com.ruoyi.isup.ehome;

import com.ruoyi.isup.mapper.EhomeDeviceMapper;
import com.ruoyi.isup.service.cmsService.HCISUPCMS;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Registration response adapted from ruoyi-qs-nvr; see docs/ehome-integration.md. */
@Service
@Slf4j
public class EhomeRegistration {
    private final EhomeDeviceMapper mapper;
    private final EhomeConfig config;
    private final ConcurrentMap<Integer, String> users = new ConcurrentHashMap<>();

    public EhomeRegistration(EhomeDeviceMapper mapper, EhomeConfig config) {
        this.mapper = mapper;
        this.config = config;
    }

    public synchronized boolean online(int loginId, Pointer input, int length, Pointer reply, int capacity) {
        if (!config.isEnabled()) return false;
        if (reply == null) return false;
        HCISUPCMS.NET_EHOME_SERVER_INFO server = new HCISUPCMS.NET_EHOME_SERVER_INFO();
        if (capacity < server.size()) return false;
        server.dwSize = server.size();
        server.dwKeepAliveSec = 15;
        server.dwTimeOutCount = 6;
        HCISUPCMS.NET_EHOME_SERVER_INFO_V50 serverV50 = new HCISUPCMS.NET_EHOME_SERVER_INFO_V50();
        if (capacity >= serverV50.size()) {
            serverV50.dwSize = serverV50.size();
            serverV50.dwKeepAliveSec = 15;
            serverV50.dwTimeOutCount = 6;
            serverV50.write();
            reply.write(0, serverV50.getPointer().getByteArray(0, serverV50.size()), 0, serverV50.size());
        } else {
            server.write();
            reply.write(0, server.getPointer().getByteArray(0, server.size()), 0, server.size());
        }

        return register(loginId, input, length);
    }

    public synchronized boolean register(int loginId, Pointer input, int length) {
        if (!config.isEnabled()) return false;
        HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12 info = new HCISUPCMS.NET_EHOME_DEV_REG_INFO_V12();
        info.write();
        if (input == null || length < info.struRegInfo.size()) return false;
        int size = Math.min(length, info.size());
        info.getPointer().write(0, input.getByteArray(0, size), 0, size);
        info.read();
        String version = EhomeNative.version(info.struRegInfo.byDevProtocolVersion);
        if (!EhomeNative.legacy(version)) {
            log.warn("EHome 监听拒绝未知协议或 ISUP 5 设备，协议版本：{}", version);
            return false;
        }
        EhomeDevice device = new EhomeDevice();
        device.setDeviceId(EhomeNative.text(info.struRegInfo.byDeviceID));
        if (device.getDeviceId().isEmpty()) return false;
        device.setId(mapper.findRegisteredId(device.getDeviceId()));
        device.setName(EhomeNative.text(info.sDevName));
        if (device.getName().isEmpty()) device.setName(device.getDeviceId());
        if (device.getName().length() > 64) device.setName(device.getName().substring(0, 64));
        device.setDeptId(config.getDefaultDeptId());
        device.setDeviceSerial(EhomeNative.text(info.struRegInfo.sDeviceSerial));
        device.setIpAddress(EhomeNative.text(info.struRegInfo.struDevAdd.szIP));
        device.setFirmwareVersion(EhomeNative.text(info.struRegInfo.byFirmwareVersion));
        device.setDevProtocolVersion(EhomeNative.version(info.struRegInfo.byDevProtocolVersion));
        device.setLuserId(loginId);
        if (device.getId() == null) mapper.insertRegistration(device);
        else mapper.updateRegistration(device);
        users.entrySet().removeIf(entry -> entry.getValue().equals(device.getDeviceId()) && entry.getKey() != loginId);
        users.put(loginId, device.getDeviceId());
        log.info("EHome 设备上线: {}, 协议 {}", device.getDeviceId(), device.getDevProtocolVersion());
        return true;
    }

    public boolean owns(int loginId) { return users.containsKey(loginId); }
    public static boolean isLegacy(Pointer input, int length) {
        HCISUPCMS.NET_EHOME_DEV_REG_INFO info = new HCISUPCMS.NET_EHOME_DEV_REG_INFO();
        if (input == null || length < info.size()) return false;
        info.getPointer().write(0, input.getByteArray(0, info.size()), 0, info.size());
        info.read();
        return EhomeNative.legacy(EhomeNative.version(info.byDevProtocolVersion));
    }
    public void offline(int loginId) { mapper.markOffline(loginId); users.remove(loginId); }
    public void resetOnlineState() { mapper.markAllOffline(); users.clear(); }
}
