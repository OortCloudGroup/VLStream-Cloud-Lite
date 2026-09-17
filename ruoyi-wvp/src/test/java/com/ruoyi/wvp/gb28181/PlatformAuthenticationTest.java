package com.ruoyi.wvp.gb28181;

import com.ruoyi.wvp.conf.SipConfig;
import com.ruoyi.wvp.gb28181.bean.Platform;
import com.ruoyi.wvp.gb28181.transmit.cmd.SIPRequestHeaderPlarformProvider;
import com.ruoyi.wvp.storager.IRedisCatchStorage;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;
import javax.sip.SipFactory;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlatformAuthenticationTest {
    @Test public void challengedRegisterUsesConfiguredIdentityForHeaderAndDigest() throws Exception {
        check("assigned-user", "assigned-user");
    }
    @Test public void emptyUsernameFallsBackToDeviceIdentity() throws Exception {
        check("", "34020000002000000002");
    }
    private void check(String configured, String expected) throws Exception {
        Platform p = new Platform();
        p.setServerGBId("34020000002000000001");
        p.setServerIp("192.0.2.20"); p.setServerPort(5060);
        p.setDeviceGBId("34020000002000000002");
        p.setDeviceIp("192.0.2.10"); p.setDevicePort(8116);
        p.setTransport("UDP"); p.setUsername(configured); p.setPassword("test-only");
        SIPRequestHeaderPlarformProvider provider = new SIPRequestHeaderPlarformProvider();
        SipConfig sip = mock(SipConfig.class);
        when(sip.getDomain()).thenReturn("3402000000");
        ReflectionTestUtils.setField(provider, "sipConfig", sip);
        ReflectionTestUtils.setField(provider, "redisCatchStorage", mock(IRedisCatchStorage.class));
        WWWAuthenticateHeader challenge = SipFactory.getInstance().createHeaderFactory().createWWWAuthenticateHeader("Digest");
        challenge.setRealm("test-realm"); challenge.setNonce("test-nonce");
        Request request = provider.createRegisterRequest(p, "test", null, challenge,
                SipFactory.getInstance().createHeaderFactory().createCallIdHeader("test-call"), 3600);
        AuthorizationHeader auth = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        assertEquals(expected, auth.getUsername());
        String ha1 = md5(expected + ":test-realm:test-only");
        String ha2 = md5("REGISTER:" + auth.getURI());
        assertEquals(md5(ha1 + ":test-nonce:" + ha2), auth.getResponse());
    }
    private String md5(String text) { return DigestUtils.md5DigestAsHex(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
