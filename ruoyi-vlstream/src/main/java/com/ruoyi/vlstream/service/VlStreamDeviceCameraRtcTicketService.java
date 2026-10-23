package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.vlstream.config.VlStreamCameraRtcProperties;
import com.ruoyi.vlstream.config.VlStreamDeviceAccessProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.vlstream.util.VlStreamPlaybackUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Issues one-use, short-lived CameraRTC signaling capabilities bound to an authenticated viewer. */
@Service
public class VlStreamDeviceCameraRtcTicketService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final VlStreamDeviceMapper devices;
    private final VlStreamDeviceStreamMapper streams;
    private final VlStreamDeviceTenantService tenants;
    private final VlStreamDeviceAccessService access;
    private final VlStreamDeviceAccessProperties accessProperties;
    private final VlStreamCameraRtcProperties rtcProperties;

    public VlStreamDeviceCameraRtcTicketService(JdbcTemplate jdbc, VlStreamDeviceMapper devices,
            VlStreamDeviceStreamMapper streams, VlStreamDeviceTenantService tenants,
            VlStreamDeviceAccessService access, VlStreamDeviceAccessProperties accessProperties,
            VlStreamCameraRtcProperties rtcProperties) {
        this.jdbc = jdbc;
        this.devices = devices;
        this.streams = streams;
        this.tenants = tenants;
        this.access = access;
        this.accessProperties = accessProperties;
        this.rtcProperties = rtcProperties;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> issue(String deviceRef, Long streamRowId) {
        if (streamRowId == null) throw new ServiceException("streamId不能为空", 400);
        requireGateway();
        LoginUser holder = requireAuthenticatedHolder();
        Long deviceRowId = access.resolveDeviceRef(deviceRef);
        VlStreamDevice device = devices.selectByIdForUpdate(deviceRowId);
        String tenantId = tenants.currentTenantId();
        Long grantId = access.requireReadAccess(device, tenantId);
        if (!Boolean.TRUE.equals(device.getOnline())) throw new ServiceException("设备离线，不允许播放", 409);
        VlStreamDeviceStream stream = streams.selectById(streamRowId);
        String target = stream == null ? null : VlStreamPlaybackUtils.cameraRtcDeviceId(stream.getProtocol(), stream.getSourceUrl());
        if (stream == null || !deviceRowId.equals(stream.getDeviceRowId()) || !Boolean.TRUE.equals(stream.getAvailable()) || target == null) {
            throw new ServiceException("设备或CameraRTC视频流不存在", 404);
        }

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String ticketHash = holderBoundTicketHash(ticket, holder);
        int ttlSeconds = Math.max(10, Math.min(60, accessProperties.getCameraRtcTicketTtlSeconds()));
        jdbc.update("insert into wvp_vlstream_camera_rtc_ticket(ticket_hash,device_row_id,stream_row_id,viewer_tenant_id,viewer_user_id,grant_id,expires_at,create_time) " +
                "values (?,?,?,?,?,?,TIMESTAMPADD(SECOND,?,NOW()),NOW())",
            ticketHash, deviceRowId, streamRowId, tenantId, access.userId(), grantId, ttlSeconds);
        long leaseSeconds = boundedSessionSeconds(grantId);
        audit(deviceRowId, tenantId, access.userId(), "CAMERA_RTC_TICKET_ISSUED", null,
            "streamRowId=" + streamRowId + ";ticketTtlSeconds=" + ttlSeconds + ";leaseSeconds=" + leaseSeconds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playMode", "cameraRTC");
        result.put("deviceRef", deviceRef);
        result.put("streamId", String.valueOf(streamRowId));
        // The opaque digest includes the verified issuing session; the raw ticket/session is never persisted here.
        result.put("sessionId", ticketHash);
        result.put("webSocketPath", "/vlstream/device/shared/camera-rtc?ticket=" + ticket);
        result.put("ticket", ticket);
        result.put("expiresInSeconds", leaseSeconds);
        result.put("ticketExpiresInSeconds", ttlSeconds);
        return result;
    }

    /** Atomically burns a ticket and rechecks the bound device, stream, tenant and grant. */
    @Transactional(noRollbackFor = ServiceException.class)
    public RelayAuthorization consume(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) throw new ServiceException("信令票据无效或已过期", 403);
        LoginUser holder = requireAuthenticatedHolder();
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("select ticket_hash,device_row_id,stream_row_id,viewer_tenant_id,viewer_user_id,grant_id " +
                    "from wvp_vlstream_camera_rtc_ticket where ticket_hash=? and consumed_at is null and expires_at>NOW() for update",
                holderBoundTicketHash(ticket, holder));
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("信令票据无效或已过期", 403);
        }
        String hash = String.valueOf(row.get("ticket_hash"));
        Long deviceRowId = number(row.get("device_row_id"));
        Long streamRowId = number(row.get("stream_row_id"));
        String viewerTenant = String.valueOf(row.get("viewer_tenant_id"));
        Long viewerUser = number(row.get("viewer_user_id"));
        Long expectedGrant = nullableNumber(row.get("grant_id"));
        if (!viewerTenant.equals(tenants.currentTenantId()) || !Objects.equals(viewerUser, holder.getUserId())) {
            throw new ServiceException("信令票据不属于当前登录会话", 403);
        }
        int burned = jdbc.update("update wvp_vlstream_camera_rtc_ticket set consumed_at=NOW(),expires_at=NOW() " +
            "where ticket_hash=? and consumed_at is null", hash);
        if (burned != 1) throw new ServiceException("信令票据无效或已过期", 403);

        BoundTicketViewer boundViewer = new BoundTicketViewer(deviceRowId, viewerTenant);
        VlStreamDevice device = devices.selectByIdForUpdate(deviceRowId);
        Long currentGrant;
        try {
            currentGrant = access.recheckCameraRtcTicket(device, boundViewer);
        } catch (ServiceException denied) {
            audit(deviceRowId, viewerTenant, viewerUser, "CAMERA_RTC_TICKET_DENIED", null, "authorization changed before handshake");
            throw denied;
        }
        if (!Objects.equals(expectedGrant, currentGrant) || !Boolean.TRUE.equals(device.getOnline())) {
            audit(deviceRowId, viewerTenant, viewerUser, "CAMERA_RTC_TICKET_DENIED", null, "device or grant changed before handshake");
            throw new ServiceException("设备访问权限已变化，请重新申请播放", 403);
        }
        VlStreamDeviceStream stream = streams.selectById(streamRowId);
        String target = stream == null ? null : VlStreamPlaybackUtils.cameraRtcDeviceId(stream.getProtocol(), stream.getSourceUrl());
        if (stream == null || !deviceRowId.equals(stream.getDeviceRowId()) || !Boolean.TRUE.equals(stream.getAvailable()) || target == null) {
            audit(deviceRowId, viewerTenant, viewerUser, "CAMERA_RTC_TICKET_DENIED", null, "stream changed before handshake");
            throw new ServiceException("设备视频流不可用", 409);
        }
        long leaseSeconds = boundedSessionSeconds(currentGrant);
        jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=TIMESTAMPADD(SECOND,?,NOW()) where ticket_hash=? and consumed_at is not null",
            leaseSeconds, hash);
        String deviceRef = jdbc.queryForObject("select access_ref from wvp_vlstream_device_access_ref where device_row_id=?", String.class, deviceRowId);
        audit(deviceRowId, viewerTenant, viewerUser, "CAMERA_RTC_TICKET_CONSUMED", null,
            "streamRowId=" + streamRowId + ";leaseSeconds=" + leaseSeconds);
        return new RelayAuthorization(deviceRowId, streamRowId, deviceRef, target, viewerTenant, viewerUser, currentGrant, hash, boundViewer);
    }

    /** Rechecks access for every relayed message and while a WebSocket is idle. */
    public boolean stillAuthorized(RelayAuthorization authorization) {
        try {
            Integer active = jdbc.queryForObject("select count(*) from wvp_vlstream_camera_rtc_ticket where ticket_hash=? " +
                "and consumed_at is not null and expires_at>NOW()", Integer.class, authorization.sessionId);
            if (active == null || active != 1) return false;
            VlStreamDevice device = devices.selectById(authorization.deviceRowId);
            Long currentGrant = access.recheckCameraRtcTicket(device, authorization.boundViewer);
            if (!Objects.equals(authorization.grantId, currentGrant) || !Boolean.TRUE.equals(device.getOnline())) return false;
            VlStreamDeviceStream stream = streams.selectById(authorization.streamRowId);
            return stream != null && authorization.deviceRowId.equals(stream.getDeviceRowId())
                && Boolean.TRUE.equals(stream.getAvailable())
                && authorization.cameraId.equals(VlStreamPlaybackUtils.cameraRtcDeviceId(stream.getProtocol(), stream.getSourceUrl()));
        } catch (RuntimeException denied) {
            return false;
        }
    }

    public String upstreamWebSocketUrl() {
        String base = rtcProperties.getCameraRtcSignalUrl().trim().replaceAll("/+$", "");
        if (base.isEmpty()) throw new ServiceException("CameraRTC信令服务未配置", 503);
        try {
            URI uri = URI.create(base);
            if (!java.util.Arrays.asList("ws", "wss").contains(uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("invalid gateway");
            }
            return base + "/wswebclient/" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        } catch (IllegalArgumentException invalid) {
            throw new ServiceException("CameraRTC信令服务配置无效", 503);
        }
    }

    @Transactional
    public void recordSessionEnd(RelayAuthorization authorization, String reason) {
        jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=NOW() where ticket_hash=? and consumed_at is not null",
            authorization.sessionId);
        audit(authorization.deviceRowId, authorization.viewerTenantId, authorization.viewerUserId,
            "CAMERA_RTC_SESSION_ENDED", null, reason == null ? "closed" : reason);
    }

    /** Extends an active CameraRTC signaling lease after rechecking viewer, stream and share access. */
    @Transactional(noRollbackFor = ServiceException.class)
    public long renewSession(Long deviceRowId, String sessionId) {
        if (sessionId == null || !sessionId.matches("[a-f0-9]{64}")) throw new ServiceException("预览会话不存在", 404);
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("select device_row_id,stream_row_id,viewer_tenant_id,viewer_user_id,grant_id " +
                "from wvp_vlstream_camera_rtc_ticket where ticket_hash=? and consumed_at is not null and expires_at>NOW() for update", sessionId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("预览会话已结束或不存在", 404);
        }
        if (!deviceRowId.equals(number(row.get("device_row_id")))
                || !access.tenantId().equals(String.valueOf(row.get("viewer_tenant_id")))
                || !access.userId().equals(number(row.get("viewer_user_id")))) {
            throw new ServiceException("预览会话已结束或不属于当前用户", 403);
        }
        Long grantId = nullableNumber(row.get("grant_id"));
        VlStreamDevice device = devices.selectByIdForUpdate(deviceRowId);
        Long currentGrant;
        try {
            currentGrant = access.requireReadAccess(device, access.tenantId());
        } catch (ServiceException denied) {
            jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=NOW() where ticket_hash=?", sessionId);
            throw denied;
        }
        if (!Objects.equals(grantId, currentGrant) || !Boolean.TRUE.equals(device.getOnline())) {
            jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=NOW() where ticket_hash=?", sessionId);
            throw new ServiceException("当前租户没有设备查看权限", 403);
        }
        Long streamRowId = number(row.get("stream_row_id"));
        VlStreamDeviceStream stream = streams.selectById(streamRowId);
        if (stream == null || !deviceRowId.equals(stream.getDeviceRowId()) || !Boolean.TRUE.equals(stream.getAvailable())
                || VlStreamPlaybackUtils.cameraRtcDeviceId(stream.getProtocol(), stream.getSourceUrl()) == null) {
            jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=NOW() where ticket_hash=?", sessionId);
            throw new ServiceException("设备视频流不可用", 409);
        }
        long seconds = boundedSessionSeconds(currentGrant);
        jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=TIMESTAMPADD(SECOND,?,NOW()) where ticket_hash=?",
            seconds, sessionId);
        audit(deviceRowId, access.tenantId(), access.userId(), "CAMERA_RTC_SESSION_RENEWED", grantId,
            "sessionId=" + sessionId + ";leaseSeconds=" + seconds);
        return seconds;
    }

    /** Ends an active or not-yet-consumed session ticket owned by the current viewer. */
    @Transactional(noRollbackFor = ServiceException.class)
    public void closeSession(Long deviceRowId, String sessionId) {
        if (sessionId == null || !sessionId.matches("[a-f0-9]{64}")) throw new ServiceException("预览会话不存在", 404);
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("select device_row_id,viewer_tenant_id,viewer_user_id,grant_id from wvp_vlstream_camera_rtc_ticket " +
                "where ticket_hash=? for update", sessionId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("预览会话不存在", 404);
        }
        if (!deviceRowId.equals(number(row.get("device_row_id")))
                || !access.tenantId().equals(String.valueOf(row.get("viewer_tenant_id")))
                || !access.userId().equals(number(row.get("viewer_user_id")))) {
            throw new ServiceException("预览会话不属于当前用户", 403);
        }
        jdbc.update("update wvp_vlstream_camera_rtc_ticket set expires_at=NOW() where ticket_hash=?", sessionId);
        audit(deviceRowId, access.tenantId(), access.userId(), "CAMERA_RTC_SESSION_CLOSED",
            nullableNumber(row.get("grant_id")), "sessionId=" + sessionId);
    }

    @Scheduled(fixedDelay = 3600000)
    public void purgeExpiredTickets() {
        jdbc.update("delete from wvp_vlstream_camera_rtc_ticket where expires_at<TIMESTAMPADD(DAY,-1,NOW()) " +
            "or consumed_at<TIMESTAMPADD(DAY,-1,NOW())");
    }

    private void requireGateway() { upstreamWebSocketUrl(); }

    private long boundedSessionSeconds(Long grantId) {
        long seconds = Math.max(30L, Math.min(300L, accessProperties.getPreviewLeaseSeconds()));
        if (grantId == null) return seconds;
        Long remaining = jdbc.queryForObject("select timestampdiff(SECOND,NOW(),expires_at) from wvp_vlstream_access_grant " +
            "where grant_id=? and revoked_at is null", Long.class, grantId);
        if (remaining == null || remaining <= 0) throw new ServiceException("设备查看授权已过期", 403);
        return Math.max(1L, Math.min(seconds, remaining));
    }

    private void audit(Long deviceRowId, String tenant, Long userId, String action, Long grantId, String detail) {
        jdbc.update("insert into wvp_vlstream_access_audit(device_row_id,actor_tenant_id,actor_user_id,action,grant_id,detail,created_at) values (?,?,?,?,?,?,NOW())",
            deviceRowId, tenant, userId, action, grantId, detail == null || detail.length() <= 1000 ? detail : detail.substring(0, 1000));
    }

    private LoginUser requireAuthenticatedHolder() {
        Authentication authentication = SecurityUtils.getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new ServiceException("信令票据需要当前有效登录会话", 403);
        }
        LoginUser holder = (LoginUser) authentication.getPrincipal();
        if (holder.getUserId() == null || holder.getToken() == null || holder.getToken().trim().isEmpty()) {
            throw new ServiceException("信令票据需要当前有效登录会话", 403);
        }
        return holder;
    }

    private String holderBoundTicketHash(String ticket, LoginUser holder) {
        String tenant = tenants.currentTenantId();
        String session = holder.getToken().trim();
        if (holder.isFederated() && session.regionMatches(true, 0, "Bearer ", 0, 7)) session = session.substring(7).trim();
        if (session.isEmpty()) throw new ServiceException("信令票据需要当前有效登录会话", 403);
        // Native token is the trusted Redis login UUID; federation token is verified by VLS.
        // Length-prefix tenant and domain-separate both hashes. Never use mutable login/expiry times or permissions.
        String binding = digest("rtc-holder-v1\n" + tenant.length() + "\n" + tenant + "\n" + holder.getUserId()
            + "\n" + holder.isFederated() + "\n" + digest(session));
        return digest("rtc-ticket-v2\n" + ticket + "\n" + binding);
    }

    private static String digest(String ticket) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
    private static Long nullableNumber(Object value) { return value == null ? null : number(value); }

    /** Not constructible by controllers or clients: minted only after a persisted ticket is atomically burned. */
    static final class BoundTicketViewer {
        private final Long deviceRowId;
        private final String tenantId;

        private BoundTicketViewer(Long deviceRowId, String tenantId) {
            this.deviceRowId = deviceRowId;
            this.tenantId = tenantId;
        }

        Long deviceRowId() { return deviceRowId; }
        String tenantId() { return tenantId; }
    }

    public static final class RelayAuthorization {
        private final Long deviceRowId;
        private final Long streamRowId;
        private final String deviceRef;
        private final String cameraId;
        private final String viewerTenantId;
        private final Long viewerUserId;
        private final Long grantId;
        private final String sessionId;
        private final BoundTicketViewer boundViewer;

        private RelayAuthorization(Long deviceRowId, Long streamRowId, String deviceRef, String cameraId,
                String viewerTenantId, Long viewerUserId, Long grantId, String sessionId, BoundTicketViewer boundViewer) {
            this.deviceRowId = deviceRowId;
            this.streamRowId = streamRowId;
            this.deviceRef = deviceRef;
            this.cameraId = cameraId;
            this.viewerTenantId = viewerTenantId;
            this.viewerUserId = viewerUserId;
            this.grantId = grantId;
            this.sessionId = sessionId;
            this.boundViewer = boundViewer;
        }

        public Long getDeviceRowId() { return deviceRowId; }
        public Long getStreamRowId() { return streamRowId; }
        public String getDeviceRef() { return deviceRef; }
        public String getCameraId() { return cameraId; }
        public String getViewerTenantId() { return viewerTenantId; }
        public Long getViewerUserId() { return viewerUserId; }
        public Long getGrantId() { return grantId; }
        public String getSessionId() { return sessionId; }
    }
}
