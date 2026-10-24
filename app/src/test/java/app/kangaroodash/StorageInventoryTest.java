package app.kangaroodash;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class StorageInventoryTest {
    @Test public void reportsCoreReviewAndUnknownFilesWithoutDeletingThem() throws Exception {
        File directory = Files.createTempDirectory("kangaroo-inventory").toFile();
        File dp = new File(directory, "dp.json");
        File temporary = new File(directory, "old-chunk.tmp");
        File unknown = new File(directory, "custom-state.bin");
        try {
            write(dp, "{\"sample\":1}");
            write(temporary, "candidate");
            write(unknown, "keep me");

            String report = StorageInventory.createReport(directory);

            assertTrue(report.contains("[CORE] dp.json"));
            assertTrue(report.contains("[REVIEW ONLY] old-chunk.tmp"));
            assertTrue(report.contains("[PRESERVED] custom-state.bin"));
            assertTrue(report.contains("Directory listing failures: 0"));
            assertTrue(report.contains("no files were deleted or changed"));
            assertTrue(dp.isFile());
            assertTrue(temporary.isFile());
            assertTrue(unknown.isFile());
        } finally {
            deleteTree(directory);
        }
    }

    @Test public void findsNestedChunkCandidatesWithoutDeletingThem() throws Exception {
        File directory = Files.createTempDirectory("kangaroo-nested").toFile();
        File nested = new File(directory, "cache/session-a");
        assertTrue(nested.mkdirs());
        File chunk = new File(nested, "chunk-004.bin");
        try {
            write(chunk, "preserve");
            String report = StorageInventory.createReport(directory);
            assertTrue(report.contains("[REVIEW ONLY] cache/session-a/chunk-004.bin"));
            assertTrue(report.contains("Temporary/chunk-looking files for manual review: 1"));
            assertTrue(chunk.isFile());
        } finally {
            deleteTree(directory);
        }
    }

    @Test public void handlesMissingDirectoryWithoutThrowing() {
        assertTrue(StorageInventory.createReport(null).contains("unavailable"));
        assertTrue(StorageInventory.createReport(new File("missing-kangaroo-dir"))
                .contains("unavailable"));
    }

    @Test public void limitsDisplayedEntriesButCountsAllFiles() throws Exception {
        File directory = Files.createTempDirectory("kangaroo-inventory-many").toFile();
        try {
            for (int i = 0; i < 45; i++) {
                write(new File(directory, String.format(java.util.Locale.ROOT, "item-%02d.bin", i)), "x");
            }
            String report = StorageInventory.createReport(directory);
            assertTrue(report.contains("45 file(s)"));
            assertTrue(report.contains("5 additional item(s) not displayed"));
            assertTrue(report.contains("Directory listing failures: 0"));
        } finally {
            deleteTree(directory);
        }
    }

    @Test public void stopsAtScanLimitAndClearlyMarksInventoryIncomplete() throws Exception {
        File directory = Files.createTempDirectory("kangaroo-inventory-limit").toFile();
        try {
            for (int i = 0; i < 510; i++) {
                write(new File(directory, String.format(java.util.Locale.ROOT, "item-%04d.bin", i)), "x");
            }
            String report = StorageInventory.createReport(directory);
            assertTrue(report.contains("500 file(s)"));
            assertTrue(report.contains("WARN  Scan limit reached (500 entries); inventory may be incomplete."));
            assertTrue(report.contains("Directory listing failures: 0"));
            assertTrue(new File(directory, "item-0509.bin").isFile());
        } finally {
            deleteTree(directory);
        }
    }

    @Test public void skipsSymbolicLinksWithoutTraversingThem() throws Exception {
        File directory = Files.createTempDirectory("kangaroo-inventory-symlink").toFile();
        File targetDirectory = Files.createTempDirectory("kangaroo-inventory-target").toFile();
        try {
            File target = new File(targetDirectory, "hidden.bin");
            write(target, "must remain untouched");
            File link = new File(directory, "linked-directory");
            try {
                Files.createSymbolicLink(link.toPath(), targetDirectory.toPath());
            } catch (UnsupportedOperationException | java.io.IOException | SecurityException e) {
                org.junit.Assume.assumeNoException("Symbolic links unavailable in this environment", e);
            }

            String report = StorageInventory.createReport(directory);
            assertFalse(report.contains("linked-directory/hidden.bin"));
            assertTrue(target.isFile());
        } finally {
            deleteTree(directory);
            deleteTree(targetDirectory);
        }
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void deleteTree(File file) {
        // Never follow a symlink during test cleanup: deleting a temporary test
        // directory must not recursively remove files outside that directory.
        if (Files.isSymbolicLink(file.toPath())) {
            file.delete();
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
