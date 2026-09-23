package com.ruoyi.vlstream.service;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.VlStreamTenantContext;
import com.ruoyi.vlstream.config.VlStreamDeviceProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/** Uses verified user identity for access and provisioned device proof for first ownership. */
@Service
public class VlStreamDeviceTenantService {
    private final VlStreamDeviceProperties properties;
    private final VlStreamDeviceMapper devices;

    public VlStreamDeviceTenantService(VlStreamDeviceProperties properties, VlStreamDeviceMapper devices) {
        this.properties = properties;
        this.devices = devices;
    }

    public String defaultTenant() {
        String tenant = StringUtils.trimToEmpty(properties.getDefaultTenantId());
        if (!tenant.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ServiceException("VLStream设备默认租户配置无效", 503);
        }
        return tenant;
    }

    public String deviceTenant(VlStreamDevice device) {
        return StringUtils.defaultIfBlank(device.getTenantId(), defaultTenant());
    }

    public String resolveReportedTenant(JSONObject message, VlStreamDevice existing) {
        Object rawTenant = message.get("tenantId");
        String reported = rawTenant instanceof String ? ((String) rawTenant).trim() : "";
        if (rawTenant != null && !(rawTenant instanceof String)) {
            throw new ServiceException("tenantId必须是字符串", 400);
        }
        if (reported.isEmpty()) {
            return existing == null ? defaultTenant() : deviceTenant(existing);
        }
        if (!reported.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ServiceException("tenantId格式无效", 400);
        }
        if (existing != null && !reported.equals(deviceTenant(existing))) {
            throw new ServiceException("设备已归属其他租户，心跳不能变更归属", 403);
        }
        verifyBinding(message.getString("deviceId"), reported, message.getString("tenantBindingProof"));
        return reported;
    }

    private void verifyBinding(String deviceId, String tenantId, String proof) {
        String secret = properties.getTenantBindingSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new ServiceException("平台未配置设备租户绑定校验密钥", 503);
        }
        if (deviceId == null || !deviceId.matches("[A-Za-z0-9_-]{1,100}")
                || proof == null || !proof.matches("v1\\.[A-Za-z0-9_-]{43}")) {
            throw new ServiceException("缺少或无效的设备租户绑定凭据", 403);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(("v1\n" + deviceId + "\n" + tenantId).getBytes(StandardCharsets.UTF_8));
            byte[] supplied = Base64.getUrlDecoder().decode(proof.substring(3));
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new ServiceException("设备租户绑定凭据校验失败", 403);
            }
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new ServiceException("设备租户绑定凭据校验失败", 403);
        }
    }

    public void scopeQuery(VlStreamDevice query) {
        query.setTenantId(currentTenant());
        query.setLegacyTenantId(defaultTenant());
    }

    public VlStreamDevice requireDevice(Long deviceId) {
        VlStreamDevice device = devices.selectById(deviceId);
        if (device == null || !currentTenant().equals(deviceTenant(device))) {
            throw new ServiceException("设备不存在或不属于当前租户", 403);
        }
        return device;
    }

    private String currentTenant() {
        return VlStreamTenantContext.currentTenant(defaultTenant());
    }
}
