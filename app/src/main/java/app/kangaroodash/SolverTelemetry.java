package app.kangaroodash;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the native engine's documented Ops/DP progress line shape. */
final class SolverTelemetry {
    // Keep prefix/suffix log text permissive, but do not accept partial numeric tokens.
    private static final Pattern OPS = Pattern.compile(
            "(?<![A-Za-z0-9_])Ops: (\\d+)M \\| DPs: (\\d+)(?![A-Za-z0-9_])");

    private SolverTelemetry() {}

    /**
     * Returns a matcher positioned at the first telemetry match, or null when
     * the line is not a recognized progress record.
     */
    static Matcher find(String line) {
        if (line == null) return null;
        Matcher matcher = OPS.matcher(line);
        return matcher.find() ? matcher : null;
    }
}
