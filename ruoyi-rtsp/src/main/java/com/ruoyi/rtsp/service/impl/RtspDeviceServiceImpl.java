package com.ruoyi.rtsp.service.impl;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.rtsp.config.ODConfig;
import com.ruoyi.rtsp.constants.StreamTypeConstants;
import com.ruoyi.rtsp.domain.ODResult;
import com.ruoyi.rtsp.domain.bo.RtspDeviceBo;
import com.ruoyi.rtsp.domain.bo.alarmClockBo;
import com.ruoyi.rtsp.tool.CameraDetectionTool;
import com.ruoyi.rtsp.tool.RtspTool;
import com.ruoyi.rtsp.utils.ImageUtil;
import com.ruoyi.rtsp.utils.Letterbox;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.rtsp.mapper.RtspDeviceMapper;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.service.IRtspDeviceService;

/**
 * rtsp设备Service业务层处理
 *
 * @author 陈江灿
 * @date 2025-04-22
 */
@Service
public class RtspDeviceServiceImpl implements IRtspDeviceService {
    @Autowired
    private RtspDeviceMapper rtspDeviceMapper;

    @Autowired
    private com.ruoyi.rtsp.service.RtspDeviceScopedReader scopedReader;

    private RtspDevice requireDevice(Long id) {
        if (id == null) throw new com.ruoyi.common.exception.ServiceException("设备不存在或无权访问", 403);
        RtspDevice query = new RtspDevice();
        query.setId(id);
        return scopedReader.forOperation(query, false).stream().filter(row -> id.equals(row.getId())).findFirst()
                .orElseThrow(() -> new com.ruoyi.common.exception.ServiceException("设备不存在或无权访问", 403));
    }

    @Autowired
    private com.ruoyi.common.deviceavailability.ProtocolAvailabilityService availability;

    private RtspDevice decorate(RtspDevice device) {
        if (device != null) device.setAvailability(availability.get("RTSP", String.valueOf(device.getId())));
        return device;
    }

    private List<RtspDevice> decorate(List<RtspDevice> devices) {
        devices.forEach(this::decorate);
        return devices;
    }


    /**
     * 查询rtsp设备
     *
     * @param id rtsp设备主键
     * @return rtsp设备
     */
    @Override
    public RtspDevice selectRtspDeviceById(Long id) {
        return decorate(requireDevice(id));
    }

    /**
     * 查询rtsp设备列表
     *
     * @param rtspDevice rtsp设备
     * @return rtsp设备
     */
    @Override
    public List<RtspDevice> selectRtspDeviceList(RtspDevice rtspDevice) {
        return decorate(scopedReader.forOperation(rtspDevice, false));
    }

    /**
     * 新增rtsp设备
     *
     * @param rtspDevice rtsp设备
     * @return 结果
     */
    @Override
    public int insertRtspDevice(RtspDevice rtspDevice) {
        rtspDevice.setTenantId(scopedReader.currentTenant());
        // 生成rtsp地址
        String rtspUrl = "1".equals(rtspDevice.getPlayType())
                ? RtspTool.generateRtspUrl(rtspDevice) : rtspDevice.getUrl();
        if (rtspDevice != null) {
            rtspDevice.setUrl(rtspUrl);
            if ("1".equals(rtspDevice.getPlayType()) && rtspDevice.getPort() != null) {
                rtspDevice.setIp(RtspTool.normalizeHostPort(rtspDevice.getIp(), rtspDevice.getPort()));
            }
        }
        rtspDevice.setCreateTime(DateUtils.getNowDate());
        return rtspDeviceMapper.insertRtspDevice(rtspDevice);
    }

