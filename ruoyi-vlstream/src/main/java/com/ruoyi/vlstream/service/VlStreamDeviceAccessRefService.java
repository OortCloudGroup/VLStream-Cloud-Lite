package com.ruoyi.vlstream.service;

import com.ruoyi.common.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Maps internal row IDs to random references accepted by cross-tenant APIs. */
@Service
public class VlStreamDeviceAccessRefService {
    private final JdbcTemplate jdbc;

    public VlStreamDeviceAccessRefService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String ensureRef(Long deviceRowId) {
        if (deviceRowId == null || deviceRowId <= 0) throw new ServiceException("设备不存在", 404);
        try {
            jdbc.update("insert into wvp_vlstream_device_access_ref(device_row_id,access_ref) values (?,?)",
                deviceRowId, UUID.randomUUID().toString().replace("-", ""));
        } catch (DuplicateKeyException ignored) {
            // The mapping already exists or a concurrent caller inserted it.
        }
        return jdbc.queryForObject("select access_ref from wvp_vlstream_device_access_ref where device_row_id=?",
            String.class, deviceRowId);
    }

    public Long resolve(String accessRef) {
        if (accessRef == null || !accessRef.matches("[a-f0-9]{32}")) {
            throw new ServiceException("设备不存在或无权访问", 404);
        }
        try {
            return jdbc.queryForObject("select device_row_id from wvp_vlstream_device_access_ref where access_ref=?",
                Long.class, accessRef);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ServiceException("设备不存在或无权访问", 404);
        }
    }
}
