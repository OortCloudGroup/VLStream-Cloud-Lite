package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.vlstream.config.VlStreamDeviceAccessProperties;
import com.ruoyi.vlstream.domain.VlStreamDevice;
import com.ruoyi.vlstream.mapper.VlStreamDeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tenant-checked read-only sharing workflow for VLStream devices. */
@Service
public class VlStreamDeviceAccessService {
    private final JdbcTemplate jdbc;
    private final VlStreamDeviceMapper devices;
    private final VlStreamDeviceTenantService tenants;
    private final VlStreamDeviceAccessRefService refs;
    private final VlStreamDeviceAccessProperties properties;
    private final ApplicationEventPublisher events;
    @Autowired
    private VlStreamDeviceOwnerPlaybackGuard ownerPlayback;

    public VlStreamDeviceAccessService(JdbcTemplate jdbc, VlStreamDeviceMapper devices,
            VlStreamDeviceTenantService tenants, VlStreamDeviceAccessRefService refs,
            VlStreamDeviceAccessProperties properties, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.devices = devices;
        this.tenants = tenants;
        this.refs = refs;
        this.properties = properties;
        this.events = events;
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> access(String accessRef) {
        Long rowId = refs.resolve(accessRef);
        VlStreamDevice device = devices.selectById(rowId);
        if (device == null) throw denied();
        String ownerTenant = tenants.deviceTenant(device);
        String viewerTenant = tenants.currentTenantId();
        String viewerAccess = viewerTenant.equals(ownerTenant) ? "OWNER" : null;
        Long grantId = null;
        if (viewerAccess == null && "PUBLIC".equalsIgnoreCase(device.getVisibility())) viewerAccess = "PUBLIC";
        if (viewerAccess == null) {
            grantId = activeGrant(rowId, viewerTenant);
            if (grantId != null) viewerAccess = "GRANTED";
        }
        String requestStatus = latestRequestStatus(rowId, viewerTenant);
        if (viewerAccess == null) viewerAccess = "PENDING".equals(requestStatus) ? "PENDING" : "NONE";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceRef", accessRef);
        result.put("protocol", "VLSTREAM");
        result.put("deviceName", device.getDeviceName());
        result.put("visibility", normalizedVisibility(device.getVisibility()));
        result.put("viewerAccess", viewerAccess);
        result.put("myAccessRequestStatus", requestStatus);
        if ("OWNER".equals(viewerAccess) || "PUBLIC".equals(viewerAccess) || "GRANTED".equals(viewerAccess)) {
            result.put("status", Boolean.TRUE.equals(device.getOnline()) ? "ONLINE" :
                Boolean.FALSE.equals(device.getOnline()) ? "OFFLINE" : "UNKNOWN");
            result.put("latitude", device.getLatitude());
            result.put("longitude", device.getLongitude());
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermi('vlstream:device:share:manage')")
    public Map<String, Object> setVisibility(Long deviceRowId, String visibility) {
        String value = visibility == null ? "" : visibility.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"PRIVATE".equals(value) && !"PUBLIC".equals(value)) throw new ServiceException("visibility必须为PRIVATE或PUBLIC", 400);
        VlStreamDevice device = requireOwnerForUpdate(deviceRowId);
        String previous = normalizedVisibility(device.getVisibility());
        if (!previous.equals(value)) {
            jdbc.update("update wvp_vlstream_device set visibility=?,update_time=NOW() where id=?", value, deviceRowId);
            audit(deviceRowId, "VISIBILITY_CHANGED", null, null, previous + "->" + value);
            // Close current URLs on any visibility transition; an eligible tenant may start a fresh lease.
            events.publishEvent(new VlStreamDeviceAccessChangedEvent(deviceRowId, null));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceRowId", String.valueOf(deviceRowId));
        result.put("visibility", value);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> request(String accessRef, String reason) {
        Long deviceRowId = refs.resolve(accessRef);
        VlStreamDevice device = devices.selectByIdForUpdate(deviceRowId);
        if (device == null) throw denied();
        String owner = tenants.deviceTenant(device);
        String tenant = tenants.currentTenantId();
        if (owner.equals(tenant)) throw new ServiceException("设备所属租户不能申请查看", 403);
        if ("PUBLIC".equalsIgnoreCase(device.getVisibility())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deviceRef", accessRef);
            result.put("status", "PUBLIC");
            return result;
        }
        Long grantId = activeGrant(deviceRowId, tenant);
        if (grantId != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deviceRef", accessRef);
            result.put("status", "APPROVED");
            result.put("grantId", String.valueOf(grantId));
            return result;
        }
        List<Map<String, Object>> pending = jdbc.queryForList("select request_id from wvp_vlstream_access_request " +
            "where device_row_id=? and requester_tenant_id=? and status='PENDING' order by request_id desc limit 1 for update",
            deviceRowId, tenant);
        if (!pending.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestId", String.valueOf(pending.get(0).get("request_id")));
            result.put("deviceRef", accessRef);
            result.put("status", "PENDING");
            return result;
        }
        String cleanReason = reason == null ? null : reason.trim();
        if (cleanReason != null && cleanReason.length() > 1000) throw new ServiceException("申请理由最多1000个字符", 400);
        KeyHolder key = new GeneratedKeyHolder();
        final String reqReason = cleanReason;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "insert into wvp_vlstream_access_request(device_row_id,requester_tenant_id,requester_user_id,request_reason,status,created_at,updated_at) values (?,?,?,?, 'PENDING',NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, deviceRowId);
            statement.setString(2, tenant);
            statement.setLong(3, SecurityUtils.getUserId());
            statement.setString(4, reqReason);
            return statement;
        }, key);
        Long requestId = key.getKey().longValue();
        audit(deviceRowId, "REQUESTED", requestId, null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", String.valueOf(requestId));
        result.put("deviceRef", accessRef);
        result.put("status", "PENDING");
        return result;
    }

    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    public List<Map<String, Object>> ownerRequests(Long deviceRowId) {
        requireOwner(deviceRowId);
        List<Map<String, Object>> rows = jdbc.queryForList("select request_id,requester_tenant_id,requester_user_id,request_reason,status,created_at " +
            "from wvp_vlstream_access_request where device_row_id=? and status='PENDING' order by created_at asc limit 500", deviceRowId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("requestId", String.valueOf(row.get("request_id")));
            item.put("requesterTenantId", row.get("requester_tenant_id"));
            item.put("requesterUserId", String.valueOf(row.get("requester_user_id")));
            item.put("reason", row.get("request_reason"));
            item.put("status", row.get("status"));
            item.put("createdAt", row.get("created_at"));
            result.add(item);
        }
        return result;
    }

    /** Grant history for the current owner, including expiry before its audit worker runs. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    public Map<String, Object> ownerGrants(Long deviceRowId, Integer pageNum, Integer pageSize, String status) {
        requireOwner(deviceRowId);
        String ownerTenant = tenants.currentTenantId();
        int page = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 20 : pageSize;
        if (page < 1 || size < 1 || size > 100) throw new ServiceException("pageNum必须大于0，pageSize必须在1到100之间", 400);
        String filter = status == null || status.trim().isEmpty() ? "ALL" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Arrays.asList("ALL", "ACTIVE", "EXPIRED", "REVOKED").contains(filter)) {
            throw new ServiceException("status必须为ALL、ACTIVE、EXPIRED或REVOKED", 400);
        }
        // Use one database time and a read snapshot for both the count and page at an expiry boundary.
        Timestamp asOf = jdbc.queryForObject("select NOW()", Timestamp.class);
        String from = " from wvp_vlstream_access_grant g join wvp_vlstream_device v on v.id=g.device_row_id " +
            "left join wvp_vlstream_access_request r on r.request_id=g.request_id and r.device_row_id=g.device_row_id " +
            "and r.requester_tenant_id=g.viewer_tenant_id ";
        String where = " where g.device_row_id=? and g.owner_tenant_id=? " +
            "and (case when v.tenant_id is null or TRIM(v.tenant_id)='' then ? else v.tenant_id end)=?";
        List<Object> args = new ArrayList<>();
        args.add(deviceRowId);
        args.add(ownerTenant);
        args.add(tenants.defaultTenant());
        args.add(ownerTenant);
        if ("ACTIVE".equals(filter)) {
            where += " and g.revoked_at is null and g.expires_at>?";
            args.add(asOf);
        } else if ("EXPIRED".equals(filter)) {
            where += " and g.revoked_at is null and g.expires_at<=?";
            args.add(asOf);
        } else if ("REVOKED".equals(filter)) {
            where += " and g.revoked_at is not null";
        }
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        long offset = ((long) page - 1L) * size;
        if (total != null && offset < total) {
            String audits = "left join wvp_vlstream_access_audit rev on g.revoked_at is not null and rev.audit_id=(" +
                "select max(a.audit_id) from wvp_vlstream_access_audit a where a.device_row_id=g.device_row_id " +
                "and a.grant_id=g.grant_id and a.actor_tenant_id=g.owner_tenant_id and a.action='REVOKED') " +
                "left join wvp_vlstream_access_audit exp on g.expired_at is not null and exp.audit_id=(" +
                "select max(a.audit_id) from wvp_vlstream_access_audit a where a.device_row_id=g.device_row_id " +
                "and a.grant_id=g.grant_id and a.actor_tenant_id=g.owner_tenant_id and a.action='GRANT_EXPIRED') ";
            List<Object> pageArgs = new ArrayList<>();
            pageArgs.add(asOf);
            pageArgs.addAll(args);
            pageArgs.add(size);
            pageArgs.add(offset);
            List<Map<String, Object>> rows = jdbc.queryForList("select g.grant_id,g.request_id,g.viewer_tenant_id," +
                "r.requester_user_id,r.request_reason,r.created_at request_created_at,g.approved_by_user_id," +
                "g.create_time approved_at,g.expires_at,g.expired_at,g.revoked_at," +
                "case when g.revoked_at is not null then 'REVOKED' when g.expires_at<=? then 'EXPIRED' else 'ACTIVE' end grant_status," +
                "rev.audit_id revocation_audit_id,rev.actor_user_id revoked_by_user_id,exp.audit_id expiration_audit_id" +
                from + audits + where + " order by g.create_time desc,g.grant_id desc limit ? offset ?", pageArgs.toArray());
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("grantId", nullableId(row.get("grant_id")));
                item.put("requestId", nullableId(row.get("request_id")));
                item.put("requesterTenantId", row.get("viewer_tenant_id"));
                item.put("requesterUserId", nullableId(row.get("requester_user_id")));
                item.put("reason", row.get("request_reason"));
                item.put("status", row.get("grant_status"));
                item.put("canRevoke", "ACTIVE".equals(row.get("grant_status")));
                item.put("requestCreatedAt", row.get("request_created_at"));
                item.put("approvedAt", row.get("approved_at"));
                item.put("approvedByUserId", nullableId(row.get("approved_by_user_id")));
                item.put("expiresAt", row.get("expires_at"));
                item.put("expiredAt", row.get("expired_at"));
                item.put("revokedAt", row.get("revoked_at"));
                item.put("revocationAuditId", nullableId(row.get("revocation_audit_id")));
                item.put("revokedByUserId", nullableId(row.get("revoked_by_user_id")));
                item.put("expirationAuditId", nullableId(row.get("expiration_audit_id")));
                items.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", items);
        result.put("total", total == null ? 0L : total);
        result.put("pageNum", page);
        result.put("pageSize", size);
        result.put("status", filter);
        result.put("asOf", asOf);
        return result;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> myRequests() {
        String tenant = tenants.currentTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList("select r.request_id,r.request_reason, " +
            "case when r.status='APPROVED' and g.revoked_at is not null then 'REVOKED' " +
            "when r.status='APPROVED' and g.expires_at<=NOW() then 'EXPIRED' else r.status end status,r.created_at,r.updated_at, " +
            "ref.access_ref,v.device_name,v.visibility from wvp_vlstream_access_request r " +
            "join wvp_vlstream_device_access_ref ref on ref.device_row_id=r.device_row_id " +
            "join wvp_vlstream_device v on v.id=r.device_row_id " +
            "left join wvp_vlstream_access_grant g on g.grant_id=r.resolved_grant_id " +
            "where r.requester_tenant_id=? order by r.created_at desc limit 500", tenant);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("requestId", String.valueOf(row.get("request_id")));
            item.put("deviceRef", row.get("access_ref"));
            item.put("deviceName", row.get("device_name"));
            item.put("visibility", row.get("visibility"));
            item.put("reason", row.get("request_reason"));
            item.put("status", row.get("status"));
            item.put("createdAt", row.get("created_at"));
            item.put("updatedAt", row.get("updated_at"));
            result.add(item);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    public Map<String, Object> approve(Long requestId, Integer expiresInDays) {
        // Keep the lock order consistent with request(): device first, then request.
        // This serializes concurrent requests/decisions without a request↔device deadlock.
        Long deviceRowId = requestDeviceRowId(requestId);
        VlStreamDevice device = requireOwnerForUpdate(deviceRowId);
        Map<String, Object> request = selectRequestForUpdate(requestId);
        if (!"PENDING".equals(request.get("status"))) throw new ServiceException("申请已处理", 409);
        String owner = tenants.deviceTenant(device);
        String viewer = String.valueOf(request.get("requester_tenant_id"));
        if (owner.equals(viewer)) throw new ServiceException("设备所属租户不能为自己审批查看权限", 409);
        if ("PUBLIC".equalsIgnoreCase(device.getVisibility())) throw new ServiceException("设备已公开，无需审批授权", 409);
        int days = expiresInDays == null ? properties.getDefaultGrantDays() : expiresInDays;
        if (days < 1 || days > properties.getMaxGrantDays()) {
            throw new ServiceException("审批期限必须在1到" + properties.getMaxGrantDays() + "天之间", 400);
        }
        Long existingGrant = activeGrant(deviceRowId, viewer);
        if (existingGrant != null) {
            jdbc.update("update wvp_vlstream_access_request set status='APPROVED',resolved_grant_id=?,updated_at=NOW(),decided_at=NOW(),decided_by_tenant_id=?,decided_by_user_id=?,decision_reason='租户已有有效授权' where request_id=?",
                existingGrant, owner, SecurityUtils.getUserId(), requestId);
            audit(deviceRowId, "APPROVED_EXISTING_GRANT", requestId, existingGrant, "existing grant reused");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestId", String.valueOf(requestId));
            result.put("grantId", String.valueOf(existingGrant));
            result.put("expiresInDays", days);
            result.put("status", "APPROVED");
            return result;
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "insert into wvp_vlstream_access_grant(request_id,device_row_id,owner_tenant_id,viewer_tenant_id,approved_by_user_id,expires_at,create_time) " +
                    "values (?,?,?,?,?,TIMESTAMPADD(DAY,?,NOW()),NOW())", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, requestId);
            statement.setLong(2, deviceRowId);
            statement.setString(3, owner);
            statement.setString(4, viewer);
            statement.setLong(5, SecurityUtils.getUserId());
            statement.setInt(6, days);
            return statement;
        }, key);
        Long grantId = key.getKey().longValue();
        jdbc.update("update wvp_vlstream_access_request set status='APPROVED',resolved_grant_id=?,updated_at=NOW(),decided_at=NOW(),decided_by_tenant_id=?,decided_by_user_id=? where request_id=?",
            grantId, owner, SecurityUtils.getUserId(), requestId);
        audit(deviceRowId, "APPROVED", requestId, grantId, "expiresInDays=" + days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", String.valueOf(requestId));
        result.put("grantId", String.valueOf(grantId));
        result.put("expiresInDays", days);
        result.put("status", "APPROVED");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    public Map<String, Object> reject(Long requestId, String reason) {
        // Match approve()/request() lock ordering so racing duplicate decisions serialize.
        Long deviceRowId = requestDeviceRowId(requestId);
        VlStreamDevice device = requireOwnerForUpdate(deviceRowId);
        Map<String, Object> request = selectRequestForUpdate(requestId);
        if (!"PENDING".equals(request.get("status"))) throw new ServiceException("申请已处理", 409);
        String cleanReason = reason == null ? null : reason.trim();
        if (cleanReason != null && cleanReason.length() > 1000) throw new ServiceException("拒绝理由最多1000个字符", 400);
        jdbc.update("update wvp_vlstream_access_request set status='REJECTED',updated_at=NOW(),decided_at=NOW(),decided_by_tenant_id=?,decided_by_user_id=?,decision_reason=? where request_id=?",
            tenants.deviceTenant(device), SecurityUtils.getUserId(), cleanReason, requestId);
        audit(deviceRowId, "REJECTED", requestId, null, cleanReason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", String.valueOf(requestId));
        result.put("status", "REJECTED");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermi('vlstream:device:share:approve')")
    public Map<String, Object> revoke(Long grantId) {
        Long deviceRowId = grantDeviceRowId(grantId);
        VlStreamDevice device = requireOwnerForUpdate(deviceRowId);
        Map<String, Object> grant = selectGrantForUpdate(grantId);
        if (grant.get("revoked_at") != null) throw new ServiceException("授权已撤销", 409);
        if (new Date().after((Date) grant.get("expires_at"))) throw new ServiceException("授权已过期", 409);
        jdbc.update("update wvp_vlstream_access_grant set revoked_at=NOW() where grant_id=? and revoked_at is null", grantId);
        jdbc.update("update wvp_vlstream_access_request set status='REVOKED',updated_at=NOW() where resolved_grant_id=? and status='APPROVED'", grantId);
        audit(deviceRowId, "REVOKED", null, grantId, null);
        events.publishEvent(new VlStreamDeviceAccessChangedEvent(deviceRowId, grantId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grantId", String.valueOf(grantId));
        result.put("status", "REVOKED");
        return result;
    }

    /** Materialize expiry once for audit and promptly stop any still-active per-viewer proxy. */
    @Transactional(rollbackFor = Exception.class)
    @Scheduled(fixedDelay = 60000)
    public void markExpiredGrants() {
        List<Map<String, Object>> expired = jdbc.queryForList("select grant_id,device_row_id,owner_tenant_id from wvp_vlstream_access_grant " +
            "where revoked_at is null and expired_at is null and expires_at<=NOW() order by expires_at limit 200 for update");
        for (Map<String, Object> grant : expired) {
            Long grantId = number(grant.get("grant_id"));
            Long deviceRowId = number(grant.get("device_row_id"));
            int changed = jdbc.update("update wvp_vlstream_access_grant set expired_at=NOW() where grant_id=? and revoked_at is null and expired_at is null and expires_at<=NOW()", grantId);
            if (changed == 0) continue;
            jdbc.update("update wvp_vlstream_access_request set status='EXPIRED',updated_at=NOW() where resolved_grant_id=? and status='APPROVED'", grantId);
            jdbc.update("insert into wvp_vlstream_access_audit(device_row_id,actor_tenant_id,actor_user_id,action,grant_id,detail,created_at) values (?,?,0,'GRANT_EXPIRED',?,'system expiry',NOW())",
                deviceRowId, grant.get("owner_tenant_id"), grantId);
            events.publishEvent(new VlStreamDeviceAccessChangedEvent(deviceRowId, grantId));
        }
    }

    Long resolveDeviceRef(String accessRef) { return refs.resolve(accessRef); }
    String tenantId() { return tenants.currentTenantId(); }
    Long userId() { return SecurityUtils.getUserId(); }
    Long requireReadAccess(VlStreamDevice device, String viewerTenant) {
        if (device == null) throw denied();
        // This entry point belongs to a user request. Never accept another tenant as its viewer.
        if (viewerTenant == null || !viewerTenant.equals(tenants.currentTenantId())) throw denied();
        if (viewerTenant.equals(tenants.deviceTenant(device))) {
            if (ownerPlayback == null) throw denied();
            try {
                ownerPlayback.requireOwnerPlayback(device.getId());
            } catch (AccessDeniedException denied) {
                throw new ServiceException("没有设备播放权限", 403);
            }
            return null;
        }
        return requireForeignReadAccess(device, viewerTenant);
    }

    /** Only a persisted one-use ticket can create this capability; WebSocket workers have no login context. */
    Long recheckCameraRtcTicket(VlStreamDevice device, VlStreamDeviceCameraRtcTicketService.BoundTicketViewer viewer) {
        if (device == null || viewer == null || !device.getId().equals(viewer.deviceRowId())) throw denied();
        if (viewer.tenantId().equals(tenants.deviceTenant(device))) return null;
        return requireForeignReadAccess(device, viewer.tenantId());
    }

    private Long requireForeignReadAccess(VlStreamDevice device, String viewerTenant) {
        if ("PUBLIC".equalsIgnoreCase(device.getVisibility())) return null;
        Long grantId = activeGrant(device.getId(), viewerTenant);
        if (grantId == null) throw new ServiceException("当前租户没有设备查看权限", 403);
        return grantId;
    }
    Long activeGrant(Long deviceRowId, String viewerTenant) {
        List<Map<String, Object>> rows = jdbc.queryForList("select grant_id from wvp_vlstream_access_grant where device_row_id=? and viewer_tenant_id=? and revoked_at is null and expires_at>NOW() order by grant_id desc limit 1",
            deviceRowId, viewerTenant);
        return rows.isEmpty() ? null : number(rows.get(0).get("grant_id"));
    }

    private String latestRequestStatus(Long rowId, String tenant) {
        List<Map<String, Object>> rows = jdbc.queryForList("select r.status,g.expires_at,g.revoked_at from wvp_vlstream_access_request r " +
            "left join wvp_vlstream_access_grant g on g.grant_id=r.resolved_grant_id " +
            "where r.device_row_id=? and r.requester_tenant_id=? order by r.request_id desc limit 1", rowId, tenant);
        if (rows.isEmpty()) return null;
        Map<String, Object> latest = rows.get(0);
        String status = String.valueOf(latest.get("status"));
        if ("APPROVED".equals(status)) {
            if (latest.get("revoked_at") != null) return "REVOKED";
            Object expiresAt = latest.get("expires_at");
            if (expiresAt instanceof Date && ((Date) expiresAt).before(new Date())) return "EXPIRED";
        }
        return status;
    }

    private Map<String, Object> selectRequestForUpdate(Long requestId) {
        try {
            return jdbc.queryForMap("select request_id,device_row_id,requester_tenant_id,status from wvp_vlstream_access_request where request_id=? for update", requestId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("申请不存在", 404);
        }
    }

    private Long grantDeviceRowId(Long grantId) {
        try {
            return number(jdbc.queryForMap("select device_row_id from wvp_vlstream_access_grant where grant_id=?", grantId)
                .get("device_row_id"));
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("授权不存在", 404);
        }
    }

    private Map<String, Object> selectGrantForUpdate(Long grantId) {
        try {
            return jdbc.queryForMap("select grant_id,device_row_id,viewer_tenant_id,expires_at,revoked_at from wvp_vlstream_access_grant where grant_id=? for update", grantId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("授权不存在", 404);
        }
    }

    private Long requestDeviceRowId(Long requestId) {
        try {
            return number(jdbc.queryForMap("select device_row_id from wvp_vlstream_access_request where request_id=?", requestId)
                .get("device_row_id"));
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("申请不存在", 404);
        }
    }

    private VlStreamDevice requireOwnerForUpdate(Long rowId) {
        VlStreamDevice device = devices.selectByIdForUpdate(rowId);
        if (device == null || !tenants.currentTenantId().equals(tenants.deviceTenant(device))) throw denied();
        return device;
    }

    private void requireOwner(Long rowId) {
        VlStreamDevice device = devices.selectById(rowId);
        if (device == null || !tenants.currentTenantId().equals(tenants.deviceTenant(device))) throw denied();
    }

    private void audit(Long rowId, String action, Long requestId, Long grantId, String detail) {
        jdbc.update("insert into wvp_vlstream_access_audit(device_row_id,actor_tenant_id,actor_user_id,action,request_id,grant_id,detail,created_at) values (?,?,?,?,?,?,?,NOW())",
            rowId, tenants.currentTenantId(), SecurityUtils.getUserId(), action, requestId, grantId, detail);
    }

    private static Long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
    private static String nullableId(Object value) { return value == null ? null : String.valueOf(value); }
    private static String normalizedVisibility(String visibility) { return "PUBLIC".equalsIgnoreCase(visibility) ? "PUBLIC" : "PRIVATE"; }
    private static ServiceException denied() { return new ServiceException("设备不存在或不属于当前租户", 403); }
}
