package app.kangaroodash;

import java.net.URI;
import java.net.URISyntaxException;

/** Pure helpers used by the on-device full catalog audit. */
final class PuzzleCatalogAuditPolicy {
    private PuzzleCatalogAuditPolicy() {}

    static boolean isHttpsSource(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            URI uri = new URI(value.trim());
            return uri.getScheme() != null
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Requires a documented HTTPS source reference for catalog selection.
     * This is a provenance floor only: it does not prove source authenticity.
     */
    static boolean hasSourceReference(String sourceUrl, String sourceLabel) {
        return isHttpsSource(sourceUrl)
                && sourceLabel != null
                && !sourceLabel.trim().isEmpty();
    }

    /**
     * Mirrors the app's local selection gates, including a documented source reference.
     * A true return does not imply source authenticity or native-engine correctness.
     */
    static boolean isLocallyRunnable(String status, Object privateKeyAlreadyPublic,
            Object unsafeForWallets, boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits, String sourceUrl, String sourceLabel) {
        return !PuzzleCatalogPolicy.isBlocked(status, privateKeyAlreadyPublic, unsafeForWallets)
                && publicKeyValid
                && startValid
                && rangeMetadataValid
                && PuzzleCatalogPolicy.hasSupportedSearchBits(searchBits)
                && hasSourceReference(sourceUrl, sourceLabel);
    }
}
