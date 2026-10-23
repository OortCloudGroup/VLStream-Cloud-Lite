package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.custom.availability.CustomStreamProbe;
import com.ruoyi.custom.availability.CustomStreamSpec;
import com.ruoyi.custom.domain.CustomDevice;
import com.ruoyi.vlstream.config.VlStreamDeviceAccessProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.domain.VlStreamDeviceStream;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import com.ruoyi.vlstream.mapper.VlStreamDeviceStreamMapper;
import com.ruoyi.vlstream.util.VlStreamPlaybackUtils;
import com.ruoyi.wvp.common.StreamInfo;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import com.ruoyi.wvp.vmanager.bean.StreamContent;
import com.ruoyi.wvp.vmanager.bean.WVPResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-viewer temporary proxies. Every start/renewal rechecks tenant visibility and grant expiry. */
@Service
public class VlStreamDevicePreviewLeaseService {
    private final JdbcTemplate jdbc;
    private final VlStreamDeviceMapper devices;
    private final VlStreamDeviceStreamMapper streams;
    private final VlStreamDeviceTenantService tenants;
    private final VlStreamDeviceAccessRefService refs;
    private final VlStreamDeviceAccessService access;
    private final VlStreamDeviceCameraRtcTicketService cameraRtcTickets;
    private final VlStreamDeviceAccessProperties properties;
    private final IMediaServerService media;
    private final CustomStreamProbe sourceProbe;
    private final TransactionTemplate transactions;
    private final TransactionTemplate lifecycleTransactions;

