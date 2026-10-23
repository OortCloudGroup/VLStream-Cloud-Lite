package com.ruoyi.vlstream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Allowlisted CameraRTC protocol projection. Device credentials and private ICE addresses never cross the relay. */
public final class VlStreamCameraRtcMessagePolicy {
    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern STUN_URL = Pattern.compile("(?i)^stuns?:([^:/?#]+)(?::[0-9]{1,5})?(?:\\?.*)?$");
    private static final Set<String> CLIENT_EVENTS = new HashSet<>(Arrays.asList(
        "__connectto", "__call", "__answer", "__ice_candidate", "__ping", "__disconnected"));
    private static final Set<String> SERVER_EVENTS = new HashSet<>(Arrays.asList(
        "_create", "_offer", "_ice_candidate", "_session_failed", "_session_disconnected", "_connectinfo"));

    private VlStreamCameraRtcMessagePolicy() { }

    public static boolean isClientEvent(String event) { return CLIENT_EVENTS.contains(event); }
    public static boolean isServerEvent(String event) { return SERVER_EVENTS.contains(event); }
    public static boolean validSessionId(String value) { return value != null && SESSION_ID.matcher(value).matches(); }

    /** Builds a new upstream message; caller supplied target, sender, credentials and unknown fields are discarded. */
    public static ObjectNode clientMessage(ObjectMapper mapper, JsonNode root, String cameraId,
            String relayId, String sessionId) throws IOException {
        if (root == null || !root.isObject()) return null;
        String event = root.path("eventName").asText("");
        if (!CLIENT_EVENTS.contains(event)) return null;
        JsonNode source = root.path("data");
        if (!source.isObject() || !sessionId.equals(source.path("sessionId").asText())) return null;

        ObjectNode message = mapper.createObjectNode();
        message.put("eventName", event);
        ObjectNode data = message.putObject("data");
        data.put("sessionId", sessionId);
        data.put("sessionType", "IE");
        data.put("messageId", java.util.UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        data.put("from", relayId);
        data.put("to", cameraId);

        if ("__call".equals(event)) {
            data.put("audio", "recvonly");
            data.put("video", "recvonly");
            // A signaling relay cannot enforce read-only commands on an SCTP data channel.
            data.put("datachannel", "false");
            data.put("mode", "live");
            data.put("source", "MainStream");
            JsonNode ice = source.get("iceservers");
            if (ice != null && ice.isTextual()) {
                String safe = sanitizeIceServers(mapper, ice.asText());
                if (safe != null) data.put("iceservers", safe);
            }
        } else if ("__answer".equals(event)) {
            String type = source.path("type").asText("");
            String sdp = source.path("sdp").asText("");
            if (!"answer".equalsIgnoreCase(type) || sdp.length() > 32768) return null;
            String safeSdp = sanitizeSdp(sdp);
            if (safeSdp.isEmpty()) return null;
            data.put("type", "answer");
            data.put("sdp", safeSdp);
        } else if ("__ice_candidate".equals(event)) {
            JsonNode candidate = candidateNode(mapper, source.get("candidate"));
            if (candidate == null || candidate.path("candidate").asText("").isEmpty()
                    || isPrivateCandidate(candidate.path("candidate").asText())) return null;
            data.put("candidate", mapper.writeValueAsString(safeCandidate(mapper, candidate)));
        }
        return message;
    }

    /** Projects only fields the in-tree player consumes and filters addresses/ICE credentials first. */
    public static ObjectNode serverMessage(ObjectMapper mapper, JsonNode root, String expectedSessionId) throws IOException {
        if (root == null || !root.isObject()) return null;
        String event = root.path("eventName").asText("");
        if (!SERVER_EVENTS.contains(event)) return null;
        JsonNode source = root.path("data");
        if (!source.isObject() || !expectedSessionId.equals(source.path("sessionId").asText())) return null;

        ObjectNode message = mapper.createObjectNode();
        message.put("eventName", event);
        ObjectNode data = message.putObject("data");
        data.put("sessionId", expectedSessionId);
        if ("_create".equals(event)) {
            copyText(source, data, "state", 32);
            copySafeMessage(source, data);
            for (String key : Arrays.asList("iceServers", "domainnameiceServers", "domainnameIceServers")) {
                JsonNode ice = source.get(key);
                if (ice != null) {
                    String safe = sanitizeIceServers(mapper, ice.isTextual() ? ice.asText() : mapper.writeValueAsString(ice));
                    if (safe != null) data.put(key, safe);
                }
            }
        } else if ("_offer".equals(event)) {
            String sdp = source.path("sdp").asText("");
            if (sdp.length() > 32768) return null;
            String safeSdp = sanitizeSdp(sdp);
            if (safeSdp.isEmpty()) return null;
            copyText(source, data, "type", 16);
            data.put("sdp", safeSdp);
        } else if ("_ice_candidate".equals(event)) {
            JsonNode candidate = candidateNode(mapper, source.get("candidate"));
            if (candidate == null || candidate.path("candidate").asText("").isEmpty()
                    || isPrivateCandidate(candidate.path("candidate").asText())) return null;
            data.put("candidate", mapper.writeValueAsString(safeCandidate(mapper, candidate)));
        } else if ("_session_failed".equals(event)) {
            data.put("message", "call time out".equals(source.path("message").asText()) ? "call time out" : "session failed");
        } else if ("_session_disconnected".equals(event)) {
            data.put("message", "session disconnected");
        } else if ("_connectinfo".equals(event)) {
            String type = "direct";
            String raw = source.path("message").asText("");
            try {
                JsonNode info = mapper.readTree(raw);
                if ("relay".equalsIgnoreCase(info.path("type").asText())) type = "relay";
            } catch (IOException ignored) { }
            ObjectNode info = mapper.createObjectNode();
            info.put("type", type);
            data.put("message", mapper.writeValueAsString(info));
        }
        return message;
    }

    public static String sanitizeIceServers(ObjectMapper mapper, String raw) {
        if (raw == null || raw.length() > 8192) return null;
        try {
            JsonNode configuration = mapper.readTree(raw);
            JsonNode servers = configuration.path("iceServers");
            if (!servers.isArray()) return null;
            ObjectNode safe = mapper.createObjectNode();
            ArrayNode safeServers = safe.putArray("iceServers");
            for (JsonNode server : servers) {
                JsonNode urls = server.get("urls");
                if (urls == null) continue;
                ArrayNode safeUrls = mapper.createArrayNode();
                if (urls.isArray()) {
                    for (JsonNode url : urls) if (url.isTextual() && isSafeStun(url.asText())) safeUrls.add(url.asText());
                } else if (urls.isTextual() && isSafeStun(urls.asText())) {
                    safeUrls.add(urls.asText());
                }
                if (safeUrls.size() > 0) safeServers.addObject().set("urls", safeUrls);
            }
            safe.put("iceTransportPolicy", "all");
            safe.put("iceCandidatePoolSize", 0);
            return mapper.writeValueAsString(safe);
        } catch (IOException invalid) {
            return null;
        }
    }

    public static String sanitizeSdp(String sdp) {
        if (sdp == null || sdp.isEmpty()) return "";
        StringBuilder result = new StringBuilder(sdp.length());
        String[] lines = sdp.split("\\r\\n|\\n|\\r", -1);
        for (String line : lines) {
            String normalized = line.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("m=application") || normalized.startsWith("a=sctp")
                    || normalized.startsWith("a=dcmap:") || normalized.startsWith("a=dcsa:")
                    || normalized.startsWith("a=max-message-size:")
                    || normalized.startsWith("m=") && normalized.contains("sctp")) return "";
        }
        for (String line : lines) {
            if (line.startsWith("a=remote-candidates:")) continue;
            if (line.startsWith("a=candidate:") && isPrivateCandidate(line.substring(2))) continue;
            if (line.startsWith("c=")) {
                String[] fields = line.substring(2).trim().split("\\s+");
                if (fields.length != 3 || !"IN".equalsIgnoreCase(fields[0])
                        || !("IP4".equalsIgnoreCase(fields[1]) || "IP6".equalsIgnoreCase(fields[1]))) continue;
                fields[2] = publicSdpAddress(fields[1], fields[2]);
                line = "c=" + fields[0] + " " + fields[1] + " " + fields[2];
            } else if (line.startsWith("o=")) {
                String[] fields = line.split("\\s+");
                if (fields.length != 6 || !"IN".equalsIgnoreCase(fields[3])
                        || !("IP4".equalsIgnoreCase(fields[4]) || "IP6".equalsIgnoreCase(fields[4]))) continue;
                fields[5] = publicSdpAddress(fields[4], fields[5]);
                line = String.join(" ", fields);
            } else if (line.startsWith("a=rtcp:")) {
                String[] fields = line.substring("a=rtcp:".length()).trim().split("\\s+");
                if (!boundedNumber(fields[0], 1, 65535)) continue;
                if (fields.length == 4 && "IN".equalsIgnoreCase(fields[1])
                        && ("IP4".equalsIgnoreCase(fields[2]) || "IP6".equalsIgnoreCase(fields[2]))) {
                    fields[3] = publicSdpAddress(fields[2], fields[3]);
                } else if (fields.length != 1) continue;
                line = "a=rtcp:" + String.join(" ", fields);
            } else if (line.startsWith("a=source-filter:")) {
                // Multicast/source-filter metadata is unnecessary for the shared WebRTC receive-only flow.
                continue;
            }
            result.append(line).append("\r\n");
        }
        return result.toString();
    }

