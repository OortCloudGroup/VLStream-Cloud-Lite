package com.ruoyi.onvif.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

import com.ruoyi.onvif.domain.OnvifDevice;

/**
 * onvif 设备Mapper接口
 *
 * @author 陈江灿
 * @date 2025-04-10
 */
public interface OnvifDeviceMapper {
    /**
     * 查询onvif 设备
     *
     * @param id onvif 设备主键
     * @return onvif 设备
     */
    public OnvifDevice selectOnvifDeviceById(Long id);

    /**
     * 查询onvif 设备列表
     *
     * @param onvifDevice onvif 设备
     * @return onvif 设备集合
     */
    public List<OnvifDevice> selectOnvifDeviceList(OnvifDevice onvifDevice);

    /**
     * 新增onvif 设备
     *
     * @param onvifDevice onvif 设备
     * @return 结果
     */
    public int insertOnvifDevice(OnvifDevice onvifDevice);

    /**
     * 修改onvif 设备
     *
     * @param onvifDevice onvif 设备
     * @return 结果
     */
    public int updateOnvifDevice(OnvifDevice onvifDevice);

    /**
     * 删除onvif 设备
     *
     * @param id onvif 设备主键
     * @return 结果
     */
    public int deleteOnvifDeviceById(@Param("id") Long id, @Param("tenantId") String tenantId, @Param("defaultTenantId") String defaultTenantId);

    /**
     * 批量删除onvif 设备
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteOnvifDeviceByIds(@Param("ids") Long[] ids, @Param("tenantId") String tenantId, @Param("defaultTenantId") String defaultTenantId);

    /**
     * 根据ip查询设备
     *
     * @param ip
     * @return
     */
    OnvifDevice getOneByIp(OnvifDevice device);

    /**
     * 查询地图onvif 设备列表
     *
     * @param onvifDevice
     * @return
     */
    List<OnvifDevice> selectOnvifDeviceListMap(OnvifDevice onvifDevice);
}
