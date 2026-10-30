package com.ruoyi.web.service;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CloudAiNativeStreamsTest {
    @Test public void partialCleanupRetriesOnlyUnfinishedStages() {
        AtomicInteger device = new AtomicInteger(), media = new AtomicInteger(), callback = new AtomicInteger();
        CloudAiNativeStreams.Stream stream = new CloudAiNativeStreams.Stream("rtsp://fixture/live",
            device::incrementAndGet,
            () -> { if (media.incrementAndGet() == 1) throw new IllegalStateException("temporary failure"); },
            callback::incrementAndGet);
        assertThrows(IllegalStateException.class, stream::close);
        assertEquals(1, device.get()); assertEquals(0, callback.get());
        stream.close(); stream.close();
        assertEquals(1, device.get()); assertEquals(2, media.get()); assertEquals(1, callback.get());
    }
}