    public VlStreamDevicePreviewLeaseService(JdbcTemplate jdbc, VlStreamDeviceMapper devices,
            VlStreamDeviceStreamMapper streams, VlStreamDeviceTenantService tenants,
            VlStreamDeviceAccessRefService refs, VlStreamDeviceAccessService access,
            VlStreamDeviceCameraRtcTicketService cameraRtcTickets, VlStreamDeviceAccessProperties properties, IMediaServerService media,
            CustomStreamProbe sourceProbe, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.devices = devices;
        this.streams = streams;
        this.tenants = tenants;
        this.refs = refs;
        this.access = access;
        this.cameraRtcTickets = cameraRtcTickets;
        this.properties = properties;
        this.media = media;
        this.sourceProbe = sourceProbe;
        this.transactions = new TransactionTemplate(transactionManager);
        this.lifecycleTransactions = new TransactionTemplate(transactionManager);
        this.lifecycleTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> streams(String deviceRef) {
        Long deviceRowId = refs.resolve(deviceRef);
        requireEntitled(deviceRowId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (VlStreamDeviceStream stream : streams.selectAvailableByDeviceId(deviceRowId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(stream.getId()));
            item.put("channelId", stream.getChannelId());
            item.put("streamName", stream.getStreamName());
            item.put("streamType", stream.getStreamType());
            item.put("protocol", stream.getProtocol());
            item.put("defaultStream", stream.getDefaultStream());
            item.put("available", stream.getAvailable());
            item.put("lastReportTime", stream.getLastReportTime());
            result.add(item);
        }
        return result;
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> start(String deviceRef, Long streamId) {
        if (streamId == null) throw new ServiceException("streamId不能为空", 400);
        Long deviceRowId = refs.resolve(deviceRef);
        VlStreamDevice device = requireEntitled(deviceRowId);
        if (!Boolean.TRUE.equals(device.getOnline())) throw new ServiceException("设备离线，不允许播放", 409);
        Long grantId = entitlementGrantId(device);
        VlStreamDeviceStream stream = streams.selectById(streamId);
        if (stream == null || !deviceRowId.equals(stream.getDeviceRowId()) || !Boolean.TRUE.equals(stream.getAvailable())) {
            throw new ServiceException("设备或视频流不存在", 404);
        }
        if (VlStreamPlaybackUtils.isCameraRtc(stream.getProtocol(), stream.getSourceUrl())) {
            // The returned capability is one-use and the browser reaches CameraRTC only through WVP.
            return cameraRtcTickets.issue(deviceRef, streamId);
        }
        String sourceUrl = validatedProxyUrl(stream.getSourceUrl());
        MediaServer configured = media.getDefaultMediaServer();
        MediaServer server = configured == null ? null : media.getOne(configured.getId());
        if (server == null || !server.isStatus()) throw new ServiceException("没有可用的ZLM媒体服务器", 503);
        String leaseId = UUID.randomUUID().toString().replace("-", "");
        String app = "vlstream_share";
        String proxyStream = "share_" + leaseId;
        // Commit the cleanup identity before the external call. ZLM uses vhost/app/stream as
        // its proxy key, so a lost response or process interruption is still recoverable.
        reserveCreation(deviceRowId, streamId, stream.getSourceUrl(), leaseId, app, proxyStream, server.getId(), grantId);
        boolean creationAcknowledged = false;
        try {
            WVPResult<String> proxy = media.addStreamProxy(server, app, proxyStream,
                sourceUrl, true, false, "0", 15);
            if (proxy == null || proxy.getCode() != 0 || proxy.getData() == null || proxy.getData().trim().isEmpty()) {
                throw new ServiceException("ZLM拉流失败，请检查设备流状态", 502);
            }
            creationAcknowledged = true;
            lifecycleTransactions.execute(status -> {
                int updated = jdbc.update("update wvp_vlstream_preview_lease set proxy_key=?,proxy_deleted=0,update_time=NOW() where lease_id=?",
                    proxy.getData(), leaseId);
                if (updated != 1) throw new ServiceException("预览清理记录不存在，请重试", 503);
                return null;
            });
            StreamInfo streamInfo = media.getStreamInfoByAppAndStream(server, app, proxyStream, null, null);
            if (streamInfo == null) throw new ServiceException("ZLM已接受拉流请求，但尚未生成播放地址", 502);
            Map<String, Object> safeInfo = safeStreamInfo(new StreamContent(streamInfo));
            if (safeInfo.isEmpty()) throw new ServiceException("ZLM未配置可供当前网络使用的安全播放地址", 503);
            long seconds = activateLeaseIfStillAuthorized(deviceRowId, streamId, stream.getSourceUrl(), leaseId, grantId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", leaseId);
            result.put("playMode", "ZLM");
            result.put("expiresInSeconds", seconds);
            result.put("streamInfo", safeInfo);
            return result;
        } catch (RuntimeException failure) {
            try {
                final boolean completedCreation = creationAcknowledged;
                lifecycleTransactions.execute(status -> {
                    if (completedCreation) {
                        jdbc.update("update wvp_vlstream_preview_lease set state='FAILED',expires_at=NOW(),update_time=NOW() where lease_id=? and state<>'ACTIVE'", leaseId);
                    } else {
                        // A timeout may occur while ZLM is still creating the resource. Keep
                        // the creation deadline so an early 'absent' reply cannot end cleanup.
                        jdbc.update("update wvp_vlstream_preview_lease set state='CANCEL_REQUESTED',update_time=NOW() where lease_id=? and state='CREATING'", leaseId);
                    }
                    return null;
                });
                deleteProxy(leaseId);
            } catch (RuntimeException cleanupUnavailable) {
                // The independently committed reservation survives even a database outage.
                // Its deadline lets the scheduler retry cleanup after recovery.
            }
            throw failure;
        }
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> renew(String deviceRef, String leaseId) {
        Long deviceRowId = refs.resolve(deviceRef);
        if (leaseId != null && leaseId.matches("[a-f0-9]{64}")) {
            long seconds = cameraRtcTickets.renewSession(deviceRowId, leaseId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", leaseId);
            result.put("expiresInSeconds", seconds);
            return result;
        }
        Map<String, Object> lease = loadViewerLease(leaseId, deviceRowId, true);
        long seconds;
        try {
            seconds = transactions.execute(status -> renewIfStillAuthorized(deviceRowId, leaseId, lease));
        } catch (RuntimeException denied) {
            terminate(leaseId, "REVOKED");
            throw denied;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", leaseId);
        result.put("expiresInSeconds", seconds);
        return result;
    }

    @PreAuthorize("isAuthenticated()")
    public void close(String deviceRef, String leaseId) {
        Long deviceRowId = refs.resolve(deviceRef);
        if (leaseId != null && leaseId.matches("[a-f0-9]{64}")) {
            cameraRtcTickets.closeSession(deviceRowId, leaseId);
            return;
        }
        loadViewerLease(leaseId, deviceRowId, false);
        terminate(leaseId, "CLOSED");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessChanged(VlStreamDeviceAccessChangedEvent event) {
        List<String> leaseIds = lifecycleTransactions.execute(status -> event.getGrantId() == null
            ? jdbc.queryForList("select lease_id from wvp_vlstream_preview_lease where device_row_id=? and state in ('ACTIVE','CREATING')",
                String.class, event.getDeviceRowId())
            : jdbc.queryForList("select lease_id from wvp_vlstream_preview_lease where device_row_id=? and grant_id=? and state in ('ACTIVE','CREATING')",
                String.class, event.getDeviceRowId(), event.getGrantId()));
        for (String leaseId : leaseIds) terminate(leaseId, "REVOKED");
    }

    @Scheduled(fixedDelay = 1000)
    public void expireAndRetryCleanup() {
        jdbc.update("update wvp_vlstream_preview_lease set state='FAILED',update_time=NOW() " +
            "where state in ('CREATING','CANCEL_REQUESTED') and expires_at<=NOW()");
        List<String> expired = jdbc.queryForList("select l.lease_id from wvp_vlstream_preview_lease l " +
                "left join wvp_vlstream_device v on v.id=l.device_row_id where l.state='ACTIVE' and (l.expires_at<=NOW() " +
                "or v.id is null or not ((case when v.tenant_id is null or TRIM(v.tenant_id)='' then ? else v.tenant_id end)=l.viewer_tenant_id or v.visibility='PUBLIC' " +
                "or exists (select 1 from wvp_vlstream_access_grant g where g.device_row_id=l.device_row_id " +
                "and g.viewer_tenant_id=l.viewer_tenant_id and g.revoked_at is null and g.expires_at>NOW())))",
            String.class, tenants.defaultTenant());
        for (String leaseId : expired) terminate(leaseId, "EXPIRED");
        List<String> cleanup = jdbc.queryForList("select lease_id from wvp_vlstream_preview_lease where state not in ('ACTIVE','CREATING','CANCEL_REQUESTED') " +
                "and proxy_deleted=0 order by COALESCE(update_time,create_time),lease_id limit 200",
            String.class);
        for (String leaseId : cleanup) deleteProxy(leaseId);
    }

    private VlStreamDevice requireEntitled(Long deviceRowId) {
        VlStreamDevice device = devices.selectById(deviceRowId);
        access.requireReadAccess(device, tenants.currentTenantId());
        return device;
    }

    private void reserveCreation(Long deviceRowId, Long streamId, String sourceUrl, String leaseId,
            String app, String proxyStream, String mediaServerId, Long grantId) {
        lifecycleTransactions.execute(status -> {
            jdbc.update("insert into wvp_vlstream_preview_lease(lease_id,device_row_id,stream_row_id,viewer_tenant_id,viewer_user_id,grant_id,media_server_id,proxy_app,proxy_stream,proxy_key,source_fingerprint,state,expires_at,proxy_deleted,create_time,update_time) " +
                    "values (?,?,?,?,?,?,?,?,?,?,?,'CREATING',TIMESTAMPADD(SECOND,120,NOW()),0,NOW(),NOW())",
                leaseId, deviceRowId, streamId, access.tenantId(), access.userId(), grantId, mediaServerId, app,
                proxyStream, "__defaultVhost__/" + app + "/" + proxyStream, fingerprint(sourceUrl));
            return null;
        });
    }

    private long activateLeaseIfStillAuthorized(Long deviceRowId, Long streamId, String sourceUrl, String leaseId,
            Long expectedGrantId) {
        Long seconds = transactions.execute(status -> {
            VlStreamDevice locked = devices.selectByIdForUpdate(deviceRowId);
            if (locked == null || !Boolean.TRUE.equals(locked.getOnline())) {
                throw new ServiceException("设备不可用或无权访问", 409);
            }
            Long currentGrantId = entitlementGrantId(locked);
            if (!Objects.equals(expectedGrantId, currentGrantId)) {
                throw new ServiceException("设备共享授权已变化，请重新播放", 403);
            }
            VlStreamDeviceStream currentStream = streams.selectById(streamId);
            if (currentStream == null || !deviceRowId.equals(currentStream.getDeviceRowId())
                    || !Boolean.TRUE.equals(currentStream.getAvailable())
                    || !fingerprint(sourceUrl).equals(fingerprint(currentStream.getSourceUrl()))) {
                throw new ServiceException("设备流已变更或不可用，请重新播放", 409);
            }
            long leaseSeconds = boundedLeaseSeconds(currentGrantId);
            int activated = jdbc.update("update wvp_vlstream_preview_lease set state='ACTIVE',expires_at=TIMESTAMPADD(SECOND,?,NOW()),update_time=NOW() " +
                    "where lease_id=? and state='CREATING' and expires_at>NOW() and proxy_deleted=0",
                leaseSeconds, leaseId);
            if (activated != 1) throw new ServiceException("预览创建已取消或过期，请重新播放", 409);
            return leaseSeconds;
        });
        return seconds == null ? leaseSeconds() : seconds;
    }

    private long renewIfStillAuthorized(Long deviceRowId, String leaseId, Map<String, Object> lease) {
        VlStreamDevice locked = devices.selectByIdForUpdate(deviceRowId);
        if (locked == null) throw new ServiceException("设备不存在或无权访问", 403);
        Long currentGrantId = entitlementGrantId(locked);
        if (!Objects.equals(nullableLong(lease.get("grant_id")), currentGrantId)) {
            throw new ServiceException("当前租户没有设备查看权限", 403);
        }
        VlStreamDeviceStream stream = streams.selectById(asLong(lease.get("stream_row_id")));
        if (!Boolean.TRUE.equals(locked.getOnline()) || stream == null || !Boolean.TRUE.equals(stream.getAvailable())
                || !deviceRowId.equals(stream.getDeviceRowId())
                || !fingerprint(stream.getSourceUrl()).equals(String.valueOf(lease.get("source_fingerprint")))) {
            throw new ServiceException("设备流已变更或不可用，请重新播放", 409);
        }
        long seconds = boundedLeaseSeconds(currentGrantId);
        int updated = jdbc.update("update wvp_vlstream_preview_lease set expires_at=TIMESTAMPADD(SECOND,?,NOW()),update_time=NOW() " +
                "where lease_id=? and device_row_id=? and viewer_tenant_id=? and viewer_user_id=? and state='ACTIVE' and expires_at>NOW()",
            seconds, leaseId, deviceRowId, access.tenantId(), access.userId());
        if (updated == 0) throw new ServiceException("预览会话已结束，请重新播放", 409);
        return seconds;
    }

    private long boundedLeaseSeconds(Long grantId) {
        long seconds = leaseSeconds();
        if (grantId == null) return seconds;
        Long remaining = jdbc.queryForObject("select timestampdiff(SECOND,NOW(),expires_at) from wvp_vlstream_access_grant where grant_id=? and revoked_at is null", Long.class, grantId);
        if (remaining == null || remaining <= 0) throw new ServiceException("设备查看授权已过期", 403);
        return Math.max(1L, Math.min(seconds, remaining));
    }

    private Long entitlementGrantId(VlStreamDevice device) {
        return access.requireReadAccess(device, tenants.currentTenantId());
    }

    private Map<String, Object> loadViewerLease(String leaseId, Long deviceRowId, boolean requireUnexpired) {
        if (leaseId == null || !leaseId.matches("[a-f0-9]{32}")) throw new ServiceException("预览会话不存在", 404);
        String sql = "select * from wvp_vlstream_preview_lease where lease_id=? and device_row_id=? and viewer_tenant_id=? and viewer_user_id=? and state='ACTIVE'" +
            (requireUnexpired ? " and expires_at>NOW()" : "");
        try {
            return jdbc.queryForMap(sql, leaseId, deviceRowId, access.tenantId(), access.userId());
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("预览会话已结束或不属于当前用户", 403);
        }
    }

    private void terminate(String leaseId, String state) {
        // Do not confirm deletion before an in-flight add request finishes. Its response
        // either completes cleanup, or the persisted creation deadline triggers recovery.
        lifecycleTransactions.execute(status -> {
            jdbc.update("update wvp_vlstream_preview_lease set state='CANCEL_REQUESTED',update_time=NOW() where lease_id=? and state='CREATING'", leaseId);
            jdbc.update("update wvp_vlstream_preview_lease set state=?,expires_at=NOW(),update_time=NOW() where lease_id=? and state='ACTIVE'",
                state, leaseId);
            return null;
        });
        deleteProxy(leaseId);
    }

    private void deleteProxy(String leaseId) {
        List<Map<String, Object>> rows = lifecycleTransactions.execute(status -> jdbc.queryForList(
            "select media_server_id,proxy_key from wvp_vlstream_preview_lease " +
            "where lease_id=? and proxy_deleted=0 and state not in ('ACTIVE','CREATING','CANCEL_REQUESTED')", leaseId));
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.get(0);
        String proxyKey = String.valueOf(row.get("proxy_key"));
        try {
            MediaServer server = media.getOne(String.valueOf(row.get("media_server_id")));
            if (server != null && Boolean.TRUE.equals(media.delStreamProxy(server, proxyKey))) {
                lifecycleTransactions.execute(status -> {
                    jdbc.update("update wvp_vlstream_preview_lease set proxy_deleted=1,update_time=NOW() " +
                            "where lease_id=? and proxy_key=? and state not in ('ACTIVE','CREATING','CANCEL_REQUESTED')", leaseId, proxyKey);
                    return null;
                });
            }
        } catch (RuntimeException ignored) {
            // The terminal lease cannot renew; scheduled cleanup retries proxy deletion.
        } finally {
            lifecycleTransactions.execute(status -> {
                // Failed attempts move to the back of the queue, including unavailable servers.
                jdbc.update("update wvp_vlstream_preview_lease set update_time=NOW() where lease_id=? and proxy_key=? " +
                    "and proxy_deleted=0 and state not in ('ACTIVE','CREATING','CANCEL_REQUESTED')", leaseId, proxyKey);
                return null;
            });
        }
    }

    private long leaseSeconds() { return Math.max(30L, Math.min(300L, properties.getPreviewLeaseSeconds())); }

    private String validatedProxyUrl(String sourceUrl) {
        try {
            CustomDevice temporary = new CustomDevice();
            temporary.setStreamUrl(sourceUrl);
            CustomStreamSpec spec = new CustomStreamSpec(temporary);
            if (!"RTSP".equals(spec.kind) && !"RTMP".equals(spec.kind)) throw new ServiceException("共享预览仅支持安全校验后的RTSP/RTMP流", 409);
            return sourceProbe.proxyUrl(spec, 3000);
        } catch (CustomStreamSpec.ProbeFailure invalid) {
            // The shared proxy must use a pinned, policy-allowed DNS result to avoid SSRF/rebinding.
            throw new ServiceException("视频源地址未通过网络安全校验", 409);
        }
    }

    private Map<String, Object> safeStreamInfo(StreamContent stream) {
        Map<String, Object> safe = new LinkedHashMap<>();
        putUrl(safe, "flv", stream.getFlv());
        putUrl(safe, "https_flv", stream.getHttps_flv());
        putUrl(safe, "ws_flv", stream.getWs_flv());
        putUrl(safe, "wss_flv", stream.getWss_flv());
        putUrl(safe, "fmp4", stream.getFmp4());
        putUrl(safe, "https_fmp4", stream.getHttps_fmp4());
        putUrl(safe, "ws_fmp4", stream.getWs_fmp4());
        putUrl(safe, "wss_fmp4", stream.getWss_fmp4());
        putUrl(safe, "hls", stream.getHls());
        putUrl(safe, "https_hls", stream.getHttps_hls());
        putUrl(safe, "ws_hls", stream.getWs_hls());
        putUrl(safe, "wss_hls", stream.getWss_hls());
        putUrl(safe, "ts", stream.getTs());
        putUrl(safe, "https_ts", stream.getHttps_ts());
        putUrl(safe, "ws_ts", stream.getWs_ts());
        putUrl(safe, "wss_ts", stream.getWss_ts());
        putUrl(safe, "rtc", stream.getRtc());
        putUrl(safe, "rtcs", stream.getRtcs());
        return safe;
    }

    private void putUrl(Map<String, Object> out, String key, String value) {
        if (value != null && safePlaybackUrl(value)) out.put(key, value);
    }

    private boolean safePlaybackUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || !java.util.Arrays.asList("http", "https", "ws", "wss").contains(scheme)) return false;
            String normalized = host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
            if (normalized.indexOf('.') < 0 || isInternalHostSuffix(normalized)) return false;
            return !privateIpLiteral(normalized);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private boolean isInternalHostSuffix(String host) {
        return java.util.Arrays.asList(".localhost", ".local", ".localdomain", ".internal", ".intranet",
            ".cluster.local", ".svc", ".lan", ".home", ".home.arpa", ".corp", ".test", ".invalid")
            .stream().anyMatch(host::endsWith);
    }

    private boolean privateIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            String literal = host;
            int zone = literal.indexOf('%');
            if (zone >= 0) literal = literal.substring(0, zone);
            try {
                InetAddress address = InetAddress.getByName(literal);
                return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress();
            } catch (UnknownHostException invalid) {
                return true;
            }
        }
        if (!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] p = host.split("\\.");
        int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
        for (String octet : p) if (Integer.parseInt(octet) > 255) return true;
        return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
            || (a == 192 && (b == 0 || b == 168)) || (a == 100 && b >= 64 && b <= 127)
            || (a == 198 && (b == 18 || b == 19 || b == 51)) || (a == 203 && b == 0)
            || a >= 224;
    }

    private static String fingerprint(String sourceUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((sourceUrl == null ? "" : sourceUrl).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : digest) out.append(String.format("%02x", value & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Long asLong(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
    private static Long nullableLong(Object value) { return value == null ? null : asLong(value); }
}
