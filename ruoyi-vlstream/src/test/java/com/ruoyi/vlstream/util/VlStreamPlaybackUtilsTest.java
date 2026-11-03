package com.ruoyi.vlstream.util;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VlStreamPlaybackUtilsTest {
    @Test
    public void recognizesHttpCameraRtcEndpoint() {
        assertTrue(VlStreamPlaybackUtils.isCameraRtc("http",
                "http://camera.example.com:8082/videocall/AETY-00-XOKU-L1A2-00000002"));
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
        assertEquals("AETY-00-XOKU-L1A2-00000002", VlStreamPlaybackUtils.cameraRtcDeviceId("http",
                "http://camera.example.com:8082/videocall/AETY-00-XOKU-L1A2-00000002"));
        assertEquals("CAM-1", VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/gateway/videocall/CAM-1?token=secret"));
        assertEquals("CAM-1", VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/gate%20way/%E8%AE%BE%E5%A4%87/videocall/CAM-1"));
        assertEquals("CAM-1", VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/gateway/VideoCall//CAM-1/extra"));
        assertNull(VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/videocall/../admin"));
        assertNull(VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                "https://camera.example.com/videocall/invalid%2Ftarget"));
        for (String path : new String[] {"/videocall/A/videocall/B", "/videocall/A/videocall",
                "/gateway/../videocall/A", "/gateway/./videocall/A", "/videocall/A%2FB",
                "/%76ideocall/A", "/videocall/A%5cB", "/%2e%2e/videocall/A",
                "/foo%2fbar/videocall/A", "/foo%255cbar/videocall/A"}) {
            assertNull(path, VlStreamPlaybackUtils.cameraRtcDeviceId("https", "https://camera.example.com" + path));
        }
    }

    @Test
    public void markerMatchingDoesNotDependOnSystemLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("CAM-1", VlStreamPlaybackUtils.cameraRtcDeviceId("https",
                    "https://camera.example.com/VIDEOCALL/CAM-1"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
