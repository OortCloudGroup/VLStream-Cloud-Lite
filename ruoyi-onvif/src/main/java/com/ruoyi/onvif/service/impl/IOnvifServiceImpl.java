package com.ruoyi.onvif.service.impl;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.onvif.domain.FetchMainAndSubStreamUris;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.AbsoluteMoveBo;
import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import com.ruoyi.onvif.domain.bo.OnvifPZT;
import com.ruoyi.onvif.domain.bo.PresetsBo;
import com.ruoyi.onvif.mapper.OnvifDeviceMapper;
import com.ruoyi.onvif.service.IOnvifService;
import com.ruoyi.onvif.utils.OnvifUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * onvif 接口实现类
 * @Author: 陈江灿
 * @CreateTime: 2025-04-21
 */
@Service
public class IOnvifServiceImpl implements IOnvifService {

    @Autowired
    private com.ruoyi.onvif.service.OnvifDeviceScopedReader scopedReader;

    private OnvifDevice requireDevice(Long id) {
        OnvifDevice query = new OnvifDevice(); query.setId(id);
        return scopedReader.forOperation(query, false).stream().filter(row -> id != null && id.equals(row.getId()))
                .findFirst().orElseThrow(() -> new com.ruoyi.common.exception.ServiceException("设备不存在或无权访问", 403));
    }

    /**
     * 获取设备信息
     * @param bo
     * @return
     */
    @Override
    public FetchMainAndSubStreamUris getOnvifDeviceInfo(FetchMainAndSubStreamUrisBo bo) {
        return OnvifUtil.getOnvifDeviceInfo(bo);
    }

    /**
     * 获取通道token
     */
    @Override
    public List<Map<String, String>> getChannelToken(FetchMainAndSubStreamUrisBo bo) {
        List<String> profileToken = OnvifUtil.getProfileToken(bo);
        List<Map<String, String>> options = profileToken.stream()
                .map(token -> {
                    Map<String, String> option = new HashMap<>();
                    option.put("value", token);
                    option.put("label", token);
                    return option;
                })
                .collect(Collectors.toList());
        return options;
    }

    /**
     * 绝对位置云台
     * @param bo
     * @return
     */
    @Override
    public R generateAbsoluteMove(AbsoluteMoveBo bo) {
        return OnvifUtil.absoluteMove(bo);
    }

    /**
     * 连续移动云台
     * @param bo
     * @return
     */
    @Override
    public R generateContinuousMove(AbsoluteMoveBo bo) {
        return OnvifUtil.continuousMove(bo);
    }

    /**
     * 连续移动停止
     * @param bo
     * @return
     */
    @Override
    public R continuousMoveStop(AbsoluteMoveBo bo) {
        return OnvifUtil.continuousMoveStop(bo);
    }

    /**
     * 获取预置点列表
     * @param bo
     * @return
     */
    @Override
    public List<Map<String, String>> getPresetList(AbsoluteMoveBo bo) {
        return OnvifUtil.getPresets(bo);
    }

    /**
     * goto预置点
     * @param bo
     */
    @Override
    public void gotoPreset(PresetsBo bo) {
        OnvifUtil.gotoPreset(bo);
    }

    /**
     * 删除预置点
     * @param bo
     */
    @Override
    public void removePreset(PresetsBo bo) {
        OnvifUtil.removePreset(bo);
    }

    /**
     * 添加预置点
     * @param bo
     */
    @Override
    public void addPreset(PresetsBo bo) {
        OnvifUtil.setPreset(bo);
    }

    /**
     * 云台开始
     *
     * @param onvifPZT
     */
    @Override
    public void onvifPZTStart(OnvifPZT onvifPZT) {
        OnvifDevice onvifDevice = requireDevice(onvifPZT.getId());
        OnvifUtil.onvifPZTStart(onvifDevice,onvifPZT);
    }

    /**
     * 云台结束
     *
     * @param onvifPZT
     */
    @Override
    public void onvifPZTEnd(OnvifPZT onvifPZT) {
        OnvifDevice onvifDevice = requireDevice(onvifPZT.getId());
        OnvifUtil.onvifPZTEnd(onvifDevice,onvifPZT);
    }

}
