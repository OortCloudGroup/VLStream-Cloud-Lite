package com.ruoyi.common.deviceavailability;

import org.junit.Test;
import static org.junit.Assert.*;

public class AvailabilitySnapshotTest {
    private AvailabilitySnapshot result(AvailabilitySnapshot previous, String outcome, long time) {
        return AvailabilitySnapshot.transition(previous, outcome, time, 3, 2);
    }
    @Test public void requiresTwoSuccessesAndThreeFailuresThenRecovers() {
        AvailabilitySnapshot state = result(new AvailabilitySnapshot(), "AVAILABLE", 100);
        assertEquals("UNKNOWN", state.getState());
        state = result(state, "AVAILABLE", 200);
        assertEquals(Boolean.TRUE, state.getOnline());
        state = result(state, "UNREACHABLE", 300);
        state = result(state, "UNREACHABLE", 400);
        assertEquals("ONLINE", state.getState());
        state = result(state, "UNREACHABLE", 500);
        assertEquals(Boolean.FALSE, state.getOnline());
        assertEquals(200, state.getLastAvailableAt());
        state = result(state, "AVAILABLE", 600);
        assertNull(state.getOnline());
        state = result(state, "AVAILABLE", 700);
        assertEquals(Boolean.TRUE, state.getOnline());
        assertEquals(0, state.getFailureCount());
        assertEquals(700, state.getLastAvailableAt());
    }
    @Test public void authenticationAndStreamErrorsAreDistinctFromNetworkOffline() {
        for (String reason : new String[]{"AUTHENTICATION_FAILED", "NO_VIDEO_SDP"}) {
            AvailabilitySnapshot state = new AvailabilitySnapshot();
            for (int i = 0; i < 3; i++) state = result(state, reason, i + 1);
            assertEquals(reason.equals("AUTHENTICATION_FAILED") ? "AUTH_ERROR" : "STREAM_ERROR", state.getState());
            assertNull(state.getOnline());
            assertEquals(reason, state.getReason());
        }
    }
    @Test public void invalidConfigurationDoesNotClaimNetworkOffline() {
        AvailabilitySnapshot state = new AvailabilitySnapshot();
        for (int i = 0; i < 5; i++) state = result(state, "CONFIGURATION_ERROR", i + 1);
        assertEquals("UNKNOWN", state.getState());
        assertEquals(0, state.getLastAvailableAt());
    }
}
