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
     * Checks input metadata separately so the UI can display valid data for inspection.
     * This does not authorize starting the native engine.
     */
    static boolean hasValidSearchMetadata(boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits) {
        return publicKeyValid && startValid && rangeMetadataValid
                && PuzzleCatalogPolicy.hasSupportedSearchBits(searchBits);
    }

    /** Native execution is denied unless the record explicitly carries Boolean true. */
    static boolean hasNativeSearchApproval(Object value) {
        return value instanceof Boolean && Boolean.TRUE.equals(value);
    }

    /**
     * Runtime eligibility requires safety flags, valid search metadata, a record-scoped
     * HTTPS source reference, and explicit native-search approval. Source presence alone
     * never grants execution permission.
     */
    static boolean isExecutionEligible(String status, Object privateKeyAlreadyPublic,
            Object unsafeForWallets, boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits, String sourceUrl, String sourceLabel,
            String sourceScope, Object nativeSearchApproval) {
        return !PuzzleCatalogPolicy.isBlocked(status, privateKeyAlreadyPublic, unsafeForWallets)
                && hasValidSearchMetadata(publicKeyValid, startValid, rangeMetadataValid, searchBits)
                && hasSourceReference(sourceUrl, sourceLabel, sourceScope)
                && hasNativeSearchApproval(nativeSearchApproval);
    }

    /** Compatibility name used by the catalog audit for a locally eligible record. */
    static boolean isLocallyRunnable(String status, Object privateKeyAlreadyPublic,
            Object unsafeForWallets, boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits, String sourceUrl, String sourceLabel,
            String sourceScope, Object nativeSearchApproval) {
        return isExecutionEligible(status, privateKeyAlreadyPublic, unsafeForWallets,
                publicKeyValid, startValid, rangeMetadataValid, searchBits,
                sourceUrl, sourceLabel, sourceScope, nativeSearchApproval);
    }
}
