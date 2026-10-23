package com.ruoyi.framework.security.filter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.springframework.http.HttpHeaders;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transports browser credentials to the existing TokenService for one shared RTC handshake.
 * Decoded values are authentication inputs, never a trusted identity. Only the public protocol
 * reaches the remainder of the chain; no decoded credentials are added to that request view.
 */
public final class SharedCameraRtcAuthenticationRequest {
    private static final String PATH = "/vlstream/device/shared/camera-rtc";
    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final String PUBLIC_PROTOCOL = "vlstream-shared-rtc.v1";
    private static final String AUTH_PROTOCOL_PREFIX = "rtc-auth.";
    private static final int MAX_ENCODED_LENGTH = 8192;
    private static final int MAX_RAW_LENGTH = 6144;
    private static final JsonFactory JSON = new JsonFactory();
    private static final Map<String, String> ALLOWED_HEADERS;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        for (String name : new String[]{"accesstoken", "X-VLStream-Token", "X-WVP-Auth-Source",
                "tenantid", "requesttype", "appid", "secretkey", "Authorization", "blade-auth"}) {
            names.put(lower(name), name);
        }
        ALLOWED_HEADERS = Collections.unmodifiableMap(names);
    }

    private SharedCameraRtcAuthenticationRequest() { }

    public static boolean isSharedHandshake(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
            && PATH.equals(request.getServletPath())
            && (request.getContextPath() + PATH).equals(request.getRequestURI())
            && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"));
    }

    public static Views prepare(HttpServletRequest request) {
        if (!isSharedHandshake(request)) return new Views(request, request, false);
        List<String> protocols = protocolValues(request);
        String credentialProtocol = null;
        for (String protocol : protocols) {
            // Protocol names are case sensitive. Reject near matches rather than forwarding a
            // private credential envelope under a different spelling to downstream logging.
            if (lower(protocol).startsWith("rtc-auth")) {
                if (!protocol.startsWith(AUTH_PROTOCOL_PREFIX) || credentialProtocol != null) throw invalid();
                credentialProtocol = protocol;
            }
        }
        if (credentialProtocol == null) return new Views(request, request, false);
        if (protocols.size() != 2 || Collections.frequency(protocols, PUBLIC_PROTOCOL) != 1) throw invalid();
        Map<String, String> supplied = decodeHeaders(credentialProtocol.substring(AUTH_PROTOCOL_PREFIX.length()));
        rejectHttpHeaderConflicts(request, supplied);
        Map<String, String> publicProtocol = Collections.singletonMap(lower(PROTOCOL_HEADER), PUBLIC_PROTOCOL);
        Map<String, String> authenticationHeaders = new LinkedHashMap<>(supplied);
        authenticationHeaders.putAll(publicProtocol);
        return new Views(new HeaderView(request, authenticationHeaders), new HeaderView(request, publicProtocol), true);
    }

    private static List<String> protocolValues(HttpServletRequest request) {
        List<String> values = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders(PROTOCOL_HEADER);
        if (headers == null) return values;
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (header == null) throw invalid();
            // Bound parsing before splitting or decoding untrusted input.
            if (header.length() > MAX_ENCODED_LENGTH + AUTH_PROTOCOL_PREFIX.length() + PUBLIC_PROTOCOL.length() + 8) throw invalid();
            validateHeaderValue(header);
            for (String value : header.split(",", -1)) {
                String token = value.trim();
                if (token.isEmpty()) throw invalid();
                values.add(token);
                if (values.size() > 16) throw invalid();
            }
        }
        return values;
    }

    private static Map<String, String> decodeHeaders(String encoded) {
        if (encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH || !encoded.matches("[A-Za-z0-9_-]+")) throw invalid();
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (bytes.length > MAX_RAW_LENGTH
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded)) throw invalid();
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw invalid();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        try (JsonParser parser = JSON.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid();
                String name = lower(parser.currentName());
                if (!ALLOWED_HEADERS.containsKey(name) || headers.containsKey(name)) throw invalid();
                if (parser.nextToken() != JsonToken.VALUE_STRING) throw invalid();
                String value = parser.getText();
                validateHeaderValue(value);
                headers.put(name, value);
            }
            if (parser.nextToken() != null) throw invalid();
        } catch (IOException e) {
            throw invalid();
        }
        return headers;
    }

    private static void rejectHttpHeaderConflicts(HttpServletRequest request, Map<String, String> supplied) {
        for (Map.Entry<String, String> entry : supplied.entrySet()) {
            Enumeration<String> actual = request.getHeaders(ALLOWED_HEADERS.get(entry.getKey()));
            if (actual == null) continue;
            while (actual.hasMoreElements()) {
                if (!entry.getValue().equals(actual.nextElement())) throw invalid();
            }
        }
    }

    private static void validateHeaderValue(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            // The bridge runs after the firewall. Apply its control-character restriction again
            // to decoded values, including C1 characters U+0080-U+009F.
            if (Character.isISOControl(character)) throw invalid();
            if (Character.isHighSurrogate(character)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) throw invalid();
            } else if (Character.isLowSurrogate(character)) {
                throw invalid();
            }
        }
    }

    private static String lower(String name) { return name.toLowerCase(Locale.ROOT); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid shared RTC authentication envelope"); }

    public static final class Views {
        private final HttpServletRequest authenticationRequest;
        private final HttpServletRequest chainRequest;
        private final boolean credentialsSupplied;

        private Views(HttpServletRequest authenticationRequest, HttpServletRequest chainRequest, boolean credentialsSupplied) {
            this.authenticationRequest = authenticationRequest;
            this.chainRequest = chainRequest;
            this.credentialsSupplied = credentialsSupplied;
        }

        public HttpServletRequest getAuthenticationRequest() { return authenticationRequest; }
        public HttpServletRequest getChainRequest() { return chainRequest; }
        public boolean isCredentialsSupplied() { return credentialsSupplied; }
    }

    private static final class HeaderView extends HttpServletRequestWrapper {
        private final Map<String, String> values;

        private HeaderView(HttpServletRequest request, Map<String, String> values) {
            super(request);
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        @Override public String getHeader(String name) {
            return name != null && values.containsKey(lower(name)) ? values.get(lower(name)) : super.getHeader(name);
        }

        @Override public Enumeration<String> getHeaders(String name) {
            return name != null && values.containsKey(lower(name))
                ? Collections.enumeration(Collections.singletonList(values.get(lower(name)))) : super.getHeaders(name);
        }

        @Override public Enumeration<String> getHeaderNames() {
            Map<String, String> names = new LinkedHashMap<>();
            Enumeration<String> existing = super.getHeaderNames();
            if (existing != null) while (existing.hasMoreElements()) {
                String name = existing.nextElement();
                names.put(lower(name), name);
            }
            for (String name : values.keySet()) names.put(name, lower(PROTOCOL_HEADER).equals(name) ? PROTOCOL_HEADER : ALLOWED_HEADERS.get(name));
            return Collections.enumeration(names.values());
        }

        @Override public int getIntHeader(String name) {
            return name != null && values.containsKey(lower(name)) ? Integer.parseInt(getHeader(name)) : super.getIntHeader(name);
        }

        @Override public long getDateHeader(String name) {
            if (name == null || !values.containsKey(lower(name))) return super.getDateHeader(name);
            HttpHeaders headers = new HttpHeaders();
            headers.set(name, getHeader(name));
            return headers.getFirstDate(name);
        }
    }
}
