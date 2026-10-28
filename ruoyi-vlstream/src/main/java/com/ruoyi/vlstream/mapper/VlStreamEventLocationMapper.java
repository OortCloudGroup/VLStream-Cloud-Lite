package com.ruoyi.vlstream.mapper;

import com.ruoyi.vlstream.domain.VlStreamDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** Minimal, tenant-scoped batch projection; no device catalogue duplication in VLS. */
@Mapper
public interface VlStreamEventLocationMapper {
    @Select({"<script>",
        "SELECT device_id AS deviceId, tenant_id AS tenantId, longitude, latitude, online,",
        "last_heartbeat_time AS lastHeartbeatTime FROM wvp_vlstream_device",
        "WHERE (tenant_id = #{tenantId} OR ((tenant_id IS NULL OR tenant_id = '') AND #{tenantId} = #{defaultTenant}))",
        "AND device_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "</script>"})
    List<VlStreamDevice> selectLocations(@Param("tenantId") String tenantId,
        @Param("defaultTenant") String defaultTenant, @Param("ids") List<String> ids);
}
