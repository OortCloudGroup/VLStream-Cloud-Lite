package com.ruoyi.common.deviceclassification.service;

import java.util.List;

/** Supplies classification counts/assignments with the owning protocol's authorized directory. */
public interface DeviceClassificationScope {
    String protocolType();
    List<String> visibleDeviceKeys();
}
