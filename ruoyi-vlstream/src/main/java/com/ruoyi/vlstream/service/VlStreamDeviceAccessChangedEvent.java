package com.ruoyi.vlstream.service;

/** Published only after the access transaction commits; grantId=null closes all device leases. */
public class VlStreamDeviceAccessChangedEvent {
    private final Long deviceRowId;
    private final Long grantId;

    public VlStreamDeviceAccessChangedEvent(Long deviceRowId, Long grantId) {
        this.deviceRowId = deviceRowId;
        this.grantId = grantId;
    }

    public Long getDeviceRowId() { return deviceRowId; }
    public Long getGrantId() { return grantId; }
}
