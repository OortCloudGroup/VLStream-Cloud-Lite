package com.ruoyi.video.service;

import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.video.config.VideoDeviceIdentityProperties;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VideoDeviceCatalogTest {
    static VideoDeviceSource source(String protocol, String row, String id, String serial, String ip, String state) {
        VideoDeviceSource source = new VideoDeviceSource();
        source.setProtocol(protocol); source.setRowId(row); source.setDeviceId(id);
        source.setSerial(serial); source.setIpAddress(ip); source.setState(state);
        source.setDeviceName(protocol + "-" + row);
        return source;
    }

    @Test public void mergesSameProtocolIdentityAndSerialTransitivelyBeforePaging() {
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(
            source("ISUP", "1", "device-a", "serial-a", null, "OFFLINE"),
            source("ISUP", "2", "device-a", null, null, "ON"),
            source("ISUP", "3", "different-id", "serial-a", null, "OFFLINE")));
        assertEquals(1, devices.size());
        assertEquals("ISUP:1", devices.get(0).get("id"));
        assertEquals("ONLINE", devices.get(0).get("status"));
        assertEquals(3, ((List<?>) devices.get(0).get("sources")).size());
    }

    @Test public void verifiedSourceMappingMergesDifferentProtocolsAndTheirDuplicateRows() {
        Map<String, String> links = new HashMap<>();
        links.put("VLSTREAM:1", "verified-camera-a"); links.put("ISUP:1", "verified-camera-a");
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(
            source("VLSTREAM", "1", "vls-a", "serial-a", null, "OFFLINE"),
            source("ISUP", "1", "isup-a", "other-serial", null, "ON"),
            source("ISUP", "2", "isup-a", null, null, "OFFLINE")), links);
        assertEquals(1, devices.size()); assertEquals("VLSTREAM:1", devices.get(0).get("id"));
        assertEquals("ONLINE", devices.get(0).get("status"));
        assertEquals(3, ((List<?>) devices.get(0).get("sources")).size());
    }

    @Test public void equalIdsAndReportedSerialsDoNotMergeIndependentProtocolNamespaces() {
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(
            source("VLSTREAM", "1", "same", "same-serial", null, "OFFLINE"),
            source("CUSTOM", "1", "same", "same-serial", null, "ONLINE"),
            source("GB28181", "1", "same", "same-serial", null, "OFFLINE")));
        assertEquals(3, devices.size());
        assertEquals("ONLINE", devices.get(0).get("status")); // CUSTOM has its own state.
        assertEquals("OFFLINE", devices.get(2).get("status")); // VLSTREAM is still offline.
        Map<?, ?> stats = (Map<?, ?>) VideoDeviceCatalog.page(devices, 1, 1, null).get("stats");
        assertEquals(3, stats.get("total")); assertEquals(1L, stats.get("online")); assertEquals(2L, stats.get("offline"));
    }

    @Test public void onvifRtspEqualAddressRequiresCrossProtocolMapping() {
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(
            source("ONVIF", "1", null, null, "192.168.1.11", null),
            source("RTSP", "1", null, null, "192.168.1.11", null),
            source("RTSP", "2", null, null, "192.168.1.11", null)));
        assertEquals(2, devices.size());
    }

    @Test public void countsMappedOnvifRtspDeviceAcrossChannelsButNotGatewayDevices() {
        Map<String, String> links = new HashMap<>();
        links.put("ONVIF:1", "verified-camera-a"); links.put("RTSP:1", "verified-camera-a");
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(
            source("ONVIF", "1", null, null, "192.168.1.11", null),
            source("RTSP", "1", null, null, "192.168.1.11", null),
            source("RTSP", "2", null, null, "192.168.1.11", null),
            source("GB28181", "1", "gb-1", null, "192.168.1.11", "ONLINE"),
            source("GB28181", "2", "gb-2", null, "192.168.1.11", "OFFLINE")), links);
        assertEquals(3, devices.size());
        Map<?, ?> stats = (Map<?, ?>) VideoDeviceCatalog.page(devices, 1, 1, null).get("stats");
        assertEquals(3, stats.get("total"));
        assertEquals(1L, stats.get("online")); assertEquals(1L, stats.get("offline")); assertEquals(1L, stats.get("unknown"));
    }

    @Test public void missingIdsDoNotCollideAcrossTablesAndUnknownIsNotOffline() {
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(
            source("RTSP", "1", null, null, null, null), source("ONVIF", "1", null, null, null, null)));
        assertEquals(2, devices.size());
        assertEquals("UNKNOWN", devices.get(0).get("status"));
        assertEquals("OFFLINE", VideoDeviceCatalog.state("0"));
        assertEquals("UNKNOWN", VideoDeviceCatalog.state("unreported"));
    }

    @Test public void paginationUsesLogicalTotalsAndDoesNotFilterUnlocatedOrOffline() {
        VideoDeviceSource a = source("CUSTOM", "1", "a", null, null, "OFFLINE");
        a.setLatitude("91"); a.setLongitude("116");
        VideoDeviceSource b = source("VLSTREAM", "1", "b", null, null, "ONLINE");
        b.setLatitude("0"); b.setLongitude("0");
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(a, b));
        Map<String, Object> page = VideoDeviceCatalog.page(devices, 2, 1, "");
        assertEquals(2, page.get("total")); assertEquals(1, ((List<?>) page.get("rows")).size());
        assertEquals(1L, ((Map<?, ?>) page.get("stats")).get("located"));
        assertNull(VideoDeviceCatalog.coordinates("NaN", "12"));
        assertNull(VideoDeviceCatalog.coordinates("12degrees", "12"));
        assertNull(VideoDeviceCatalog.coordinates(null, "12"));
        assertEquals(2, VideoDeviceCatalog.page(devices, Integer.MAX_VALUE, 500, "").get("total"));
        assertTrue(((List<?>) VideoDeviceCatalog.page(devices, Integer.MAX_VALUE, 500, "").get("rows")).isEmpty());
    }

    @Test public void mixedOfflineAndUnreportedSourcesRemainUnknown() {
        Map<String, String> links = new HashMap<>();
        links.put("VLSTREAM:1", "verified-camera-a"); links.put("CUSTOM:1", "verified-camera-a");
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(Arrays.asList(
            source("VLSTREAM", "1", "same", null, null, "OFFLINE"),
            source("CUSTOM", "1", "different", null, null, null)), links);
        assertEquals("UNKNOWN", devices.get(0).get("status"));
        assertEquals(1, VideoDeviceCatalog.page(devices, 1, 100, "same").get("total"));
        assertEquals(0, VideoDeviceCatalog.page(devices, 1, 100, "absent").get("total"));
    }

    @Test public void operatorMappingsAreEmptyByDefaultAndRejectConflictingMembership() {
        VideoDeviceIdentityProperties properties = new VideoDeviceIdentityProperties();
        assertTrue(properties.sourceIdentities().isEmpty());
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("camera-a", Arrays.asList("VLSTREAM:1", "CUSTOM:1"));
        properties.setIdentityGroups(groups);
        assertEquals(properties.sourceIdentities().get("VLSTREAM:1"), properties.sourceIdentities().get("CUSTOM:1"));
        groups.put("camera-b", Collections.singletonList("CUSTOM:1"));
        try { properties.sourceIdentities(); fail("Conflicting source links must not be accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void onlineFirstSortingUsesMergedStateAndStableIdsBeforePagination() {
        Map<String, String> links = new HashMap<>();
        links.put("VLSTREAM:9", "camera-a"); links.put("RTSP:7", "camera-a");
        List<VideoDeviceSource> sources = Arrays.asList(
            source("CUSTOM", "1", "offline", null, null, "OFFLINE"),
            source("VLSTREAM", "9", "merged", null, null, "OFFLINE"),
            source("RTSP", "7", null, null, "192.168.1.7", "ONLINE"),
            source("RTSP", "8", null, null, "192.168.1.7", null),
            source("GB28181", "3", "online", null, null, "ONLINE"),
            source("ONVIF", "4", null, null, null, null));
        List<Map<String, Object>> devices = VideoDeviceCatalog.consolidate(sources, links);
        assertEquals(4, devices.size());
        assertEquals(Arrays.asList("GB28181:3", "VLSTREAM:9", "ONVIF:4", "CUSTOM:1"), ids(devices));
        List<VideoDeviceSource> reversed = new ArrayList<>(sources);
        Collections.reverse(reversed);
        assertEquals(ids(devices), ids(VideoDeviceCatalog.consolidate(reversed, links)));
        Collections.reverse(devices);
        Map<String, Object> first = VideoDeviceCatalog.page(devices, 1, 2, null);
        assertEquals(Arrays.asList("GB28181:3", "VLSTREAM:9"), ids((List<Map<String, Object>>) first.get("rows")));
        assertEquals(4, first.get("total"));
        assertEquals(2L, ((Map<?, ?>) first.get("stats")).get("online"));
        assertEquals(Arrays.asList("ONVIF:4", "CUSTOM:1"), ids((List<Map<String, Object>>)
            VideoDeviceCatalog.page(devices, 2, 2, null).get("rows")));
        assertEquals("CUSTOM:1", devices.get(0).get("id")); // Input order is not mutated by page().
    }

    private static List<String> ids(List<Map<String, Object>> devices) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> device : devices) ids.add(String.valueOf(device.get("id")));
        return ids;
    }
}

