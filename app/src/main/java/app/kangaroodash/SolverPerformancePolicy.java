package app.kangaroodash;

/** Pure timing policy for reducing diagnostic I/O and thermal-sensor polling overhead. */
final class SolverPerformancePolicy {
    static final long BATTERY_TEMPERATURE_SAMPLE_INTERVAL_MS = 15_000L;
    static final long TELEMETRY_LOG_INTERVAL_MS = 5_000L;
    static final long NO_PREVIOUS_TELEMETRY_LOG = -1L;

    private SolverPerformancePolicy() {}

    /** Allow retries only while the per-session failed-exit budget remains. */
    static boolean shouldRetryAfterExit(int failedExits, int maxFailedExits) {
        if (failedExits < 0) throw new IllegalArgumentException("failed exits must not be negative");
        if (maxFailedExits < 1) throw new IllegalArgumentException("maximum failures must be positive");
        return failedExits < maxFailedExits;
    }

    static boolean isDue(long nowMs, long nextDueMs) {
        return nextDueMs <= 0 || nowMs >= nextDueMs;
    }

    static long nextDueAt(long nowMs, long intervalMs) {
        if (intervalMs <= 0) throw new IllegalArgumentException("interval must be positive");
        if (nowMs > Long.MAX_VALUE - intervalMs) return Long.MAX_VALUE;
        return nowMs + intervalMs;
    }

    /** Watch for stalls only after search has started and a valid baseline timestamp exists. */
    static boolean shouldWarnNoProgress(boolean searchStarted, long nowMs,
            long lastProgressAtMs, long timeoutMs) {
        if (timeoutMs <= 0) throw new IllegalArgumentException("timeout must be positive");
        return searchStarted && lastProgressAtMs >= 0 && nowMs >= lastProgressAtMs
                && nowMs - lastProgressAtMs >= timeoutMs;
    }

    static boolean shouldLogTelemetry(long nowMs, long lastLoggedAtMs) {
        return lastLoggedAtMs < 0 || nowMs < lastLoggedAtMs
                || nowMs - lastLoggedAtMs >= TELEMETRY_LOG_INTERVAL_MS;
    }
}
