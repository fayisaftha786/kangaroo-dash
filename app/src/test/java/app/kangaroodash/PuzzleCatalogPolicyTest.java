package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.*;

public class PuzzleCatalogPolicyTest {
    @Test public void blocksSolvedEntries() {
        assertTrue(PuzzleCatalogPolicy.isBlocked("solved", true, true));
    }

    @Test public void blocksAnyEntryMarkedUnsafeEvenIfStatusSaysUnsolved() {
        assertTrue(PuzzleCatalogPolicy.isBlocked("unsolved", false, true));
        assertTrue(PuzzleCatalogPolicy.isBlocked("unsolved", true, false));
    }

    @Test public void blocksUnknownOrMissingStatus() {
        assertTrue(PuzzleCatalogPolicy.isBlocked("unknown", false, false));
        assertTrue(PuzzleCatalogPolicy.isBlocked(null, false, false));
    }

    @Test public void blocksMissingOrMalformedSafetyMetadata() {
        assertTrue(PuzzleCatalogPolicy.isBlocked("unsolved", null, false));
        assertTrue(PuzzleCatalogPolicy.isBlocked("unsolved", false, null));
        assertTrue(PuzzleCatalogPolicy.isBlocked("unsolved", "false", false));
    }

    @Test public void permitsOnlyUnsolvedUnflaggedEntries() {
        assertFalse(PuzzleCatalogPolicy.isBlocked("unsolved", false, false));
    }

    @Test public void requiresPositiveSupportedRangeWidth() {
        assertFalse(PuzzleCatalogPolicy.hasSupportedSearchBits(0));
        assertFalse(PuzzleCatalogPolicy.hasSupportedSearchBits(-1));
        assertTrue(PuzzleCatalogPolicy.hasSupportedSearchBits(1));
        assertTrue(PuzzleCatalogPolicy.hasSupportedSearchBits(256));
        assertFalse(PuzzleCatalogPolicy.hasSupportedSearchBits(257));
    }

    @Test public void allowsStartOnlyWhenValuesMatchLoadedCatalogRecord() {
        assertTrue(PuzzleCatalogPolicy.matchesLoadedValues(
                "02ABC", "0001", "4", "02abc", "0001", "4"));
        assertFalse(PuzzleCatalogPolicy.matchesLoadedValues(
                "02ABC", "0001", "4", "02abc", "0002", "4"));
        assertFalse(PuzzleCatalogPolicy.matchesLoadedValues(
                "02ABC", "0001", "4", "02abc", "0001", "5"));
        assertFalse(PuzzleCatalogPolicy.matchesLoadedValues(
                null, "0001", "4", "02abc", "0001", "4"));
    }

    @Test public void acceptsInternallyConsistentInclusiveRangeMetadata() {
        assertTrue(PuzzleCatalogPolicy.matchesRangeMetadata(4, "08", "0f", "8", 3));
        assertTrue(PuzzleCatalogPolicy.matchesRangeMetadata(4,
                "0x08", "0X0f", "8", 3));
    }

    @Test public void rejectsEndSizeAndSearchWidthMismatches() {
        assertFalse(PuzzleCatalogPolicy.matchesRangeMetadata(4, "08", "10", "8", 3));
        assertFalse(PuzzleCatalogPolicy.matchesRangeMetadata(4, "08", "0f", "7", 3));
        assertFalse(PuzzleCatalogPolicy.matchesRangeMetadata(4, "08", "0f", "8", 2));
        assertFalse(PuzzleCatalogPolicy.matchesRangeMetadata(4, "10", "1f", "16", 3));
        assertFalse(PuzzleCatalogPolicy.matchesRangeMetadata(0, "00", "00", "1", -1));
        assertFalse(PuzzleCatalogPolicy.matchesRangeMetadata(4, "not-hex", "0f", "8", 3));
    }
}
