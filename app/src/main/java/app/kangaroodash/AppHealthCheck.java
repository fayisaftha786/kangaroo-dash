package app.kangaroodash;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only local checks for common app setup and runtime problems. */
final class AppHealthCheck {
    private AppHealthCheck() {}

    static String fileState(boolean exists, long length) {
        if (!exists) return "MISSING";
        return length <= 0 ? "EMPTY" : "OK (" + formatBytes(length) + ")";
    }

    static String formatTemperature(float celsius) {
        if (!Float.isFinite(celsius) || celsius < 0f) return "unavailable";
        return String.format(Locale.US, "%.1f°C", celsius);
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) return Math.max(0, bytes) + " B";
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static String createReport(Activity activity) {
        List<String> lines = new ArrayList<>();
        int passed = 0;
        int warnings = 0;
        int failures = 0;

        File engine = new File(activity.getApplicationInfo().nativeLibraryDir, "libkangaroo.so");
        if (engine.isFile() && engine.canRead() && engine.canExecute()) {
            lines.add("PASS  Native engine: present, readable and executable");
            passed++;
        } else {
            lines.add("FAIL  Native engine: missing, unreadable or not executable");
            failures++;
        }

        File filesDir = activity.getFilesDir();
        File probe = null;
        try {
            probe = File.createTempFile("health-check-", ".tmp", filesDir);
            lines.add("PASS  Internal storage: writable");
            passed++;
        } catch (IOException | SecurityException e) {
            lines.add("FAIL  Internal storage: cannot create a temporary file (" +
                    e.getClass().getSimpleName() + ")");
            failures++;
        } finally {
            if (probe != null && probe.exists() && !probe.delete()) {
                lines.add("WARN  Internal storage: temporary probe could not be deleted");
                warnings++;
            }
        }

        long usable = filesDir.getUsableSpace();
        if (usable >= 20L * 1024L * 1024L) {
            lines.add("PASS  Free app storage: " + formatBytes(usable));
            passed++;
        } else {
            lines.add("WARN  Free app storage is low: " + formatBytes(usable));
            warnings++;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                lines.add("PASS  Notifications permission: granted");
                passed++;
            } else {
                lines.add("WARN  Notifications permission: not granted; foreground notifications may be less visible");
                warnings++;
            }
        } else {
            lines.add("PASS  Notifications permission: runtime permission not required on this Android version");
            passed++;
        }

        try {
            Intent battery = activity.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (tenths >= 0) {
                    float celsius = tenths / 10f;
                    if (celsius >= 45f) {
                        lines.add(String.format(Locale.US,
                                "WARN  Battery temperature: %.1f°C; pause heavy work and let device cool", celsius));
                        warnings++;
                    } else {
                        lines.add(String.format(Locale.US, "PASS  Battery temperature: %.1f°C", celsius));
                        passed++;
                    }
                } else {
                    lines.add("INFO  Battery temperature: unavailable");
                }
                if (level >= 0 && scale > 0) {
                    int percent = Math.round(level * 100f / scale);
                    if (percent < 15) {
                        lines.add("WARN  Battery level is low: " + percent + "%");
                        warnings++;
                    } else {
                        lines.add("PASS  Battery level: " + percent + "%");
                        passed++;
                    }
                } else {
                    lines.add("INFO  Battery level: unavailable");
                }
            } else {
                lines.add("INFO  Battery status: unavailable");
            }
        } catch (RuntimeException e) {
            // Some OEM Android builds may reject a system-service query. Diagnostics
            // must report unavailable data rather than crash the health dialog.
            lines.add("INFO  Battery status: unavailable (" + e.getClass().getSimpleName() + ")");
        }

        File dpFile = new File(filesDir, "dp.json");
        String dpState = fileState(dpFile.exists(), dpFile.exists() ? dpFile.length() : 0);
        if (dpFile.exists() && dpFile.length() > 0) {
            lines.add("INFO  DP persistence file: " + dpState + "; native save/load integrity is not verified by this check");
        } else {
            lines.add("WARN  DP persistence file: " + dpState + "; it may be normal before the first run");
            warnings++;
        }

        File logFile = new File(filesDir, "scan.log");
        lines.add("INFO  Diagnostic log: " + fileState(logFile.exists(), logFile.exists() ? logFile.length() : 0));
        lines.add("");
        lines.add(StorageInventory.createReport(filesDir));
        lines.add("");
        lines.add("INFO  Solver state: " + (SolverService.running ? "RUNNING" :
                SolverService.isBusy() ? "STOPPING / CLEANUP IN PROGRESS" : "STOPPED"));
        lines.add("INFO  Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        lines.add("");
        lines.add("Summary: " + passed + " passed, " + warnings + " warning(s), " +
                failures + " failure(s).");
        lines.add("This is a local diagnostic, not proof of native solver correctness. Review the report before sharing.");
        return String.join("\n", lines);
    }
}
