package com.ruoyi.common.deviceavailability;

/** Protocol/source evidence, not a guarantee that a browser can receive video. */
public class AvailabilitySnapshot {
    private String state = "UNKNOWN";
    private String reason = "NOT_CHECKED";
    private long checkedAt;
    private long lastAvailableAt;
    private int failureCount;
    private int successCount;

    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public long getCheckedAt() { return checkedAt; }
    public void setCheckedAt(long value) { checkedAt = value; }
    public long getLastAvailableAt() { return lastAvailableAt; }
    public void setLastAvailableAt(long value) { lastAvailableAt = value; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int value) { failureCount = value; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int value) { successCount = value; }
    public Boolean getOnline() {
        return "ONLINE".equals(state) ? Boolean.TRUE : "OFFLINE".equals(state) ? Boolean.FALSE : null;
    }

    public static AvailabilitySnapshot transition(AvailabilitySnapshot previous, String result,
                                                  long now, int failures, int successes) {
        AvailabilitySnapshot next = new AvailabilitySnapshot();
        next.checkedAt = now;
        next.lastAvailableAt = previous.lastAvailableAt;
        next.state = previous.state;
        next.reason = result;
        if ("AVAILABLE".equals(result)) {
            next.lastAvailableAt = now;
            next.successCount = Math.min(successes, previous.successCount + 1);
            if (next.successCount >= successes) next.state = "ONLINE";
            else if (!"ONLINE".equals(previous.state)) next.state = "UNKNOWN";
        } else {
            next.failureCount = Math.min(100, previous.failureCount + 1);
            if ("CONFIGURATION_ERROR".equals(result) || "UNSUPPORTED".equals(result)) {
                next.state = "UNKNOWN";
            } else if (next.failureCount >= failures) {
                next.state = "UNREACHABLE".equals(result) ? "OFFLINE"
                    : "AUTHENTICATION_FAILED".equals(result) ? "AUTH_ERROR" : "STREAM_ERROR";
            }
        }
        return next;
    }
}
