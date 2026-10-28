package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.*;

public class PuzzleCatalogAuditPolicyTest {
    @Test public void acceptsHttpsUrlWithHost() {
        assertTrue(PuzzleCatalogAuditPolicy.isHttpsSource("https://example.org/puzzles/1"));
        assertTrue(PuzzleCatalogAuditPolicy.isHttpsSource("HTTPS://example.org/source"));
    }

    @Test public void rejectsMissingMalformedAndNonHttpsSource() {
        assertFalse(PuzzleCatalogAuditPolicy.isHttpsSource(null));
        assertFalse(PuzzleCatalogAuditPolicy.isHttpsSource(""));
        assertFalse(PuzzleCatalogAuditPolicy.isHttpsSource("http://example.org/source"));
        assertFalse(PuzzleCatalogAuditPolicy.isHttpsSource("https:///missing-host"));
        assertFalse(PuzzleCatalogAuditPolicy.isHttpsSource("not a URL"));
    }

    @Test public void sourceReferenceRequiresHttpsUrlAndNonEmptyLabel() {
        assertTrue(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://example.org/puzzles/1", "Community reference"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(null, "Community reference"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "http://example.org/puzzles/1", "Community reference"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://example.org/puzzles/1", null));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://example.org/puzzles/1", "   "));
    }

    @Test public void localEligibilityRequiresEveryRuntimeGateAndSourceReference() {
        assertTrue(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "solved", true, true, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, false, true, true, 3,
                "https://example.org/puzzles/1", "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, false, true, 3,
                "https://example.org/puzzles/1", "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, false, 3,
                "https://example.org/puzzles/1", "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 0,
                "https://example.org/puzzles/1", "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 3, null, "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 3,
                "https://example.org/puzzles/1", ""));
    }

    @Test public void missingSafetyMetadataIsNeverLocallyRunnable() {
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", null, false, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, null, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source"));
    }
}
