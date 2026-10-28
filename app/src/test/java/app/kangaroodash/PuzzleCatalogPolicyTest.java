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
}
