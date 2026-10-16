package com.ruoyi.rtsp.service;

import com.ruoyi.common.deviceclassification.service.DeviceClassificationScope;
import com.ruoyi.rtsp.domain.RtspDevice;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RtspClassificationScope implements DeviceClassificationScope {
    private final RtspDeviceScopedReader reader;
    public RtspClassificationScope(RtspDeviceScopedReader reader) { this.reader = reader; }
    @Override public String protocolType() { return "RTSP"; }
    @Override public List<String> visibleDeviceKeys() {
        return reader.list(new RtspDevice(), false).stream()
                .map(row -> String.valueOf(row.getId())).collect(Collectors.toList());
    }
}
