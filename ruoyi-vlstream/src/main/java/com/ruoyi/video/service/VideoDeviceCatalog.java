package com.ruoyi.video.service;

import com.ruoyi.video.domain.VideoDeviceSource;
import java.util.*;
import java.util.stream.Collectors;

/** Consolidates permitted device records before pagination. Never counts stream/channel rows. */
public final class VideoDeviceCatalog {
    private VideoDeviceCatalog() { }

    private static final Comparator<Map<String, Object>> DEVICE_ORDER = Comparator
        .comparingInt((Map<String, Object> device) -> statusRank(device.get("status")))
        .thenComparing(device -> String.valueOf(device.get("id")));

    private static int statusRank(Object status) {
        return "ONLINE".equals(status) ? 0 : "OFFLINE".equals(status) ? 2 : 1;
    }

    public static List<Map<String, Object>> consolidate(List<VideoDeviceSource> sources) {
        return consolidate(sources, Collections.emptyMap());
    }

    public static List<Map<String, Object>> consolidate(List<VideoDeviceSource> sources, Map<String, String> trustedIdentities) {
        int[] parents = new int[sources.size()];
        Map<String, Integer> identities = new HashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            parents[i] = i;
            for (String identity : identities(sources.get(i), trustedIdentities)) {
                Integer previous = identities.putIfAbsent(identity, i);
                if (previous != null) parents[find(parents, i)] = find(parents, previous);
            }
        }
        Map<Integer, List<VideoDeviceSource>> groups = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            groups.computeIfAbsent(find(parents, i), key -> new ArrayList<>()).add(sources.get(i));
        }
        List<Map<String, Object>> devices = new ArrayList<>();
        for (List<VideoDeviceSource> group : groups.values()) {
            group.sort(Comparator.comparing((VideoDeviceSource source) -> !"VLSTREAM".equals(source.getProtocol()))
                .thenComparing(VideoDeviceSource::getProtocol).thenComparing(VideoDeviceSource::getRowId));
            VideoDeviceSource primary = group.get(0);
            Map<String, Object> device = new LinkedHashMap<>();
            device.put("id", primary.getProtocol() + ":" + primary.getRowId());
            device.put("deviceId", first(group, VideoDeviceSource::getDeviceId));
            device.put("deviceName", Optional.ofNullable(first(group, VideoDeviceSource::getDeviceName))
                .orElse(primary.getProtocol() + " " + primary.getRowId()));
            device.put("ipAddress", first(group, VideoDeviceSource::getIpAddress));
            device.put("deviceType", first(group, VideoDeviceSource::getDeviceType));
            device.put("address", first(group, VideoDeviceSource::getAddress));
            List<String> states = group.stream().map(source -> state(source.getState())).collect(Collectors.toList());
            device.put("status", states.contains("ONLINE") ? "ONLINE" :
                states.stream().allMatch("OFFLINE"::equals) ? "OFFLINE" : "UNKNOWN");
            double[] coordinates = group.stream().map(source -> coordinates(source.getLatitude(), source.getLongitude()))
                .filter(Objects::nonNull).findFirst().orElse(null);
            device.put("latitude", coordinates == null ? null : coordinates[0]);
            device.put("longitude", coordinates == null ? null : coordinates[1]);
            device.put("protocols", group.stream().map(VideoDeviceSource::getProtocol).distinct().collect(Collectors.toList()));
            device.put("sources", group);
            devices.add(device);
        }
        devices.sort(DEVICE_ORDER);
        return devices;
    }

    private static String first(List<VideoDeviceSource> group, java.util.function.Function<VideoDeviceSource, String> getter) {
        return group.stream().map(getter).map(VideoDeviceCatalog::clean).filter(value -> !value.isEmpty()).findFirst().orElse(null);
    }

    private static List<String> identities(VideoDeviceSource source, Map<String, String> trustedIdentities) {
        List<String> keys = new ArrayList<>();
        String sourceKey = source.getProtocol() + ":" + source.getRowId();
        String physicalIdentity = clean(trustedIdentities.get(sourceKey));
        if (!physicalIdentity.isEmpty()) keys.add("physical:" + physicalIdentity);
        String id = clean(source.getDeviceId());
        String serial = clean(source.getSerial());
        // IDs and reported serials belong to each protocol's namespace. Equal text alone
        // never proves cross-protocol physical identity, including custom device codes.
        if (!id.isEmpty()) keys.add("device:" + source.getProtocol() + ":" + id);
        if (!serial.isEmpty()) keys.add("serial:" + source.getProtocol() + ":" + serial);
        // Multiple directly addressed channel records may share an endpoint within a protocol.
        // Cross-protocol endpoint links require an explicit trusted source mapping too.
        String ip = clean(source.getIpAddress()).toLowerCase(Locale.ROOT);
        if (Arrays.asList("RTSP", "ONVIF").contains(source.getProtocol()) && !ip.isEmpty()) keys.add("endpoint:" + source.getProtocol() + ":" + ip);
        if (keys.isEmpty()) keys.add("row:" + sourceKey);
        return keys;
    }

    private static int find(int[] parents, int index) {
        if (parents[index] != index) parents[index] = find(parents, parents[index]);
        return parents[index];
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public static String state(String raw) {
        String value = clean(raw).toUpperCase(Locale.ROOT);
        if (Arrays.asList("ONLINE", "ON", "1", "TRUE").contains(value)) return "ONLINE";
        if (Arrays.asList("OFFLINE", "OFF", "0", "FALSE").contains(value)) return "OFFLINE";
        return "UNKNOWN";
    }

    public static double[] coordinates(String latitude, String longitude) {
        try {
            double lat = Double.parseDouble(clean(latitude));
            double lng = Double.parseDouble(clean(longitude));
            return Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180
                ? new double[]{lat, lng} : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    public static Map<String, Object> page(List<Map<String, Object>> devices, int pageNum, int pageSize, String keyword) {
        String search = clean(keyword).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matches = devices.stream().filter(device -> search.isEmpty() ||
            Arrays.asList("deviceName", "deviceId", "ipAddress").stream()
                .anyMatch(key -> String.valueOf(device.get(key)).toLowerCase(Locale.ROOT).contains(search)))
            .collect(Collectors.toList());
        // Permission-scoped logical devices have already been consolidated. Sort the
        // complete filtered set here too, before slicing, so page boundaries stay stable.
        matches.sort(DEVICE_ORDER);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", matches.size());
        for (String state : Arrays.asList("ONLINE", "OFFLINE", "UNKNOWN")) {
            stats.put(state.toLowerCase(Locale.ROOT), matches.stream().filter(device -> state.equals(device.get("status"))).count());
        }
        stats.put("located", matches.stream().filter(device -> device.get("latitude") != null).count());
        int size = Math.max(1, Math.min(500, pageSize));
        long offset = (long) (Math.max(1, pageNum) - 1) * size;
        int from = (int) Math.min(matches.size(), offset);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", matches.subList(from, Math.min(matches.size(), from + size)));
        result.put("total", matches.size());
        result.put("stats", stats);
        return result;
    }
}

