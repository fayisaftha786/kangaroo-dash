package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppHealthCheckTest {
    @Test public void formatsKnownBatteryTemperature() {
        assertEquals("36.5°C", AppHealthCheck.formatTemperature(36.5f));
    }

    @Test public void reportsUnknownBatteryTemperatureHonestly() {
        assertEquals("unavailable", AppHealthCheck.formatTemperature(Float.NaN));
        assertEquals("unavailable", AppHealthCheck.formatTemperature(Float.POSITIVE_INFINITY));
        assertEquals("unavailable", AppHealthCheck.formatTemperature(-0.1f));
    }

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

    @Test public void reportsAllocatableStorageUsingExplicitThreshold() {
        assertEquals("PASS  Allocatable app storage: 20.0 MB",
                AppHealthCheck.formatAllocatableStorage(20L * 1024L * 1024L));
        assertEquals("WARN  Allocatable app storage is low: 19.0 MB",
                AppHealthCheck.formatAllocatableStorage(19L * 1024L * 1024L));
    }

    @Test public void reportsAllocatableStorageUnavailableWithoutGuessing() {
        assertEquals("WARN  Allocatable app storage: unavailable",
                AppHealthCheck.formatAllocatableStorage(-1));
    }
}