    public static boolean isPrivateCandidate(String candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.length() > 2048
                || candidate.indexOf('\r') >= 0 || candidate.indexOf('\n') >= 0) return true;
        String[] fields = candidate.trim().split("\\s+");
        String first = fields.length == 0 ? "" : fields[0].startsWith("a=") ? fields[0].substring(2) : fields[0];
        if (fields.length < 8 || (fields.length - 8) % 2 != 0 || !first.matches("candidate:[A-Za-z0-9+/]{1,32}")
                || !boundedNumber(fields[1], 1, 256) || !("udp".equalsIgnoreCase(fields[2]) || "tcp".equalsIgnoreCase(fields[2]))
                || !boundedNumber(fields[3], 0, 4294967295L) || !boundedNumber(fields[5], 1, 65535)
                || !"typ".equalsIgnoreCase(fields[6])
                || !Arrays.asList("host", "srflx", "prflx", "relay").contains(fields[7].toLowerCase(Locale.ROOT))) return true;
        InetAddress address = literalAddress(fields[4]);
        if (address == null || isNonPublicAddress(address)) return true;
        Set<String> extensions = new HashSet<>();
        for (int i = 8; i + 1 < fields.length; i += 2) {
            String key = fields[i].toLowerCase(Locale.ROOT);
            String value = fields[i + 1];
            if (!extensions.add(key)) return true;
            if ("raddr".equals(key)) {
                InetAddress related = literalAddress(value);
                if (related == null || isNonPublicAddress(related)) return true;
            } else if ("rport".equals(key)) {
                if (!boundedNumber(value, 1, 65535)) return true;
            } else if ("tcptype".equals(key)) {
                if (!"tcp".equalsIgnoreCase(fields[2]) || !Arrays.asList("active", "passive", "so").contains(value.toLowerCase(Locale.ROOT))) return true;
            } else if (Arrays.asList("generation", "network-id", "network-cost").contains(key)) {
                if (!boundedNumber(value, 0, 4294967295L)) return true;
            } else if ("ufrag".equals(key)) {
                if (!value.matches("[A-Za-z0-9+/_-]{1,256}")) return true;
            } else return true;
        }
        return extensions.contains("raddr") != extensions.contains("rport")
            || "tcp".equalsIgnoreCase(fields[2]) && !extensions.contains("tcptype");
    }

    private static boolean isSafeStun(String url) {
        Matcher matcher = STUN_URL.matcher(url == null ? "" : url.trim());
        return matcher.matches() && !isPrivateAddress(matcher.group(1));
    }

    private static boolean isPrivateAddress(String address) {
        if (address == null || address.isEmpty()) return true;
        String value = address.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        InetAddress literal = literalAddress(value);
        if (literal != null) return isNonPublicAddress(literal);
        if (value.indexOf(':') >= 0 || value.matches("[0-9.]+")) return true;
        for (String suffix : Arrays.asList(".local", ".localhost", ".localdomain", ".internal", ".intranet", ".lan", ".home",
                ".home.arpa", ".corp", ".invalid", ".test")) if (value.endsWith(suffix)) return true;
        return !value.contains(".") || !value.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+");
    }

    /** Parse literals only; never resolve a candidate or SDP hostname through DNS. */
    private static InetAddress literalAddress(String value) {
        if (value == null || value.isEmpty() || value.indexOf('%') >= 0) return null;
        try {
            if (value.indexOf(':') >= 0) {
                if (!value.matches("[0-9a-fA-F:.]+")) return null;
                return InetAddress.getByName(value);
            }
            String[] octets = value.split("\\.", -1);
            if (octets.length != 4) return null;
            byte[] bytes = new byte[4];
            for (int i = 0; i < octets.length; i++) {
                if (!octets[i].matches("0|[1-9][0-9]{0,2}")) return null;
                int octet = Integer.parseInt(octets[i]);
                if (octet > 255) return null;
                bytes[i] = (byte) octet;
            }
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException | NumberFormatException invalid) { return null; }
    }

    private static boolean isNonPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
            return a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && b == 254
                || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168 || a == 100 && b >= 64 && b <= 127
                || a == 192 && b == 0 && (c == 0 || c == 2) || a == 198 && (b == 18 || b == 19 || b == 51 && c == 100)
                || a == 203 && b == 0 && c == 113;
        }
        if (bytes.length != 16) return true;
        // Only global unicast IPv6 is eligible; JDK-normalized mapped IPv4 uses the IPv4 policy above.
        if ((bytes[0] & 0xe0) != 0x20) return true;
        // Transition addresses embed other endpoints; do not expose their embedded address data to viewers.
        if ((bytes[0] & 255) == 0x20 && ((bytes[1] & 255) == 0x02
                || (bytes[1] & 255) == 0x01 && bytes[2] == 0 && bytes[3] == 0)) return true;
        return (bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 0x01
            && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8;
    }

    private static String publicSdpAddress(String family, String value) {
        InetAddress address = literalAddress(value);
        int expectedBytes = "IP6".equalsIgnoreCase(family) ? 16 : 4;
        return address != null && address.getAddress().length == expectedBytes && !isNonPublicAddress(address)
            ? value : expectedBytes == 16 ? "::" : "0.0.0.0";
    }

    private static boolean boundedNumber(String value, long minimum, long maximum) {
        if (value == null || !value.matches("[0-9]{1,10}")) return false;
        try { long number = Long.parseLong(value); return number >= minimum && number <= maximum; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static JsonNode candidateNode(ObjectMapper mapper, JsonNode value) {
        if (value == null) return null;
        try {
            JsonNode candidate = value.isTextual() ? mapper.readTree(value.asText()) : value;
            return candidate.isObject() ? candidate : null;
        } catch (IOException invalid) { return null; }
    }

    private static ObjectNode safeCandidate(ObjectMapper mapper, JsonNode source) {
        ObjectNode candidate = mapper.createObjectNode();
        copyText(source, candidate, "candidate", 2048);
        copyText(source, candidate, "sdpMid", 64);
        if (source.path("sdpMLineIndex").canConvertToInt()) candidate.put("sdpMLineIndex", source.path("sdpMLineIndex").intValue());
        copyText(source, candidate, "usernameFragment", 128);
        return candidate;
    }

    private static void copySafeMessage(JsonNode source, ObjectNode target) {
        String value = source.path("message").asText("");
        if ("online".equalsIgnoreCase(source.path("state").asText()) && "".equals(value)) return;
        if ("offline".equalsIgnoreCase(source.path("state").asText())) target.put("message", "device offline");
        else if ("sleep".equalsIgnoreCase(source.path("state").asText())) target.put("message", "device standby");
    }

    private static void copyText(JsonNode source, ObjectNode target, String key, int maxLength) {
        JsonNode value = source.get(key);
        if (value != null && value.isTextual() && value.asText().length() <= maxLength) target.put(key, value.asText());
    }
}
