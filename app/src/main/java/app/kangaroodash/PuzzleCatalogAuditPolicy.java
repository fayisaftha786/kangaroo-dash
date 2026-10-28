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
     * Requires a record-specific HTTPS source and explicit record scope.
     * A dataset-wide URL is not sufficient for selecting an entry.
     */
    static boolean hasSourceReference(String sourceUrl, String sourceLabel, String sourceScope) {
        return isHttpsSource(sourceUrl)
                && sourceLabel != null
                && !sourceLabel.trim().isEmpty()
                && sourceScope != null
                && "record".equalsIgnoreCase(sourceScope.trim());
    }

    /**
     * Mirrors the app's local selection gates, including a documented record-specific source.
     * A true return does not imply source authenticity or native-engine correctness.
     */
    static boolean isLocallyRunnable(String status, Object privateKeyAlreadyPublic,
            Object unsafeForWallets, boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits, String sourceUrl, String sourceLabel,
            String sourceScope) {
        return !PuzzleCatalogPolicy.isBlocked(status, privateKeyAlreadyPublic, unsafeForWallets)
                && publicKeyValid
                && startValid
                && rangeMetadataValid
                && PuzzleCatalogPolicy.hasSupportedSearchBits(searchBits)
                && hasSourceReference(sourceUrl, sourceLabel, sourceScope);
    }
}
