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
    private com.ruoyi.onvif.service.OnvifDeviceScopedReader scopedReader;

    private OnvifDevice requireDevice(Long id) {
        if (id == null) throw new ServiceException("设备不存在或无权访问", 403);
        OnvifDevice query = new OnvifDevice(); query.setId(id);
        return scopedReader.forOperation(query, false).stream().filter(row -> id.equals(row.getId())).findFirst()
                .orElseThrow(() -> new ServiceException("设备不存在或无权访问", 403));
    }

    @Autowired
    private com.ruoyi.common.deviceavailability.ProtocolAvailabilityService availability;

    private OnvifDevice decorate(OnvifDevice device) {
        if (device != null) device.setAvailability(availability.get("ONVIF", String.valueOf(device.getId())));
        return device;
    }

    private List<OnvifDevice> decorate(List<OnvifDevice> devices) {
        devices.forEach(this::decorate);
        return devices;
    }


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
        return decorate(requireDevice(id));
    }

    /**
     * 查询onvif 设备列表
     *
     * @param onvifDevice onvif 设备
     * @return onvif 设备
     */
    @Override
    public List<OnvifDevice> selectOnvifDeviceList(OnvifDevice onvifDevice) {
        return decorate(scopedReader.forOperation(onvifDevice, false));
    }

    /**
     * 新增onvif 设备
     *
     * @param onvifDevice onvif 设备
     * @return 结果
     */
    @Override
    public int insertOnvifDevice(OnvifDevice onvifDevice) {
        onvifDevice.setTenantId(scopedReader.currentTenant());
        scopedReader.scope(onvifDevice);
        OnvifDevice row = onvifDeviceMapper.getOneByIp(onvifDevice);
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
        requireDevice(onvifDevice.getId());
        scopedReader.scope(onvifDevice);
        try {
            if (onvifDevice.getStreamUris() != null) {
                onvifDevice.setStreamUris(new ObjectMapper().writeValueAsString(onvifDevice.getStreamUris()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        onvifDevice.setUpdateTime(DateUtils.getNowDate());
        int changed = onvifDeviceMapper.updateOnvifDevice(onvifDevice);
        if (changed > 0) availability.invalidate("ONVIF", String.valueOf(onvifDevice.getId()));
        return changed;
    }

    /**
     * 批量删除onvif 设备
     *
     * @param ids 需要删除的onvif 设备主键
     * @return 结果
     */
    @Override
    public int deleteOnvifDeviceByIds(Long[] ids) {
        for (Long id : ids) requireDevice(id);
        OnvifDevice scope = new OnvifDevice(); scopedReader.scope(scope);
        return onvifDeviceMapper.deleteOnvifDeviceByIds(ids, (String) scope.getParams().get("tenantId"),
                (String) scope.getParams().get("defaultTenantId"));
    }

    /**
     * 删除onvif 设备信息
     *
     * @param id onvif 设备主键
     * @return 结果
     */
    @Override
    public int deleteOnvifDeviceById(Long id) {
        requireDevice(id);
        OnvifDevice scope = new OnvifDevice(); scopedReader.scope(scope);
        return onvifDeviceMapper.deleteOnvifDeviceById(id, (String) scope.getParams().get("tenantId"),
                (String) scope.getParams().get("defaultTenantId"));
    }

    /**
     * 查询地图onvif 设备列表
     *
     * @param onvifDevice
     * @return
     */
    @Override
    public List<OnvifDevice> selectOnvifDeviceListMap(OnvifDevice onvifDevice) {
        return decorate(scopedReader.forOperation(onvifDevice, true));
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
