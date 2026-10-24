package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppHealthCheckTest {
    @Test public void reportsMissingAndEmptyFilesClearly() {
        assertEquals("MISSING", AppHealthCheck.fileState(false, 0));
        assertEquals("EMPTY", AppHealthCheck.fileState(true, 0));
        assertEquals("EMPTY", AppHealthCheck.fileState(true, -1));
    }

    @Test public void reportsNonEmptyFileSizes() {
        assertEquals("OK (1 B)", AppHealthCheck.fileState(true, 1));
        assertEquals("OK (1.0 KB)", AppHealthCheck.fileState(true, 1024));
        assertEquals("OK (1.0 MB)", AppHealthCheck.fileState(true, 1024L * 1024L));
    }

    @Test public void formatsStorageSizesWithoutNegativeValues() {
        assertEquals("0 B", AppHealthCheck.formatBytes(-1));
        assertEquals("1023 B", AppHealthCheck.formatBytes(1023));
        assertEquals("1.0 KB", AppHealthCheck.formatBytes(1024));
    }
}
