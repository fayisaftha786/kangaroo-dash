package app.kangaroodash;

import org.junit.Test;

import static org.junit.Assert.*;

public class SolverServiceInputPolicyTest {
    private static final String VALID_KEY =
            "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    private static String[] validArgs() {
        return new String[] { VALID_KEY, "1", "1", "8", "1" };
    }

    @Test
    public void acceptsWellFormedArgumentsAtLowerBounds() {
        assertNull(SolverServiceInputPolicy.validationError(validArgs()));
    }

    @Test
    public void acceptsUpperBoundsWithoutLaunchingNativeCode() {
        String[] args = validArgs();
        args[1] = "f".repeat(64);
        args[2] = "255";
        args[3] = "256";
        args[4] = "4096";

        assertNull(SolverServiceInputPolicy.validationError(args));
    }

    @Test
    public void rejectsMissingOrWrongArgumentCount() {
        assertEquals("solver argument count", SolverServiceInputPolicy.validationError(null));
        assertEquals("solver argument count", SolverServiceInputPolicy.validationError(new String[4]));
        assertEquals("solver argument count", SolverServiceInputPolicy.validationError(new String[6]));
    }

    @Test
    public void rejectsInvalidPublicKey() {
        String[] args = validArgs();
        args[0] = "not-a-public-key";
        assertEquals("public key", SolverServiceInputPolicy.validationError(args));
    }

    @Test
    public void rejectsMissingAndOversizedStartValues() {
        String[] args = validArgs();
        args[1] = "";
        assertEquals("start value", SolverServiceInputPolicy.validationError(args));
        args[1] = "a".repeat(65);
        assertEquals("start value", SolverServiceInputPolicy.validationError(args));
    }

    @Test
    public void rejectsInvalidRangeWidth() {
        String[] args = validArgs();
        args[2] = "0";
        assertEquals("range bits", SolverServiceInputPolicy.validationError(args));
        args[2] = "256";
        assertEquals("range bits", SolverServiceInputPolicy.validationError(args));
        args[2] = "257";
        assertEquals("range bits", SolverServiceInputPolicy.validationError(args));
        args[2] = "+1";
        assertEquals("range bits", SolverServiceInputPolicy.validationError(args));
    }

    @Test
    public void rejectsInvalidDpWidth() {
        String[] args = validArgs();
        args[3] = "0";
        assertEquals("DP bits", SolverServiceInputPolicy.validationError(args));
        args[3] = "257";
        assertEquals("DP bits", SolverServiceInputPolicy.validationError(args));
        args[3] = "1.5";
        assertEquals("DP bits", SolverServiceInputPolicy.validationError(args));
    }

    @Test
    public void rejectsInvalidKangarooCount() {
        String[] args = validArgs();
        args[4] = "0";
        assertEquals("kangaroo count", SolverServiceInputPolicy.validationError(args));
        args[4] = "4097";
        assertEquals("kangaroo count", SolverServiceInputPolicy.validationError(args));
        args[4] = "1e3";
        assertEquals("kangaroo count", SolverServiceInputPolicy.validationError(args));
    }

    @Test
    public void rejectsNullFieldWithoutThrowing() {
        String[] args = validArgs();
        args[3] = null;
        assertEquals("DP bits", SolverServiceInputPolicy.validationError(args));
    }
}
