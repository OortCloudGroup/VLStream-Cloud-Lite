package com.ruoyi.isup.ehome;
import org.junit.Test;
import static org.junit.Assert.*;

public class HikSdkLibrariesTest {
    @Test public void formatSdkVersionIsDistinctFromProtocolVersion() {
        assertEquals("2.5.1.35", HikSdkLibraries.version(0x02050123));
    }
    @Test public void fixedPathBufferIncludesNullTerminator() {
        byte[] buffer = HikSdkLibraries.pathBuffer("/sdk/libcrypto.so");
        assertEquals(256, buffer.length); assertEquals(0, buffer[17]);
    }
    @Test(expected=IllegalArgumentException.class) public void longPathFailsBeforeNativeWrite() {
        HikSdkLibraries.pathBuffer(new String(new char[256]).replace('\0', 'x'));
    }
}
