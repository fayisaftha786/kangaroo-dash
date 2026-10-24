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
