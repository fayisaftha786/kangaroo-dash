package app.kangaroodash;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class DiagnosticLogTest {
    @Test public void rotatesLogWhenSizeLimitIsExceeded() throws Exception {
        File dir = Files.createTempDirectory("kangaroo-log-test").toFile();
        File log = new File(dir, "scan.log");
        try {
            StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < 1024; i++) chunk.append('x');
            for (int i = 0; i < 260; i++) DiagnosticLog.append(log, chunk.toString());
            assertTrue(log.length() > DiagnosticLog.MAX_BYTES);

            DiagnosticLog.append(log, "new-session");
            File backup = new File(dir, "scan.log.1");
            assertTrue("previous log should be preserved as backup", backup.isFile());
            assertTrue(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8)
                    .contains("new-session"));
            assertTrue(new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8)
                    .contains("xxx"));
        } finally {
            File backup = new File(dir, "scan.log.1");
            if (backup.exists()) backup.delete();
            if (log.exists()) log.delete();
            dir.delete();
        }
    }

    @Test public void appendsUtf8Messages() throws Exception {
        File dir = Files.createTempDirectory("kangaroo-log-utf8").toFile();
        File log = new File(dir, "scan.log");
        try {
            DiagnosticLog.append(log, "താപനില ഉയർന്നിരിക്കുന്നു");
            String contents = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertTrue(contents.contains("താപനില ഉയർന്നിരിക്കുന്നു"));
        } finally {
            if (log.exists()) log.delete();
            dir.delete();
        }
    }
}
