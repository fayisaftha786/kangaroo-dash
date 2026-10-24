package app.kangaroodash;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Read-only inventory of app-private storage.
 *
 * File names and sizes are inspected only; file contents are never read and no
 * files are deleted or modified. Unknown files are explicitly preserved.
 */
final class StorageInventory {
    private static final int MAX_DISPLAYED_ENTRIES = 40;

    private StorageInventory() {}

    static String createReport(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return "Storage inventory: unavailable (app storage directory missing)";
        }

        File[] entries = directory.listFiles();
        if (entries == null) {
            return "Storage inventory: unavailable (directory could not be listed)";
        }
        Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        long totalBytes = 0L;
        int fileCount = 0;
        int directoryCount = 0;
        int reviewCount = 0;
        StringBuilder report = new StringBuilder("Storage inventory (metadata only): ");
        for (File entry : entries) {
            if (entry.isDirectory()) {
                directoryCount++;
            } else {
                fileCount++;
                long length = Math.max(0L, entry.length());
                totalBytes = totalBytes > Long.MAX_VALUE - length
                        ? Long.MAX_VALUE : totalBytes + length;
                if (isTemporaryLooking(entry.getName())) reviewCount++;
            }
        }

        report.append(fileCount).append(" file(s), ")
                .append(directoryCount).append(" director(y/ies), ")
                .append(AppHealthCheck.formatBytes(totalBytes)).append(" total file data.\n");

        int displayed = Math.min(entries.length, MAX_DISPLAYED_ENTRIES);
        for (int i = 0; i < displayed; i++) {
            File entry = entries[i];
            report.append("  ");
            if (entry.isDirectory()) {
                report.append("[DIR] ");
            } else if (isCoreFile(entry.getName())) {
                report.append("[CORE] ");
            } else if (isTemporaryLooking(entry.getName())) {
                report.append("[REVIEW ONLY] ");
            } else {
                report.append("[PRESERVED] ");
            }
            report.append(entry.getName());
            if (!entry.isDirectory()) {
                report.append(" — ").append(AppHealthCheck.formatBytes(Math.max(0L, entry.length())));
            }
            report.append('\n');
        }
        if (entries.length > displayed) {
            report.append("  … ").append(entries.length - displayed)
                    .append(" additional item(s) not displayed.\n");
        }
        report.append("Temporary-looking files for manual review: ").append(reviewCount).append(".\n")
                .append("Safety: inventory only; no files were deleted or changed.");
        return report.toString();
    }

    private static boolean isCoreFile(String name) {
        return "dp.json".equals(name)
                || "found.txt".equals(name)
                || "scan.log".equals(name)
                || "scan.log.1".equals(name);
    }

    private static boolean isTemporaryLooking(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return (lower.startsWith("health-check-") && lower.endsWith(".tmp"))
                || lower.endsWith(".tmp")
                || lower.endsWith(".temp")
                || lower.endsWith(".part");
    }
}
