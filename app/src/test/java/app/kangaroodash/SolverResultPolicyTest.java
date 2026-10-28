package app.kangaroodash;

import org.junit.Test;

import static org.junit.Assert.*;

public class SolverResultPolicyTest {
    @Test
    public void nativeSuccessMarkerAndZeroExitStillRequireIndependentVerification() {
        String status = SolverResultPolicy.statusForFoundResult(true, true);

        assertTrue(status.startsWith("ENGINE REPORTED SUCCESS"));
        assertTrue(status.contains("independent verification unavailable"));
        assertFalse(status.toLowerCase(java.util.Locale.ROOT).contains("independently verified"));
    }

    @Test
    public void nativeSuccessMarkerWithUncleanExitIsNotReportedAsSuccess() {
        String status = SolverResultPolicy.statusForFoundResult(true, false);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
        assertTrue(status.contains("did not exit with code 0"));
    }

    @Test
    public void zeroExitWithoutNativeSuccessMarkerRemainsUnverified() {
        String status = SolverResultPolicy.statusForFoundResult(false, true);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
        assertTrue(status.contains("verification marker absent"));
    }

    @Test
    public void missingMarkerAndUncleanExitRemainUnverified() {
        String status = SolverResultPolicy.statusForFoundResult(false, false);

        assertTrue(status.startsWith("UNVERIFIED RESULT"));
    }
}
