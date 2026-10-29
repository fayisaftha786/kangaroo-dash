package app.kangaroodash;

import org.junit.Test;

import static org.junit.Assert.*;

public class SolverServiceExecutionPolicyTest {
    @Test
    public void inspectionOnlyBuildCannotLaunchNativeSearch() {
        assertFalse(SolverServiceExecutionPolicy.isNativeExecutionAllowed());
    }

    @Test
    public void blockedNativeSearchHasClearReason() {
        String reason = SolverServiceExecutionPolicy.nativeExecutionBlockReason();
        assertNotNull(reason);
        assertTrue(reason.contains("inspection-only"));
        assertTrue(reason.contains("not authorized"));
    }
}
