package com.ruoyi.vlstream.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VlStreamPlaybackUtilsTest {
    @Test
    public void recognizesHttpCameraRtcEndpoint() {
        assertTrue(VlStreamPlaybackUtils.isCameraRtc("http",
                "http://146.56.220.167:8082/videocall/AETY-00-XOKU-L1A2-00000002"));
        assertTrue(VlStreamPlaybackUtils.isCameraRtc("https",
                "https://camera.example.com/gateway/videocall/CAM-1?token=test"));
    }

    @Test
    public void rejectsOrdinaryHttpAndMismatchedProtocols() {
        assertFalse(VlStreamPlaybackUtils.isCameraRtc("http", "http://camera.example.com/live.mp4"));
        assertFalse(VlStreamPlaybackUtils.isCameraRtc("rtsp", "http://camera.example.com/videocall/CAM-1"));
        assertFalse(VlStreamPlaybackUtils.isCameraRtc("http", "https://camera.example.com/videocall/CAM-1"));
        assertFalse(VlStreamPlaybackUtils.isCameraRtc("http", "not-a-url"));
    }

    @Test
    public void extractsOnlyValidatedCameraRtcTargetFromSavedPath() {
        assertEquals("CAM-1", VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/gateway/videocall/CAM-1?token=secret"));
        assertNull(VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/videocall/../admin"));
        assertNull(VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/videocall/invalid%2Ftarget"));
    }
}
