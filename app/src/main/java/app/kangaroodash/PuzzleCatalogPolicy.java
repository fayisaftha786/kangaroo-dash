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

    static boolean matchesLoadedValues(String expectedPublicKey, String expectedStart,
            String expectedRange, String actualPublicKey, String actualStart,
            String actualRange) {
        return expectedPublicKey != null && expectedStart != null && expectedRange != null
                && actualPublicKey != null && actualStart != null && actualRange != null
                && expectedPublicKey.equalsIgnoreCase(actualPublicKey.trim())
                && expectedStart.equalsIgnoreCase(actualStart.trim())
                && expectedRange.equals(actualRange.trim());
    }

    /**
     * Validate the bundled catalog's inclusive puzzle range independently at runtime.
     * This checks catalog arithmetic only; it does not prove the native CLI's semantics.
     */
    static boolean matchesRangeMetadata(int puzzleId, String startHex, String endHex,
            String sizeDecimal, int searchBits) {
        if (puzzleId < 1 || puzzleId > 160 || startHex == null || endHex == null
                || sizeDecimal == null || searchBits != puzzleId - 1) {
            return false;
        }
        try {
            java.math.BigInteger start = new java.math.BigInteger(stripHexPrefix(startHex), 16);
            java.math.BigInteger end = new java.math.BigInteger(stripHexPrefix(endHex), 16);
            java.math.BigInteger expectedStart = java.math.BigInteger.ONE.shiftLeft(puzzleId - 1);
            java.math.BigInteger expectedEnd = java.math.BigInteger.ONE.shiftLeft(puzzleId)
                    .subtract(java.math.BigInteger.ONE);
            java.math.BigInteger expectedSize = expectedEnd.subtract(expectedStart)
                    .add(java.math.BigInteger.ONE);
            return start.equals(expectedStart)
                    && end.equals(expectedEnd)
                    && end.compareTo(start) >= 0
                    && expectedSize.toString().equals(sizeDecimal.trim())
                    && end.subtract(start).add(java.math.BigInteger.ONE).equals(expectedSize);
        } catch (NumberFormatException | NullPointerException invalid) {
            return false;
        }
    }

    private static String stripHexPrefix(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("0x") || trimmed.startsWith("0X")
                ? trimmed.substring(2) : trimmed;
    }
}
