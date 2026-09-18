package com.ruoyi.isup.ehome;
import com.ruoyi.common.exception.ServiceException;
import org.junit.Test;
import java.net.InetAddress;
import static org.junit.Assert.*;

public class EhomeAddressResolverTest {
    @Test public void routeSelectsDifferentInterfacesPerDevice() throws Exception {
        InetAddress ethernet = InetAddress.getByName("192.168.88.28");
        InetAddress wifi = InetAddress.getByName("192.168.50.28");
        assertEquals("192.168.88.28", EhomeAddressResolver.resolve("", "192.168.88.128", peer -> ethernet));
        assertEquals("192.168.50.28", EhomeAddressResolver.resolve(null, "192.168.50.128", peer -> wifi));
    }
    @Test public void explicitPublicAddressWinsWithoutRouteLookup() {
        assertEquals("203.0.113.20", EhomeAddressResolver.resolve("203.0.113.20", null, peer -> {throw new AssertionError();}));
    }
    @Test(expected=ServiceException.class) public void rejectWildcardAdvertisement() {
        EhomeAddressResolver.resolve("0.0.0.0", "192.168.88.128");
    }
    @Test(expected=ServiceException.class) public void rejectLoopbackRoute() throws Exception {
        InetAddress loopback=InetAddress.getByName("127.0.0.1");
        EhomeAddressResolver.resolve("", "192.168.88.128", peer -> loopback);
    }
    @Test(expected=ServiceException.class) public void missingDeviceDoesNotGuessAnInterface() {
        EhomeAddressResolver.resolve("", null);
    }
}
