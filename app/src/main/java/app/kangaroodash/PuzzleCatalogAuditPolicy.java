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
     * Runtime eligibility is based on safety flags, a valid public key, and verified range
     * arithmetic. Missing source metadata is reported separately: URLs help reviewers trace
     * claims, but URL presence is not proof of authenticity and must not masquerade as a
     * mathematical/runtime requirement.
     */
        /**
     * Checks input metadata separately so the UI can display valid data for inspection.
     * This does not authorize starting the native engine.
     */
    static boolean hasValidSearchMetadata(boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits) {
        return publicKeyValid && startValid && rangeMetadataValid
                && PuzzleCatalogPolicy.hasSupportedSearchBits(searchBits);
    }

    /**
     * Runtime eligibility requires safety flags, valid search metadata, and a complete
     * record-scoped HTTPS source reference. URL presence alone does not prove authenticity.
     */
    static boolean isExecutionEligible(String status, Object privateKeyAlreadyPublic,
            Object unsafeForWallets, boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits, String sourceUrl, String sourceLabel,
            String sourceScope) {
        return !PuzzleCatalogPolicy.isBlocked(status, privateKeyAlreadyPublic, unsafeForWallets)
                && hasValidSearchMetadata(publicKeyValid, startValid, rangeMetadataValid, searchBits)
                && hasSourceReference(sourceUrl, sourceLabel, sourceScope);
    }

    /** Compatibility name used by the catalog audit for a locally eligible record. */
    static boolean isLocallyRunnable(String status, Object privateKeyAlreadyPublic,
            Object unsafeForWallets, boolean publicKeyValid, boolean startValid,
            boolean rangeMetadataValid, int searchBits, String sourceUrl, String sourceLabel,
            String sourceScope) {
        return isExecutionEligible(status, privateKeyAlreadyPublic, unsafeForWallets,
                publicKeyValid, startValid, rangeMetadataValid, searchBits,
                sourceUrl, sourceLabel, sourceScope);
    }
}
