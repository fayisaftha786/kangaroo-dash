package app.kangaroodash;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Bounded, read-only inventory of app-private storage.
 *
 * Only names and metadata are inspected. Symlinks are skipped; file contents
 * are never read, and nothing is deleted or modified.
 */
final class StorageInventory {
    private static final int MAX_DISPLAYED_ENTRIES = 40;
    private static final int MAX_SCANNED_ENTRIES = 500;
    private static final int MAX_DEPTH = 3;

    private StorageInventory() {}

    static String createReport(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return "Storage inventory: unavailable (app storage directory missing)";
        }

        List<Entry> inventory = new ArrayList<>();
        Deque<Entry> pending = new ArrayDeque<>();
        pending.add(new Entry(directory, "", 0));

        int scanned = 0;
        int fileCount = 0;
        int directoryCount = 0;
        int reviewCount = 0;
        int listingFailures = 0;
        long totalBytes = 0L;
        boolean truncated = false;

        while (!pending.isEmpty()) {
            Entry parent = pending.removeFirst();
            File[] children;
            try {
                children = parent.file.listFiles();
            } catch (SecurityException e) {
                listingFailures++;
                continue;
            }
            if (children == null) {
                // File.listFiles() returns null when listing fails; do not silently
                // present a partial inventory as complete.
                listingFailures++;
                continue;
            }
            Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

            for (File child : children) {
                if (scanned >= MAX_SCANNED_ENTRIES) {
                    truncated = true;
                    pending.clear();
                    break;
                }
                scanned++;
                if (isSymbolicLink(child)) continue;

                String relative = parent.relativePath.isEmpty()
                        ? child.getName() : parent.relativePath + "/" + child.getName();
                Entry item = new Entry(child, relative, parent.depth + 1);
                inventory.add(item);

                if (child.isDirectory()) {
                    directoryCount++;
                    if (item.depth < MAX_DEPTH) pending.addLast(item);
                } else {
                    fileCount++;
                    long length = Math.max(0L, child.length());
                    totalBytes = totalBytes > Long.MAX_VALUE - length
                            ? Long.MAX_VALUE : totalBytes + length;
                    if (isReviewCandidate(child.getName())) reviewCount++;
                }
            }
        }

        StringBuilder report = new StringBuilder("Storage inventory (read-only metadata): ");
        report.append(fileCount).append(" file(s), ")
                .append(directoryCount).append(directoryCount == 1 ? " directory, " : " directories, ")
                .append(AppHealthCheck.formatBytes(totalBytes)).append(" total file data.\n");

        int displayed = Math.min(inventory.size(), MAX_DISPLAYED_ENTRIES);
        for (int i = 0; i < displayed; i++) {
            Entry item = inventory.get(i);
            report.append("  ");
            if (item.file.isDirectory()) {
                report.append("[DIR] ");
            } else if (isCoreFile(item.file.getName())) {
                report.append("[CORE] ");
            } else if (isReviewCandidate(item.file.getName())) {
                report.append("[REVIEW ONLY] ");
            } else {
                report.append("[PRESERVED] ");
            }
            report.append(item.relativePath);
            if (!item.file.isDirectory()) {
                report.append(" — ").append(AppHealthCheck.formatBytes(Math.max(0L, item.file.length())));
            }
            report.append('\n');
        }
        if (inventory.size() > displayed) {
            report.append("  … ").append(inventory.size() - displayed)
                    .append(" additional item(s) not displayed.\n");
        }
        if (truncated) {
            report.append("WARN  Scan limit reached (").append(MAX_SCANNED_ENTRIES)
                    .append(" entries); inventory may be incomplete.\n");
        }
        if (listingFailures > 0) {
            report.append("WARN  Could not list ").append(listingFailures)
                    .append(" director(y/ies); inventory may be incomplete.\n");
        }
        report.append("Directory listing failures: ").append(listingFailures).append(".\n")
                .append("Temporary/chunk-looking files for manual review: ").append(reviewCount).append(".\n")
                .append("Limits: depth ").append(MAX_DEPTH).append(", entries ").append(MAX_SCANNED_ENTRIES).append(".\n")
                .append("Safety: inventory only; no files were deleted or changed.");
        return report.toString();
    }

    private static boolean isSymbolicLink(File file) {
        try {
            return Files.isSymbolicLink(file.toPath());
        } catch (SecurityException e) {
            // Fail closed: avoid traversing an entry whose type cannot be checked.
            return true;
        }
    }

    private static boolean isCoreFile(String name) {
        return "dp.json".equals(name)
                || "found.txt".equals(name)
                || "scan.log".equals(name)
                || "scan.log.1".equals(name);
    }

    private static boolean isReviewCandidate(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".tmp")
                || lower.endsWith(".temp")
                || lower.endsWith(".part")
                || lower.contains("chunk");
    }

    private static final class Entry {
        final File file;
        final String relativePath;
        final int depth;

        Entry(File file, String relativePath, int depth) {
            this.file = file;
            this.relativePath = relativePath;
            this.depth = depth;
        }
    }
}
