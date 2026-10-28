package app.kangaroodash;

/** Fail-closed policy for catalog entries shown in the Android UI. */
final class PuzzleCatalogPolicy {
    private PuzzleCatalogPolicy() {}

    static boolean isBlocked(String status, Object privateKeyAlreadyPublic,
            Object unsafeForWallets) {
        // Missing/malformed safety metadata is unknown, not an implicit safe value.
        return !"unsolved".equalsIgnoreCase(status)
                || !(privateKeyAlreadyPublic instanceof Boolean)
                || !(unsafeForWallets instanceof Boolean)
                || (Boolean) privateKeyAlreadyPublic
                || (Boolean) unsafeForWallets;
    }

    static boolean hasSupportedSearchBits(int searchBits) {
        // The current native UI contract supports positive range widths only.
        return searchBits >= 1 && searchBits <= 256;
    }
}
