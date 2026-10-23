package app.kangaroodash;

import org.junit.Test;
import java.util.regex.Matcher;
import static org.junit.Assert.*;

public class SolverTelemetryTest {
    @Test public void parsesNativeProgressLine() {
        Matcher matcher = SolverTelemetry.find("Ops: 123M | DPs: 456");
        assertNotNull(matcher);
        assertEquals("123", matcher.group(1));
        assertEquals("456", matcher.group(2));
    }

    @Test public void findsProgressWhenEngineAddsPrefixOrSuffix() {
        Matcher matcher = SolverTelemetry.find("[INFO] Ops: 7M | DPs: 9 (active)");
        assertNotNull(matcher);
        assertEquals("7", matcher.group(1));
        assertEquals("9", matcher.group(2));
    }

    @Test public void rejectsMalformedOrUnrelatedLines() {
        assertNull(SolverTelemetry.find(null));
        assertNull(SolverTelemetry.find(""));
        assertNull(SolverTelemetry.find("engine started"));
        assertNull(SolverTelemetry.find("Ops: 123 | DPs: 456"));
        assertNull(SolverTelemetry.find("Ops: -1M | DPs: 456"));
        assertNull(SolverTelemetry.find("Ops: 123M | DPs: nope"));
        assertNull(SolverTelemetry.find("Ops: 123M|DPs: 456"));
        assertNull(SolverTelemetry.find("Ops: 123M | DPs: 456xyz"));
        assertNull(SolverTelemetry.find("prefixOps: 123M | DPs: 456"));
        assertNull(SolverTelemetry.find("Ops: 123M | DPs: 456_"));
        assertNull(SolverTelemetry.find("Ops: 9223372036854775808M | DPs: 456"));
        assertNull(SolverTelemetry.find("Ops: 123M | DPs: 9223372036854775808"));
    }
}