    /**
     * 修改rtsp设备
     *
     * @param rtspDevice rtsp设备
     * @return 结果
     */
    @Override
    public int updateRtspDevice(RtspDevice rtspDevice) {
        requireDevice(rtspDevice.getId());
        scopedReader.scope(rtspDevice);
        // 生成rtsp地址
        String rtspUrl = "1".equals(rtspDevice.getPlayType())
                ? RtspTool.generateRtspUrl(rtspDevice) : rtspDevice.getUrl();
        if (rtspDevice != null) {
            rtspDevice.setUrl(rtspUrl);
            if ("1".equals(rtspDevice.getPlayType()) && rtspDevice.getPort() != null) {
                rtspDevice.setIp(RtspTool.normalizeHostPort(rtspDevice.getIp(), rtspDevice.getPort()));
            }
        }
        rtspDevice.setUpdateTime(DateUtils.getNowDate());
        int changed = rtspDeviceMapper.updateRtspDevice(rtspDevice);
        if (changed > 0) availability.invalidate("RTSP", String.valueOf(rtspDevice.getId()));
        return changed;
    }

    /**
     * 批量删除rtsp设备
     *
     * @param ids 需要删除的rtsp设备主键
     * @return 结果
     */
    @Override
    public int deleteRtspDeviceByIds(Long[] ids) {
        for (Long id : ids) requireDevice(id);
        RtspDevice scope = new RtspDevice(); scopedReader.scope(scope);
        return rtspDeviceMapper.deleteRtspDeviceByIds(ids, (String) scope.getParams().get("tenantId"),
                (String) scope.getParams().get("defaultTenantId"));
    }

    /**
     * 删除rtsp设备信息
     *
     * @param id rtsp设备主键
     * @return 结果
     */
    @Override
    public int deleteRtspDeviceById(Long id) {
        requireDevice(id);
        RtspDevice scope = new RtspDevice(); scopedReader.scope(scope);
        return rtspDeviceMapper.deleteRtspDeviceById(id, (String) scope.getParams().get("tenantId"),
                (String) scope.getParams().get("defaultTenantId"));
    }

    /**
     * 新增目标检测
     *
     * @param bo
     */
    @Override
    public void insertRtspDetection(RtspDeviceBo bo) throws OrtException, IOException {
        scopedReader.currentTenant();
        if (bo.getType().equals("detection")) {
            CameraDetectionTool.cameraDetection(bo);
            return;
        }
        throw new RuntimeException("暂不支持该类型");
    }

    @Override
    public void stopRtspDetection(RtspDeviceBo bo) throws OrtException, IOException {
        scopedReader.currentTenant();
        if (bo.getType().equals("detection")) {
            CameraDetectionTool.stopCameraDetection();
            return;
        }
        throw new RuntimeException("暂不支持该类型");
    }

    @Override
    public String alarmClock(alarmClockBo bo) {
        RtspDevice device = requireDevice(bo.getId());
        String firm = device.getFirm();
        if (firm.equals(StreamTypeConstants.HIKVISION)) {
            String startTime = this.formatAlarmTimeHIKVISION(bo.getStartTime());
            String endTime = this.formatAlarmTimeHIKVISION(bo.getEndTime());
            String url = "rtsp://" + device.getUserName() + ":" + device.getPassword() + "@" + device.getIp() + ":554/Streaming/tracks/" +
                    device.getChannel() + "?starttime=" + startTime + "&endtime=" + endTime;
            return url;
        } else if (firm.equals(StreamTypeConstants.DAHUA)) {
            String startTime = this.formatAlarmTimeDaHua(bo.getStartTime());
            String endTime = this.formatAlarmTimeDaHua(bo.getEndTime());
            String url = "rtsp://" + device.getUserName() + ":" + device.getPassword() + "@" + device.getIp() + ":554/cam/playback?channel=" +
                    device.getChannel() + "&subtype=1&starttime=" + startTime + "&endtime=" + endTime;
            return url;
        }
        throw new RuntimeException("暂不支持该厂商");
    }

    /**
     * 查询地图rtsp设备列表
     *
     * @param rtspDevice
     * @return
     */
    @Override
    public List<RtspDevice> selectRtspDeviceListMap(RtspDevice rtspDevice) {
        return decorate(scopedReader.forOperation(rtspDevice, true));
    }

    /**
     * 大华-格式化时间
     * @param date
     * @return
     */
    public static String formatAlarmTimeDaHua(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(date);
    }

    /**
     * HIKVISION-格式化时间
     * @param date
     * @return
     */
    public static String formatAlarmTimeHIKVISION(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }


}
