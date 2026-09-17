package com.ruoyi.wvp.gb28181;

import com.ruoyi.wvp.gb28181.bean.Platform;
import com.ruoyi.wvp.gb28181.utils.RegistrationExpiry;
import org.junit.Test;
import javax.sip.SipFactory;
import javax.sip.message.Response;
import static org.junit.Assert.*;

public class RegistrationExpiryTest {
    private Platform platform() {
        Platform p = new Platform();
        p.setDeviceGBId("34020000002000000002");
        p.setDeviceIp("192.0.2.10");
        p.setDevicePort(8116);
        p.setExpires(3600);
        return p;
    }

    private Response response(String headers) throws Exception {
        return SipFactory.getInstance().createMessageFactory().createResponse(
                "SIP/2.0 200 OK\r\nVia: SIP/2.0/UDP 192.0.2.10:8116;branch=z9hG4bK-test\r\n"
                + "From: <sip:34020000002000000002@192.0.2.10>;tag=test\r\n"
                + "To: <sip:34020000002000000002@192.0.2.10>;tag=reply\r\n"
                + "Call-ID: expiry-test\r\nCSeq: 1 REGISTER\r\n" + headers + "Content-Length: 0\r\n\r\n");
    }

    @Test public void shortServerLeaseRenewsBeforeExpiryWithoutChangingConfiguration() throws Exception {
        Platform p = platform();
        int granted = RegistrationExpiry.resolve(response("Expires: 300\r\n"), p);
        assertEquals(240000, RegistrationExpiry.renewalDelayMillis(p.getExpires(), granted));
        assertEquals(3600, p.getExpires());
    }
    @Test public void matchingContactOverridesGlobalExpiry() throws Exception {
        assertEquals(120, RegistrationExpiry.resolve(response("Expires: 300\r\n"
                + "Contact: <sip:other@192.0.2.20:8116>;expires=10\r\n"
                + "Contact: <sip:34020000002000000002@192.0.2.10:8116>;expires=120\r\n"), platform()));
    }
    @Test public void zeroIsNotTreatedAsAFullLease() throws Exception {
        assertEquals(0, RegistrationExpiry.resolve(response("Expires: 0\r\n"), platform()));
    }
    @Test public void missingExpiryUsesConfiguredValue() throws Exception {
        assertEquals(3600, RegistrationExpiry.resolve(response(""), platform()));
        assertEquals(2880000, RegistrationExpiry.renewalDelayMillis(3600, 7200));
        assertEquals(800, RegistrationExpiry.renewalDelayMillis(1, 1));
        assertEquals(Integer.MAX_VALUE, RegistrationExpiry.renewalDelayMillis(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
