package com.ruoyi.onvif.service.impl;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import be.teletask.onvif.DiscoveryManager;
import be.teletask.onvif.OnvifManager;
import be.teletask.onvif.listeners.*;
import be.teletask.onvif.models.Device;
import be.teletask.onvif.models.OnvifDeviceInformation;
import be.teletask.onvif.models.OnvifMediaProfile;
import be.teletask.onvif.models.OnvifServices;
import be.teletask.onvif.responses.OnvifResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.onvif.domain.FetchMainAndSubStreamUris;
import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import com.ruoyi.onvif.domain.bo.WSDeviceBo;
import com.ruoyi.onvif.domain.dto.WSDiscoveryDeviceDTO;
import com.ruoyi.onvif.service.IOnvifService;
import com.ruoyi.onvif.utils.OnvifDeviceProbe;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.onvif.mapper.OnvifDeviceMapper;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.service.IOnvifDeviceService;

/**
 * onvif 设备Service业务层处理
 *
 * @author 陈江灿
 * @date 2025-04-10
 */
@Service
public class OnvifDeviceServiceImpl implements IOnvifDeviceService {
    @Autowired
    private OnvifDeviceMapper onvifDeviceMapper;

    @Autowired
    private IOnvifService service;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 查询onvif 设备
     *
     * @param id onvif 设备主键
     * @return onvif 设备
     */
    @Override
    public OnvifDevice selectOnvifDeviceById(Long id) {
        return onvifDeviceMapper.selectOnvifDeviceById(id);
    }

    /**
     * 查询onvif 设备列表
     *
     * @param onvifDevice onvif 设备
     * @return onvif 设备
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<OnvifDevice> selectOnvifDeviceList(OnvifDevice onvifDevice) {
        return onvifDeviceMapper.selectOnvifDeviceList(onvifDevice);
    }

    /**
     * 新增onvif 设备
     *
     * @param onvifDevice onvif 设备
     * @return 结果
     */
    @Override
    public int insertOnvifDevice(OnvifDevice onvifDevice) {
        OnvifDevice row = onvifDeviceMapper.getOneByIp(onvifDevice.getIp());
        if (row != null) {
            throw new RuntimeException("该设备已存在");
        }
        try {
            if (onvifDevice.getStreamUris() != null) {
                onvifDevice.setStreamUris(new ObjectMapper().writeValueAsString(onvifDevice.getStreamUris()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        onvifDevice.setCreateTime(DateUtils.getNowDate());
        return onvifDeviceMapper.insertOnvifDevice(onvifDevice);
    }

    /**
     * 修改onvif 设备
     *
     * @param onvifDevice onvif 设备
     * @return 结果
     */
    @Override
    public int updateOnvifDevice(OnvifDevice onvifDevice) {
        try {
            if (onvifDevice.getStreamUris() != null) {
                onvifDevice.setStreamUris(new ObjectMapper().writeValueAsString(onvifDevice.getStreamUris()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        onvifDevice.setUpdateTime(DateUtils.getNowDate());
        return onvifDeviceMapper.updateOnvifDevice(onvifDevice);
    }

    /**
     * 批量删除onvif 设备
     *
     * @param ids 需要删除的onvif 设备主键
     * @return 结果
     */
    @Override
    public int deleteOnvifDeviceByIds(Long[] ids) {
        return onvifDeviceMapper.deleteOnvifDeviceByIds(ids);
    }

    /**
     * 删除onvif 设备信息
     *
     * @param id onvif 设备主键
     * @return 结果
     */
    @Override
    public int deleteOnvifDeviceById(Long id) {
        return onvifDeviceMapper.deleteOnvifDeviceById(id);
    }

    /**
     * 查询地图onvif 设备列表
     *
     * @param onvifDevice
     * @return
     */
    @DataScope(deptAlias = "d")
    @Override
    public List<OnvifDevice> selectOnvifDeviceListMap(OnvifDevice onvifDevice) {
        return onvifDeviceMapper.selectOnvifDeviceListMap(onvifDevice);
    }

    /**
     * Web Services Dynamic Discovery
     *
     * @return
     */
    @Override
    public List<WSDiscoveryDeviceDTO> WSDiscovery() throws Exception {
        DiscoveryManager manager = new DiscoveryManager();
        manager.setDiscoveryTimeout(10000);
        CountDownLatch latch = new CountDownLatch(1);
        List<WSDiscoveryDeviceDTO> result = new CopyOnWriteArrayList<>();
        AtomicBoolean signaled = new AtomicBoolean(false);
        DiscoveryListener listener = new DiscoveryListener() {
            @Override
            public void onDiscoveryStarted() {
            }

            @Override
            public void onDevicesFound(List<Device> devices) {
                for (Device device : devices) {
                    WSDiscoveryDeviceDTO dto = new WSDiscoveryDeviceDTO();
                    dto.setHostName(device.getHostName());
                    try {
                        URL url = new URL(device.getHostName());
                        dto.setIp(url.getHost());
                    } catch (Exception e) {
                        dto.setIp(extractIpFromUrlString(device.getHostName()));
                    }
                    result.add(dto);
                }
                if (signaled.compareAndSet(false, true)) {
                    latch.countDown();
                }
            }
        };
        manager.discover(listener);
        boolean ok = latch.await(11, TimeUnit.SECONDS);
        return new ArrayList<>(result);
    }


    /**
     * onvif新增方法
     *
     * @param bo
     * @return
     */
    @Override
    public OnvifDevice addOnvif(WSDeviceBo bo) {
        if (bo == null || !("1".equals(bo.getAuth()) || "2".equals(bo.getAuth()))) {
            throw new ServiceException("请选择认证方式");
        }
        FetchMainAndSubStreamUrisBo query = new FetchMainAndSubStreamUrisBo();
        query.setIp(bo.getIp());
        query.setUsername(bo.getUsername());
        query.setPassword(bo.getPassword());
        FetchMainAndSubStreamUris info = new OnvifDeviceProbe().read(query, "2".equals(bo.getAuth()));
        OnvifDevice device = new OnvifDevice();
        device.setIp(bo.getIp());
        device.setFirm(info.getFirm());
        device.setModel(info.getModel());
        device.setFirmwareVersion(info.getFirmwareVersion());
        device.setStreamUris(info.getStreamUris());
        return device;
    }


    /**
     * 认证
     *
     * @param onvifDevice
     * @return
     */
    @Override
    public OnvifDevice auth(OnvifDevice onvifDevice) {
        return null;
    }


    private String extractIpFromUrlString(String urlString) {
        if (urlString == null || urlString.isEmpty()) return "";
        String noProtocol = urlString.replaceAll("^https?://", "");
        if (noProtocol.startsWith("[")) {
            int end = noProtocol.indexOf(']');
            if (end != -1) {
                return noProtocol.substring(1, end);
            }
        }
        String beforePath = noProtocol.split("/")[0];
        if (beforePath.split(":").length > 2) {
            return beforePath;
        }
        return beforePath.split(":")[0];
    }

}
