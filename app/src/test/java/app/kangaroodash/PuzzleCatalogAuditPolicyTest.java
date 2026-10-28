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

    @Test public void recordSourceRequiresHttpsUrlLabelAndRecordScope() {
        assertTrue(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://example.org/puzzles/1", "Community reference", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(null, "Community reference", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "http://example.org/puzzles/1", "Community reference", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://example.org/puzzles/1", null, "record"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://example.org/puzzles/1", "   ", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://lotterypuzzle.org/puzzles/", "Secondary dataset index", "dataset"));
        assertFalse(PuzzleCatalogAuditPolicy.hasSourceReference(
                "https://example.org/puzzles/1", "Community reference", "missing"));
    }

    @Test public void localEligibilityRequiresEveryRuntimeGateAndSourceReference() {
        assertTrue(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "solved", true, true, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, false, true, true, 3,
                "https://example.org/puzzles/1", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, false, true, 3,
                "https://example.org/puzzles/1", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, false, 3,
                "https://example.org/puzzles/1", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 0,
                "https://example.org/puzzles/1", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 3, null, "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 3,
                "https://example.org/puzzles/1", "", "record"));
    }

    @Test public void missingPerRecordSourceBlocksNativeStartButValidMetadataCanBeInspected() {
        assertTrue(PuzzleCatalogAuditPolicy.hasValidSearchMetadata(true, true, true, 139));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, true, true, true, 139,
                null, null, "missing"));
    }

    @Test public void runtimeEligibilityStillBlocksUnsafeOrInvalidEntries() {
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "solved", true, true, true, true, true, 139,
                "https://example.org/puzzles/139", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, false, true, true, 139,
                "https://example.org/puzzles/139", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, true, false, true, 139,
                "https://example.org/puzzles/139", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, true, true, false, 139,
                "https://example.org/puzzles/139", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, true, true, true, 0,
                "https://example.org/puzzles/139", "Test source", "record"));
    }

    @Test public void runtimeEligibilityStillBlocksUnsafeOrInvalidEntries() {
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "solved", true, true, true, true, true, 139));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, false, true, true, 139));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, true, false, true, 139));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, true, true, false, 139));
        assertFalse(PuzzleCatalogAuditPolicy.isExecutionEligible(
                "unsolved", false, false, true, true, true, 0));
    }

    @Test public void datasetWideReferenceCannotMakeEntryLocallyRunnable() {
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, false, true, true, true, 3,
                "https://lotterypuzzle.org/puzzles/", "Secondary dataset index", "dataset"));
    }

    @Test public void missingSafetyMetadataIsNeverLocallyRunnable() {
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", null, false, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source", "record"));
        assertFalse(PuzzleCatalogAuditPolicy.isLocallyRunnable(
                "unsolved", false, null, true, true, true, 3,
                "https://example.org/puzzles/1", "Test source", "record"));
    }
}
