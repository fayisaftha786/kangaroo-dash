package app.kangaroodash;

/**
 * Defensive service-boundary validation for native-engine arguments.
 *
 * The Activity validates user input too, but the Service must not assume all
 * Intents came through that UI. This class never launches the native process.
 */
final class SolverServiceInputPolicy {
    private SolverServiceInputPolicy() {}

    /**
     * Returns a field label when arguments are unsafe/malformed, or null if valid.
     * Values are checked without including their contents in diagnostics.
     */
    static String validationError(String[] args) {
        if (args == null || args.length != 5) return "solver argument count";

        if (!MainActivity.isPublicKeyHex(args[0])) return "public key";
        if (!MainActivity.isValidStartValue(args[1])) return "start value";

        Integer rangeBits = positiveDecimal(args[2]);
        if (rangeBits == null || rangeBits < 1 || rangeBits > 255) return "range bits";

        Integer dpBits = positiveDecimal(args[3]);
        if (dpBits == null || dpBits < 1 || dpBits > 256) return "DP bits";

        Integer kangarooCount = positiveDecimal(args[4]);
        if (kangarooCount == null || kangarooCount < 1 || kangarooCount > 4096) {
            return "kangaroo count";
        }
        return null;
    }

    private static Integer positiveDecimal(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
