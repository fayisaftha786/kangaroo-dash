package app.kangaroodash;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/** Small bounded diagnostic log: current log plus one rotated backup. */
final class DiagnosticLog {
    static final long MAX_BYTES = 256L * 1024L;
    private static final String BACKUP_SUFFIX = ".1";

    private DiagnosticLog() {}

    /** Reads only the newest lines so the dashboard never renders an unbounded log. */
    static synchronized String readTail(File file, int maxLines) throws IOException {
        if (file == null) throw new IOException("Log file is unavailable");
        if (maxLines <= 0 || !file.isFile()) return "";
        Deque<String> lines = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == maxLines) lines.removeFirst();
                lines.addLast(line);
            }
        }
        StringBuilder result = new StringBuilder();
        for (String line : lines) result.append(line).append(System.lineSeparator());
        return result.toString();
    }

    static synchronized void append(File file, String message) throws IOException {
        if (file == null) throw new IOException("Log file is unavailable");
        String safeMessage = message == null ? "" : message;
        // Bound even a single unexpectedly huge native output line. Avoid
        // cutting a UTF-16 surrogate pair in half before UTF-8 encoding.
        int maxChars = (int) (MAX_BYTES / 4 - 1);
        if (safeMessage.length() > maxChars) {
            if (Character.isHighSurrogate(safeMessage.charAt(maxChars - 1))) {
                maxChars--;
            }
            safeMessage = safeMessage.substring(0, maxChars);
        }
        byte[] bytes = (safeMessage + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        File backup = new File(file.getParentFile(), file.getName() + BACKUP_SUFFIX);
        // Remove oversized files left by an older build so both retained
        // log files obey the same cap.
        if (backup.exists() && backup.length() > MAX_BYTES && !backup.delete()) {
            throw new IOException("Could not remove oversized diagnostic backup");
        }
        if (file.exists() && file.length() > MAX_BYTES) {
            // Do not preserve an oversized legacy log as the backup.
            if (!file.delete()) {
                throw new IOException("Could not remove oversized diagnostic log");
            }
        } else if (file.exists() && file.length() + bytes.length > MAX_BYTES) {
            if (backup.exists() && !backup.delete()) {
                throw new IOException("Could not remove old log backup");
            }
            if (!file.renameTo(backup)) {
                throw new IOException("Could not rotate diagnostic log");
            }
        }
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(bytes);
        }
    }
}
