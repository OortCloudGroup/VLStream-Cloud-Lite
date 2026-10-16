package com.ruoyi.custom.mapper;

import com.ruoyi.custom.domain.CustomTargetHeartbeat;
import org.apache.ibatis.annotations.Param;

public interface CustomDeviceStateMapper {
    CustomTargetHeartbeat selectCameraTarget(@Param("deviceId") String deviceId,
        @Param("tenantId") String tenantId, @Param("defaultTenantId") String defaultTenantId);
}
