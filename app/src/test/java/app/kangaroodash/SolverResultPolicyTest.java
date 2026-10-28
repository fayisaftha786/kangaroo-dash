package app.kangaroodash;

import org.junit.Test;

import static org.junit.Assert.*;

public class SolverResultPolicyTest {
    @Test
    public void nativeSuccessMarkerAndZeroExitStillRequireIndependentVerification() {
        String status = SolverResultPolicy.statusForFoundResult(true, true, false);

        assertTrue(status.startsWith("ENGINE REPORTED SUCCESS"));
        assertTrue(status.contains("independent verification unavailable"));
        assertFalse(status.toLowerCase(java.util.Locale.ROOT).contains("independently verified"));
    }

    @Test
    public void nativeSuccessMarkerWithUncleanExitIsNotReportedAsSuccess() {
        String status = SolverResultPolicy.statusForFoundResult(true, false, false);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
        assertTrue(status.contains("did not exit with code 0"));
    }

    @Test
    public void zeroExitWithoutNativeSuccessMarkerRemainsUnverified() {
        String status = SolverResultPolicy.statusForFoundResult(false, true, false);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
        assertTrue(status.contains("verification marker absent"));
    }

    @Test
    public void missingMarkerAndUncleanExitRemainUnverified() {
        String status = SolverResultPolicy.statusForFoundResult(false, false, false);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
    }

    @Test
    public void survivingNativeProcessOverridesSuccessMarkerAndCleanExitFlags() {
        String status = SolverResultPolicy.statusForFoundResult(true, true, true);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
        assertTrue(status.contains("process remains alive"));
        assertTrue(status.contains("automatic restart disabled"));
        assertFalse(status.startsWith("ENGINE REPORTED SUCCESS"));
    }

    @Test
    public void survivingNativeProcessIsReportedEvenWithoutSuccessMarker() {
        String status = SolverResultPolicy.statusForFoundResult(false, false, true);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
        assertTrue(status.contains("process remains alive"));
        assertTrue(status.contains("automatic restart disabled"));
    }
}
