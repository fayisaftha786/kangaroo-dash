package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.*;

public class SolverPerformancePolicyTest {
    @Test public void automaticRestartStopsAtConfiguredFailureLimit() {
        assertTrue(SolverPerformancePolicy.shouldRetryAfterExit(1, 3));
        assertTrue(SolverPerformancePolicy.shouldRetryAfterExit(2, 3));
        assertFalse(SolverPerformancePolicy.shouldRetryAfterExit(3, 3));
        assertFalse(SolverPerformancePolicy.shouldRetryAfterExit(4, 3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeFailureCount() {
        SolverPerformancePolicy.shouldRetryAfterExit(-1, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroRestartFailureLimit() {
        SolverPerformancePolicy.shouldRetryAfterExit(0, 0);
    }

    @Test public void thermalSampleIsDueAtStartAndAtDeadline() {
        assertTrue(SolverPerformancePolicy.isDue(0, 0));
        assertTrue(SolverPerformancePolicy.isDue(15_000, 15_000));
        assertFalse(SolverPerformancePolicy.isDue(14_999, 15_000));
    }

    @Test public void nextThermalSampleDeadlineUsesConfiguredInterval() {
        assertEquals(25_000L, SolverPerformancePolicy.nextDueAt(10_000L,
                SolverPerformancePolicy.BATTERY_TEMPERATURE_SAMPLE_INTERVAL_MS));
    }

    @Test public void deadlineAdditionDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE,
                SolverPerformancePolicy.nextDueAt(Long.MAX_VALUE - 2, 15));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveSamplingInterval() {
        SolverPerformancePolicy.nextDueAt(1, 0);
    }

    @Test public void noProgressWatchdogDoesNotRunDuringGpuInitialization() {
        assertFalse(SolverPerformancePolicy.shouldWarnNoProgress(false, 120_000, -1,
                SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
    }

    @Test public void noProgressWatchdogAllowsCurrentTelemetryCadence() {
        long baseline = 10_000;
        assertFalse(SolverPerformancePolicy.shouldWarnNoProgress(true, baseline + 102_000,
                baseline, SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
        assertFalse(SolverPerformancePolicy.shouldWarnNoProgress(true, baseline + 149_999,
                baseline, SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
        assertTrue(SolverPerformancePolicy.shouldWarnNoProgress(true, baseline + 150_000,
                baseline, SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
    }

    @Test public void warningTextMatchesConfiguredWatchdogTimeout() {
        assertEquals("No telemetry/progress for 150s — engine may be stalled; not auto-stopped",
                SolverPerformancePolicy.noProgressWarningMessage());
    }

    @Test public void noProgressWatchdogIgnoresClockBeforeBaseline() {
        assertFalse(SolverPerformancePolicy.shouldWarnNoProgress(true, 9_000, 10_000,
                SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void noProgressWatchdogRejectsInvalidTimeout() {
        SolverPerformancePolicy.shouldWarnNoProgress(true, 10, 0, 0);
    }

    @Test public void telemetryRemainsFreshAcrossExpectedNativeCadence() {
        assertTrue(SolverPerformancePolicy.isTelemetryFresh(110_000, 10_000,
                SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
        assertTrue(SolverPerformancePolicy.isTelemetryFresh(160_000, 10_000,
                SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
        assertFalse(SolverPerformancePolicy.isTelemetryFresh(160_001, 10_000,
                SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
    }

    @Test public void missingTelemetryAndBackwardsClockAreNotFresh() {
        assertFalse(SolverPerformancePolicy.isTelemetryFresh(10_000, 0,
                SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
        assertFalse(SolverPerformancePolicy.isTelemetryFresh(9_000, 10_000,
                SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void telemetryFreshnessRejectsInvalidMaximumAge() {
        SolverPerformancePolicy.isTelemetryFresh(10_000, 1, 0);
    }

    @Test public void telemetryIsLoggedImmediatelyThenAtMostEveryFiveSeconds() {
        assertTrue(SolverPerformancePolicy.shouldLogTelemetry(100,
                SolverPerformancePolicy.NO_PREVIOUS_TELEMETRY_LOG));
        assertFalse(SolverPerformancePolicy.shouldLogTelemetry(5_099, 100));
        assertTrue(SolverPerformancePolicy.shouldLogTelemetry(5_100, 100));
    }

    @Test public void telemetryLoggingRecoversIfMonotonicClockValueMovesBackwards() {
        assertTrue(SolverPerformancePolicy.shouldLogTelemetry(50, 100));
    }
}
