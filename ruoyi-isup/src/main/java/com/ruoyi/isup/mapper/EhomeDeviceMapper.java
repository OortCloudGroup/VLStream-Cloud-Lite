package com.ruoyi.isup.mapper;

import com.ruoyi.isup.ehome.EhomeDevice;

import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface EhomeDeviceMapper {
    List<EhomeDevice> selectDevices(EhomeDevice query);
    Long findRegisteredId(String deviceId);
    int insertRegistration(EhomeDevice device);
    int updateRegistration(EhomeDevice device);
    int markOffline(@Param("luserId") int luserId);
    int markAllOffline();
    int updateMetadata(EhomeDevice device);
}
