package com.ruoyi.video.mapper;

import com.ruoyi.video.domain.VideoDeviceQuery;
import com.ruoyi.video.domain.VideoDeviceSource;
import java.util.List;

public interface VideoDeviceMapper {
    List<VideoDeviceSource> selectSources(VideoDeviceQuery query);
}

