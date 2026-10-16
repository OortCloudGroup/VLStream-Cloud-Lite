package com.ruoyi.web.service;

import com.ruoyi.common.deviceavailability.AvailabilitySnapshot;
import com.ruoyi.common.deviceavailability.ProtocolAvailabilityService;
import org.junit.Test;
import javax.sql.DataSource;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ProtocolDeviceAvailabilityMonitorTest {
    @Test public void threeFailuresBackOffAndReadsDoNotScheduleImmediateRepeatedProbes() throws Exception {
        try (Fixture f = new Fixture()) {
            for (int count = 1; count <= 3; count++) {
                f.due().put("RTSP:7", 0L);
                f.monitor.tick();
                f.await(count);
                long remaining = f.due().get("RTSP:7") - System.currentTimeMillis();
                assertTrue("Failure cadence", remaining >= (count == 3 ? 59000 : 29000));
            }
            f.monitor.tick();
            assertEquals(3, f.recorded.get());
            assertEquals("OFFLINE", f.snapshot.get().getState());
        }
    }

    @Test public void changedSavedSourceInvalidatesOldEvidenceAndSchedulesFreshProbe() throws Exception {
        try (Fixture f = new Fixture()) {
            f.due().put("RTSP:7", 0L);
            f.monitor.tick();
            f.await(1);
            f.url.set(f.url.get().replace("/original", "/changed"));
            f.monitor.tick();
            f.await(2);
            verify(f.availability).invalidate("RTSP", "7");
            assertEquals(1, f.snapshot.get().getFailureCount());
        }
    }

    private static class Fixture implements AutoCloseable {
        final AtomicReference<String> url = new AtomicReference<>();
        final AtomicInteger recorded = new AtomicInteger();
        final AtomicReference<AvailabilitySnapshot> snapshot = new AtomicReference<>(new AvailabilitySnapshot());
        final ProtocolAvailabilityService availability = mock(ProtocolAvailabilityService.class);
        final ProtocolDeviceAvailabilityMonitor monitor;
        Fixture() throws Exception {
            // Reserve then close one local test port. No real camera/network configuration is changed.
            try (ServerSocket port = new ServerSocket(0)) { url.set("rtsp://127.0.0.1:" + port.getLocalPort() + "/original"); }
            DataSource database = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            when(database.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                PreparedStatement statement = mock(PreparedStatement.class);
                when(statement.executeQuery()).thenAnswer(query -> {
                    ResultSet rows = mock(ResultSet.class);
                    AtomicInteger cursor = new AtomicInteger();
                    when(rows.next()).thenAnswer(next -> cursor.getAndIncrement() == 0);
                    when(rows.getString("protocol")).thenReturn("RTSP");
                    when(rows.getString("id")).thenReturn("7");
                    when(rows.getString("ip")).thenReturn("127.0.0.1");
                    when(rows.getString("url")).thenReturn(url.get());
                    when(rows.getString("user_name")).thenReturn("fixture");
                    when(rows.getString("password")).thenReturn("fixture-only");
                    return rows;
                });
                return statement;
            });
            when(availability.claim("RTSP", "7")).thenReturn(true);
            when(availability.record(eq("RTSP"), eq("7"), anyString())).thenAnswer(invocation -> {
                snapshot.set(AvailabilitySnapshot.transition(snapshot.get(), invocation.getArgument(2), System.currentTimeMillis(), 3, 2));
                recorded.incrementAndGet();
                return snapshot.get();
            });
            doAnswer(invocation -> { snapshot.set(new AvailabilitySnapshot()); return null; }).when(availability).invalidate("RTSP", "7");
            monitor = new ProtocolDeviceAvailabilityMonitor(database, availability, true, 30, 300, 500);
        }
        @SuppressWarnings("unchecked") Map<String, Long> due() throws Exception {
            java.lang.reflect.Field field = ProtocolDeviceAvailabilityMonitor.class.getDeclaredField("due");
            field.setAccessible(true);
            return (Map<String, Long>) field.get(monitor);
        }
        void await(int count) throws Exception {
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                if (recorded.get() == count && due().get("RTSP:7") > System.currentTimeMillis()) return;
                Thread.sleep(10);
            }
            fail("Probe did not finish");
        }
        public void close() { monitor.close(); }
    }
}
