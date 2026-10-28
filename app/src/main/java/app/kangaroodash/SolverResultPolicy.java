package app.kangaroodash;

/**
 * Conservative status policy for a result written by the native engine.
 *
 * A native success marker is not independent verification. A clean zero exit is
 * also required before the UI may report that the engine itself reported success.
 */
final class SolverResultPolicy {
    private SolverResultPolicy() {}

    static String statusForFoundResult(
            boolean engineReportedSuccess,
            boolean processExitedSuccessfully) {
        if (engineReportedSuccess && processExitedSuccessfully) {
            return "ENGINE REPORTED SUCCESS — process exited with code 0; independent verification unavailable";
        }
        if (engineReportedSuccess) {
            return "UNVERIFIED RESULT — engine success marker seen, but process did not exit with code 0";
        }
        return "UNVERIFIED RESULT — engine verification marker absent; scan.log പരിശോധിക്കുക";
    }
}
