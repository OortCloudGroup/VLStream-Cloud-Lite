package com.ruoyi.onvif.service;

import com.ruoyi.common.deviceclassification.service.DeviceClassificationScope;
import com.ruoyi.onvif.domain.OnvifDevice;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OnvifClassificationScope implements DeviceClassificationScope {
    private final OnvifDeviceScopedReader reader;
    public OnvifClassificationScope(OnvifDeviceScopedReader reader) { this.reader = reader; }
    @Override public String protocolType() { return "ONVIF"; }
    @Override public List<String> visibleDeviceKeys() {
        return reader.list(new OnvifDevice(), false).stream()
                .map(row -> String.valueOf(row.getId())).collect(Collectors.toList());
    }
}
