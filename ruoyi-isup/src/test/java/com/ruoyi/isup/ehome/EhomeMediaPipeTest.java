package com.ruoyi.isup.ehome;

import org.junit.Test;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class EhomeMediaPipeTest {
    @Test public void partialPacketReadDoesNotWaitForAnotherPacket() throws Exception {
        EhomeMediaPipe.PacketInput input = new EhomeMediaPipe.PacketInput();
        assertTrue(input.offer(new byte[]{1,2,3}));
        byte[] bytes = new byte[1024];
        assertEquals(3, input.read(bytes, 0, bytes.length));
        assertEquals(3, bytes[2]);
        input.close();
        assertEquals(-1, input.read());
        assertEquals(-1, input.read());
        assertEquals(0, input.read(bytes, 0, 0));
    }
    @Test public void closeUnblocksPendingRead() throws Exception {
        EhomeMediaPipe.PacketInput input = new EhomeMediaPipe.PacketInput();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> pending = executor.submit(() -> input.read());
            input.close();
            assertEquals(Integer.valueOf(-1), pending.get(1, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    @Test public void boundedBufferRejectsOverflowAndWritesAfterClose() {
        EhomeMediaPipe.PacketInput input = new EhomeMediaPipe.PacketInput();
        assertTrue(input.offer(new byte[8 * 1024 * 1024]));
        assertFalse(input.offer(new byte[]{1}));
        input.close();
        assertFalse(input.offer(new byte[]{1}));
    }
}
