package app.kangaroodash;

/**
 * Build-level guard for the current inspection-only catalog release.
 *
 * This guard is intentionally independent of Activity fields and Intent extras:
 * callers must not be able to turn native searching on by forging a request.
 * Keep disabled until native execution has a reviewed, record-bound authorization
 * path and the native engine's argument/persistence semantics have been verified.
 */
final class SolverServiceExecutionPolicy {
    private static final boolean INSPECTION_ONLY_BUILD = true;

    private SolverServiceExecutionPolicy() {}

    static boolean isNativeExecutionAllowed() {
        return !INSPECTION_ONLY_BUILD;
    }

    static String nativeExecutionBlockReason() {
        return isNativeExecutionAllowed()
                ? null
                : "Native START blocked: this build is inspection-only; native search is not authorized.";
    }
}
