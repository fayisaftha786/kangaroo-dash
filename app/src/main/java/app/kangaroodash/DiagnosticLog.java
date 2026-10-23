package app.kangaroodash;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small bounded diagnostic log: current log plus one rotated backup. */
final class DiagnosticLog {
    static final long MAX_BYTES = 256L * 1024L;
    private static final String BACKUP_SUFFIX = ".1";

    private DiagnosticLog() {}

    static synchronized void append(File file, String message) throws IOException {
        if (file == null) throw new IOException("Log file is unavailable");
        byte[] bytes = ((message == null ? "" : message) + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
        if (file.exists() && file.length() + bytes.length > MAX_BYTES) {
            File backup = new File(file.getParentFile(), file.getName() + BACKUP_SUFFIX);
            if (backup.exists() && !backup.delete()) {
                throw new IOException("Could not remove old log backup");
            }
            if (!file.renameTo(backup)) {
                throw new IOException("Could not rotate diagnostic log");
            }
        }
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(bytes);
            out.getFD().sync();
        }
    }
}
