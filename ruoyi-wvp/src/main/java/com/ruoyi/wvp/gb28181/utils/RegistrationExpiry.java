package com.ruoyi.wvp.gb28181.utils;

import com.ruoyi.wvp.gb28181.bean.Platform;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.message.Response;
import java.util.ListIterator;

/** The negotiated lease is runtime state, never a replacement for configured Expires. */
public final class RegistrationExpiry {
    private RegistrationExpiry() { }

    public static int resolve(Response response, Platform platform) {
        ListIterator<?> contacts = response.getHeaders(ContactHeader.NAME);
        while (contacts != null && contacts.hasNext()) {
            ContactHeader contact = (ContactHeader) contacts.next();
            if (contact.isWildCard() || !(contact.getAddress().getURI() instanceof SipURI)) continue;
            SipURI uri = (SipURI) contact.getAddress().getURI();
            int port = uri.getPort() < 0 ? 5060 : uri.getPort();
            if (java.util.Objects.equals(platform.getDeviceGBId(), uri.getUser())
                    && uri.getHost().equalsIgnoreCase(platform.getDeviceIp())
                    && port == platform.getDevicePort() && contact.getExpires() >= 0) {
                return contact.getExpires();
            }
        }
        ExpiresHeader expires = response.getExpires();
        return expires != null && expires.getExpires() >= 0 ? expires.getExpires() : platform.getExpires();
    }

    public static int renewalDelayMillis(int configured, int granted) {
        long seconds = Math.max(1, configured);
        if (granted > 0) seconds = Math.min(seconds, granted);
        return (int) Math.min(Integer.MAX_VALUE, seconds * 800L);
    }
}
