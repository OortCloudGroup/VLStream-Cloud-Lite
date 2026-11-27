package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class VlStreamDeviceClaimService {
    private final JdbcTemplate jdbc;
    private final VlStreamDeviceTenantService tenants;
    private final VlStreamDeviceMapper devices;
    private final VlStreamDeviceAccessRefService refs;
    private final ApplicationEventPublisher events;
    private final String signingKey;

    public VlStreamDeviceClaimService(JdbcTemplate jdbc, VlStreamDeviceTenantService tenants,
            VlStreamDeviceMapper devices, VlStreamDeviceAccessRefService refs, ApplicationEventPublisher events,
            @Value("${vlstream.device.claim-secret:${VLSTREAM_DEVICE_CLAIM_SECRET:}}") String signingKey) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.devices = devices;
        this.refs = refs;
        this.events = events;
        this.signingKey = signingKey;
    }

    @Transactional
    public String claim(String deviceId, String proof) {
        String tenant = tenants.currentTenantId();
        verify(deviceId, proof);
        // Acquire the same unique device row used by MQTT, including concurrent first registration.
        jdbc.update("insert into wvp_vlstream_device(device_id, tenant_id, online, create_time, update_time) " +
                "values(?,?,0,now(),now()) on duplicate key update id=id", deviceId, tenant);
        VlStreamDevice row = devices.selectByDeviceIdForUpdate(deviceId);
        if (!deviceId.equals(row.getDeviceId())) throw conflict();
        String owner = tenants.deviceTenant(row);
        boolean sameTenant = tenant.equals(owner);
        if (!sameTenant && !tenants.defaultTenant().equals(owner)) throw conflict();
        Long id = row.getId();
        boolean alreadyClaimed = Boolean.TRUE.equals(jdbc.queryForObject(
                "select claimed_at is not null from wvp_vlstream_device where id=?", Boolean.class, id));
        if (alreadyClaimed) {
            if (!sameTenant) throw conflict();
            return id.toString();
        }
        if (!sameTenant) clearPreviousSharing(id);
        jdbc.update("update wvp_vlstream_device set tenant_id=?,claimed_at=now(),claimed_by=?,update_time=now() where id=?",
                tenant, SecurityUtils.getUserId(), id);
        refs.ensureRef(id);
        return id.toString();
    }

    private void clearPreviousSharing(Long id) {
        jdbc.update("update wvp_vlstream_device set visibility='PRIVATE' where id=?", id);
        jdbc.update("update wvp_vlstream_access_grant set revoked_at=now() where device_row_id=? and revoked_at is null", id);
        jdbc.update("update wvp_vlstream_access_request set status='REJECTED',decided_at=now(),updated_at=now() " +
                "where device_row_id=? and status='PENDING'", id);
        jdbc.update("update wvp_cloud_ai_source_grant set revoked=1 where protocol='VLSTREAM' and device_row_id=?", id);
        jdbc.update("delete from wvp_device_category_relation where protocol_type='VLSTREAM' and device_key=?", id.toString());
        jdbc.update("update wvp_vlstream_camera_rtc_ticket set consumed_at=now() where device_row_id=? and consumed_at is null", id);
        events.publishEvent(new VlStreamDeviceAccessChangedEvent(id, null));
    }

    private ServiceException conflict() {
        return new ServiceException("该设备已有归属，无法认领", 409);
    }

    void verify(String deviceId, String proof) {
        if (deviceId == null || !deviceId.matches("[A-Za-z0-9_-]{1,100}") ||
                proof == null || !proof.matches("[A-Za-z0-9_-]{43}")) {
            throw new ServiceException("设备认领码格式无效", 400);
        }
        byte[] key = signingKey.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new ServiceException("设备认领签名密钥尚未配置", 503);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = Base64.getUrlEncoder().withoutPadding().encode(mac.doFinal(
                    ("vlstream-claim-v1\n" + deviceId).getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected, proof.getBytes(StandardCharsets.US_ASCII)))
                throw new ServiceException("设备认领凭据无效", 403);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("设备认领签名校验不可用", e);
        }
    }
}
