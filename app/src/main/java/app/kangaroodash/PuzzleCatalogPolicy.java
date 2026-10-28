package app.kangaroodash;

/** Fail-closed policy for catalog entries shown in the Android UI. */
final class PuzzleCatalogPolicy {
    private PuzzleCatalogPolicy() {}

    static boolean isBlocked(String status, boolean privateKeyAlreadyPublic,
            boolean unsafeForWallets) {
        return !"unsolved".equalsIgnoreCase(status)
                || privateKeyAlreadyPublic
                || unsafeForWallets;
    }

    static boolean hasSupportedSearchBits(int searchBits) {
        // The current native UI contract supports positive range widths only.
        return searchBits >= 1 && searchBits <= 256;
    }
}
