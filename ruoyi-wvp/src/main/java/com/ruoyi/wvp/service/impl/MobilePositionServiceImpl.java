package com.ruoyi.wvp.service.impl;


import com.ruoyi.wvp.conf.UserSetting;
import com.ruoyi.wvp.gb28181.bean.DeviceChannel;
import com.ruoyi.wvp.gb28181.bean.MobilePosition;
import com.ruoyi.wvp.gb28181.bean.Platform;
import com.ruoyi.wvp.mapper.DeviceChannelMapper;
import com.ruoyi.wvp.mapper.DeviceMobilePositionMapper;
import com.ruoyi.wvp.mapper.PlatformMapper;
import com.ruoyi.wvp.service.IMobilePositionService;
import com.ruoyi.wvp.service.bean.GPSMsgInfo;
import com.ruoyi.wvp.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MobilePositionServiceImpl implements IMobilePositionService {

    @Autowired
    private DeviceChannelMapper channelMapper;

    @Autowired
    private DeviceMobilePositionMapper mobilePositionMapper;

    @Autowired
    private UserSetting userSetting;


    @Autowired
    private PlatformMapper platformMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(MobilePosition mobilePosition) {
        List<MobilePosition> list = new ArrayList<>();
        list.add(mobilePosition);
        add(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(List<MobilePosition> mobilePositionList) {
        if (mobilePositionList == null || mobilePositionList.isEmpty()) return;
        // Bound each SQL batch while keeping the whole incoming report in one transaction.
        for (int start = 0; start < mobilePositionList.size(); start += 3000) {
            persistPositions(mobilePositionList.subList(start, Math.min(start + 3000, mobilePositionList.size())));
        }
    }



    /**
     * 查询移动位置轨迹
     */
    @Override
    public synchronized List<MobilePosition> queryMobilePositions(String deviceId, String channelId, String startTime, String endTime) {
        return mobilePositionMapper.queryPositionByDeviceIdAndTime(deviceId, channelId, startTime, endTime);
    }

    @Override
    public List<Platform> queryEnablePlatformListWithAsMessageChannel() {
        return platformMapper.queryEnablePlatformListWithAsMessageChannel();
    }

    /**
     * 查询最新移动位置
     * @param deviceId
     */
    @Override
    public MobilePosition queryLatestPosition(String deviceId) {
        return mobilePositionMapper.queryLatestPositionByDevice(deviceId);
    }

    @Override
    public void updateStreamGPS(List<GPSMsgInfo> gpsMsgInfoList) {
        channelMapper.updateStreamGPS(gpsMsgInfoList);
    }

    private void persistPositions(List<MobilePosition> mobilePositions) {
        if (userSetting.getSavePositionHistory()) {
            mobilePositionMapper.batchadd(mobilePositions);
        }
        log.info("[移动位置订阅]更新通道位置： {}", mobilePositions.size());
        Map<String, DeviceChannel> updateChannelMap = new HashMap<>();
        for (MobilePosition mobilePosition : mobilePositions) {
            DeviceChannel deviceChannel = new DeviceChannel();
            deviceChannel.setId(mobilePosition.getChannelId());
            deviceChannel.setDeviceId(mobilePosition.getDeviceId());
            deviceChannel.setLongitude(mobilePosition.getLongitude());
            deviceChannel.setLatitude(mobilePosition.getLatitude());
            deviceChannel.setGpsTime(mobilePosition.getTime());
            deviceChannel.setUpdateTime(DateUtil.getNow());
            updateChannelMap.put(mobilePosition.getDeviceId() + ":" + mobilePosition.getChannelId(), deviceChannel);
        }
        List<DeviceChannel> channels = new ArrayList<>(updateChannelMap.values());
        channelMapper.batchUpdatePosition(channels);
    }

}
