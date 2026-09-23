package com.ruoyi.vlstream.mapper;

import com.ruoyi.vlstream.domain.VlStreamDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;

@Mapper
public interface VlStreamDeviceMapper {
    VlStreamDevice selectById(Long id);
    VlStreamDevice selectByDeviceId(String deviceId);
    VlStreamDevice selectByDeviceIdForUpdate(String deviceId);
    List<VlStreamDevice> selectList(VlStreamDevice query);
    int insert(VlStreamDevice device);
    int update(VlStreamDevice device);
    int markOfflineBefore(@Param("deadline") Date deadline, @Param("updateTime") Date updateTime);
}
