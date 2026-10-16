package com.ruoyi.common.deviceavailability;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One authenticated DESCRIBE. No SETUP/PLAY, redirection, RTP or camera secrets in results/logs. */
public class RtspDescribeProbe {
    private static final Pattern PARAM = Pattern.compile("(\\w+)\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))");

    public String check(String address, int timeoutMillis) {
        return check(address, timeoutMillis, null);
    }

    /** Optional prevalidated address avoids a second DNS lookup for custom-source probes. */
    public String check(String address, int timeoutMillis, java.net.InetAddress pinnedAddress) {
        URI uri;
        String requestUri;
        String username = "", password = "";
        try {
            uri = new URI(address);
            if (!"rtsp".equalsIgnoreCase(uri.getScheme())) return "UNSUPPORTED";
            if (uri.getHost() == null || uri.getFragment() != null) return "CONFIGURATION_ERROR";
            if (uri.getRawUserInfo() != null) {
                String[] auth = uri.getRawUserInfo().split(":", 2);
                username = decode(auth[0]);
                password = auth.length > 1 ? decode(auth[1]) : "";
            }
            // Keep the exact path/query bytes used by the camera in Digest authentication.
            String authority = uri.getRawAuthority();
            if (authority.contains("@")) authority = authority.substring(authority.lastIndexOf('@') + 1);
            requestUri = "rtsp://" + authority + (uri.getRawPath() == null ? "" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            if ((requestUri + username + password).contains("\r") || (requestUri + username + password).contains("\n"))
                return "CONFIGURATION_ERROR";
        } catch (Exception invalid) { return "CONFIGURATION_ERROR"; }
        long deadline = System.nanoTime() + Math.max(pinnedAddress==null?500:1, timeoutMillis) * 1_000_000L;
        try (Socket socket = new Socket();SocketDeadline guard=new SocketDeadline(socket,deadline)) {
            socket.connect(pinnedAddress == null ? new InetSocketAddress(uri.getHost(), uri.getPort() < 0 ? 554 : uri.getPort())
                : new InetSocketAddress(pinnedAddress, uri.getPort() < 0 ? 554 : uri.getPort()), remaining(deadline));
            Response response = request(socket, requestUri, 1, "", deadline);
            if (response.code == 401) {
                String auth = authorization(response.headers.get("www-authenticate"), username, password, requestUri);
                if (auth == null) return "AUTHENTICATION_UNSUPPORTED";
                response = request(socket, requestUri, 2, auth, deadline);
            }
            if (response.code == 401 || response.code == 403) return "AUTHENTICATION_FAILED";
            if (response.code != 200) return "STREAM_UNAVAILABLE";
            boolean video=videoDescription(response.body,pinnedAddress!=null);
            return video ? "AVAILABLE" : "NO_VIDEO_SDP";
        } catch (Exception unreachable) { return "UNREACHABLE"; }
    }

    /** Encoding belongs to a payload in this video section, never another media section. */
    static boolean videoDescription(String sdp,boolean requireControl){
        boolean version=false,inMedia=false;String sessionControl=null;java.util.List<VideoSection> sections=new java.util.ArrayList<>();VideoSection current=null;
        for(String raw:sdp.split("\\r?\\n")){String line=raw.trim();
            if(line.startsWith("m=")){
                inMedia=true;current=null;String[] fields=line.substring(2).split("\\s+");
                if(fields.length<4)continue;
                if("video".equalsIgnoreCase(fields[0]))try{
                    int port=Integer.parseInt(fields[1].split("/",2)[0]);
                    if(port<0||port>65535||!fields[2].matches("(?i)RTP/S?AVPF?(?:/TCP)?"))continue;
                    current=new VideoSection();sections.add(current);
                    for(int i=3;i<fields.length;i++){int pt=Integer.parseInt(fields[i]);if(pt>=0&&pt<=127)current.payloads.add(pt);}
                }catch(NumberFormatException invalid){if(current!=null)sections.remove(current);current=null;}
            }else if("v=0".equals(line)&&!inMedia)version=true;
            else if(line.startsWith("a=control:")&&!line.substring(10).trim().isEmpty()){
                if(current!=null)current.control=true;else if(!inMedia)sessionControl=line.substring(10).trim();
            }else if(current!=null){Matcher mapping=Pattern.compile("(?i)^a=rtpmap:([0-9]+)\\s+([A-Z0-9-]+)/([0-9]+)(?:/[0-9]+)?\\s*$").matcher(line);
                if(mapping.matches())try{if(Long.parseLong(mapping.group(3))>0)current.codecs.put(Integer.parseInt(mapping.group(1)),mapping.group(2).toUpperCase(Locale.ROOT));}catch(NumberFormatException invalid){}
            }
        }
        if(!version)return false;
        for(VideoSection section:sections){if(requireControl&&!section.control&&sessionControl==null)continue;
            for(Integer pt:section.payloads){String codec=section.codecs.get(pt);if(codec==null)codec=pt==26?"JPEG":pt==32?"MPV":null;
                if(java.util.Arrays.asList("H264","H265","HEVC","JPEG","VP8","VP9","AV1","MP4V-ES","MPV").contains(codec))return true;}
        }return false;
    }
    private static class VideoSection {final java.util.Set<Integer> payloads=new java.util.HashSet<>();final Map<Integer,String> codecs=new HashMap<>();boolean control;}

    private static int remaining(long deadline) throws java.net.SocketTimeoutException {
        long millis = (deadline - System.nanoTime()) / 1_000_000;
        if (millis <= 0) throw new java.net.SocketTimeoutException();
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }

    private static Response request(Socket socket, String uri, int sequence, String auth, long deadline) throws Exception {
        OutputStream output = socket.getOutputStream();
        output.write(("DESCRIBE " + uri + " RTSP/1.0\r\nCSeq: " + sequence
                + "\r\nAccept: application/sdp\r\n" + (auth.isEmpty() ? "" : "Authorization: " + auth + "\r\n")
                + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
        InputStream input = socket.getInputStream();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int ending = 0;
        while (ending != 0x0d0a0d0a) {
            socket.setSoTimeout(remaining(deadline));
            int next = input.read();
            if (next < 0 || header.size() >= 65536) throw new java.io.IOException("Invalid RTSP header");
            header.write(next);
            ending = (ending << 8) | next;
        }
        String[] lines = new String(header.toByteArray(), StandardCharsets.UTF_8).split("\r\n");
        int code = Integer.parseInt(lines[0].split(" ")[1]);
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator > 0) headers.put(lines[i].substring(0, separator).toLowerCase(Locale.ROOT), lines[i].substring(separator + 1).trim());
        }
        int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        if (length < 0 || length > 65536) throw new java.io.IOException("Invalid RTSP body");
        byte[] body = new byte[length];
        for (int offset = 0; offset < length;) {
            socket.setSoTimeout(remaining(deadline));
            int count = input.read(body, offset, length - offset);
            if (count < 0) throw new java.io.IOException("Incomplete RTSP body");
            offset += count;
        }
        return new Response(code, headers, new String(body, StandardCharsets.UTF_8));
    }

    static String authorization(String challenge, String user, String password, String uri) throws Exception {
        if (challenge == null) return null;
        if (challenge.regionMatches(true, 0, "Basic", 0, 5))
            return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        if (!challenge.regionMatches(true, 0, "Digest", 0, 6)) return null;
        Map<String, String> values = new HashMap<>();
        Matcher matcher = PARAM.matcher(challenge);
        while (matcher.find()) values.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2) == null ? matcher.group(3) : matcher.group(2));
        String realm = values.get("realm"), nonce = values.get("nonce");
        if (realm == null || nonce == null) return null;
        String algorithm = values.getOrDefault("algorithm", "MD5");
        if (!("MD5".equalsIgnoreCase(algorithm) || "MD5-sess".equalsIgnoreCase(algorithm))) return null;
        String qop = values.get("qop");
        if (qop != null && !java.util.Arrays.asList(qop.split("\\s*,\\s*")).contains("auth")) return null;
        String cnonce = UUID.randomUUID().toString().replace("-", "");
        String ha1 = md5(user + ":" + realm + ":" + password);
        if ("MD5-sess".equalsIgnoreCase(algorithm)) ha1 = md5(ha1 + ":" + nonce + ":" + cnonce);
        String digest = md5(ha1 + ":" + nonce + (qop == null ? "" : ":00000001:" + cnonce + ":auth") + ":" + md5("DESCRIBE:" + uri));
        return "Digest username=" + quote(user) + ", realm=" + quote(realm) + ", nonce=" + quote(nonce)
            + ", uri=" + quote(uri) + ", response=" + quote(digest)
            // Some cameras reject an algorithm parameter they did not advertise.
            + (values.containsKey("algorithm") ? ", algorithm=" + algorithm : "")
            + (qop == null ? ("MD5-sess".equalsIgnoreCase(algorithm) ? ", cnonce=" + quote(cnonce) : "")
                : ", qop=auth, nc=00000001, cnonce=" + quote(cnonce))
            + (values.containsKey("opaque") ? ", opaque=" + quote(values.get("opaque")) : "");
    }

    private static String decode(String text) throws Exception { return URLDecoder.decode(text.replace("+", "%2B"), "UTF-8"); }
    private static String quote(String text) { return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static String md5(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : hash) result.append(String.format("%02x", b & 255));
        return result.toString();
    }
    private static class Response {
        final int code;
        final Map<String, String> headers;
        final String body;
        Response(int code, Map<String, String> headers, String body) { this.code = code; this.headers = headers; this.body = body; }
    }
}
