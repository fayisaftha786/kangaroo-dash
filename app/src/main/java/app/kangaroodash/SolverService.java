package app.kangaroodash;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class SolverService extends Service {
    static volatile boolean running = false;
    static volatile String status = "നിർത്തി", ops = "-", dps = "-", found = "";
    static volatile int restarts = 0;
    static volatile long sessionStartMs = 0, elapsedMs = 0;
    static volatile String speed = "—";
    static volatile boolean resultVerified = false;
    static volatile long lastTelemetryAtMs = 0;
    static volatile float temp = 0;
    volatile Process proc;
    volatile Thread worker;
    private PowerManager.WakeLock wl;
    static final Pattern OPS = Pattern.compile("Ops: (\\d+)M \\| DPs: (\\d+)");

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int f, int id) {
        if (in != null && "stop".equals(in.getAction())) {
            stopAll();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (in == null || running) return START_NOT_STICKY;
        try {
            NotificationChannel ch = new NotificationChannel("k", "Kangaroo", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, "k")
                .setContentTitle("Kangaroo ഓടുന്നു")
                .setSmallIcon(android.R.drawable.ic_media_play).build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, n);
            }
            wl = ((PowerManager) getSystemService(POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "k:run");
            wl.acquire();
        } catch (RuntimeException e) {
            status = "Service start failed: " + safeMessage(e);
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        final String[] a = { in.getStringExtra("pub"), in.getStringExtra("start"),
                in.getStringExtra("range"), in.getStringExtra("dp"), in.getStringExtra("k") };
        running = true;
        restarts = 0;
        found = "";
        resultVerified = false;
        lastTelemetryAtMs = 0;
        sessionStartMs = SystemClock.elapsedRealtime();
        elapsedMs = 0;
        speed = "—";
        ops = "-";
        dps = "-";
        worker = new Thread(() -> loop(a), "kangaroo-engine-worker");
        worker.start();
        return START_NOT_STICKY;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName() : message;
    }

    float batt() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void appendLog(File logFile, String message) {
        try {
            DiagnosticLog.append(logFile, message);
        } catch (IOException e) {
            status = "Diagnostic log error: " + safeMessage(e);
        }
    }

    void loop(String[] a) {
        File dir = getFilesDir();
        File foundF = new File(dir, "found.txt");
        File logF = new File(dir, "scan.log");
        String bin = getApplicationInfo().nativeLibraryDir + "/libkangaroo.so";
        long previousOps = -1, previousOpsAt = 0;

        try {
            if (foundF.exists() && !foundF.delete()) {
                appendLog(logF, "Warning: stale found.txt could not be deleted");
            }
            while (running && !Thread.currentThread().isInterrupted()) {
                boolean processVerified = false;
                int exitCode = Integer.MIN_VALUE;
                temp = batt();
                if (temp >= 45f) {
                    status = "തണുക്കുന്നു " + temp + "°C";
                    pause(30000);
                    continue;
                }

                try {
                    ProcessBuilder pb = new ProcessBuilder(bin, "--pubkey", a[0],
                            "--start", a[1], "--range", a[2], "--dp-bits", a[3],
                            "--output", foundF.getAbsolutePath(), "--kangaroos", a[4]);
                    pb.directory(dir);
                    pb.environment().put("KANGAROO_DP_FILE",
                            new File(dir, "dp.json").getAbsolutePath());
                    pb.redirectErrorStream(true);
                    status = "ഓടുന്നു";
                    proc = pb.start();
                    appendLog(logF, "--- Solver process started: " + new java.util.Date() + " ---");

                    final String eof = "\u0000KANGAROO_EOF";
                    final BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
                    final Process currentProcess = proc;
                    Thread outputReader = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                            String outputLine;
                            while ((outputLine = reader.readLine()) != null) {
                                outputLines.put(outputLine);
                            }
                        } catch (Exception readerError) {
                            try {
                                outputLines.put("Engine output reader error: " + safeMessage(
                                        readerError instanceof Exception ? (Exception) readerError
                                                : new Exception(readerError)));
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        } finally {
                            try {
                                outputLines.put(eof);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }, "kangaroo-output-reader");
                    outputReader.setDaemon(true);
                    outputReader.start();

                    boolean outputEnded = false;
                    while (running && !Thread.currentThread().isInterrupted() && !outputEnded) {
                        String line = outputLines.poll(5, TimeUnit.SECONDS);
                        temp = batt();
                        if (temp >= 45f) {
                            status = "High temperature; stopping engine temporarily";
                            if (proc != null) proc.destroy();
                            break;
                        }
                        if (line == null) continue;
                        if (line.equals(eof)) {
                            outputEnded = true;
                            continue;
                        }
                        if (line.contains("Verification: SUCCESS")) processVerified = true;
                        if (line.contains("Verification FAILED")
                                || line.contains("Collision candidate failed verification")) {
                            processVerified = false;
                        }
                        line = line.replaceAll("\u001B\\[[0-9;]*m", "");
                        if (!line.trim().isEmpty()) status = "Engine: " + line.trim();
                        appendLog(logF, line);

                        Matcher m = OPS.matcher(line);
                        if (m.find()) {
                            long now = SystemClock.elapsedRealtime();
                            lastTelemetryAtMs = now;
                            long currentOps;
                            try {
                                currentOps = Long.parseLong(m.group(1));
                            } catch (NumberFormatException ex) {
                                currentOps = -1;
                            }
                            elapsedMs = Math.max(0, now - sessionStartMs);
                            if (currentOps >= 0 && previousOps >= 0
                                    && currentOps >= previousOps && now > previousOpsAt) {
                                double rate = (currentOps - previousOps) * 1000.0
                                        / (now - previousOpsAt);
                                speed = String.format(java.util.Locale.US, "%.2f M ops/s", rate);
                            } else {
                                speed = "—";
                            }
                            previousOps = currentOps;
                            previousOpsAt = now;
                            ops = m.group(1) + "M";
                            dps = m.group(2);
                        }
                    }
                    if (proc != null) {
                        exitCode = proc.waitFor();
                        appendLog(logF, "Solver process exit code: " + exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (running) status = "Engine interrupted";
                    break;
                } catch (Exception e) {
                    status = "Engine error: " + safeMessage(e);
                    appendLog(logF, "Engine exception: " + e.getClass().getSimpleName()
                            + ": " + safeMessage(e));
                } finally {
                    Process current = proc;
                    proc = null;
                    if (current != null && current.isAlive()) current.destroy();
                }

                if (foundF.exists() && foundF.length() > 0) {
                    try (BufferedReader resultReader = new BufferedReader(new FileReader(foundF))) {
                        found = resultReader.readLine();
                    } catch (Exception e) {
                        found = "found.txt വായിക്കാൻ കഴിഞ്ഞില്ല";
                        appendLog(logF, "Could not read found.txt: " + safeMessage(e));
                    }
                    if (processVerified) {
                        resultVerified = true;
                        status = "KEY FOUND — engine reported verification success";
                    } else {
                        resultVerified = false;
                        status = "UNVERIFIED RESULT — scan.log പരിശോധിക്കുക";
                    }
                    running = false;
                    break;
                }

                if (running && !Thread.currentThread().isInterrupted()) {
                    restarts++;
                    status = exitCode == Integer.MIN_VALUE
                            ? "Engine stopped unexpectedly; restart pending"
                            : "Engine exited (" + exitCode + "); restart pending";
                    appendLog(logF, status);
                    pause(30000);
                }
            }
        } finally {
            running = false;
            elapsedMs = sessionStartMs > 0
                    ? Math.max(elapsedMs, SystemClock.elapsedRealtime() - sessionStartMs) : elapsedMs;
            Process current = proc;
            proc = null;
            if (current != null && current.isAlive()) current.destroy();
            releaseWakeLock();
            try {
                stopForeground(true);
            } catch (RuntimeException ignored) {}
            stopSelf();
        }
    }

    private synchronized void releaseWakeLock() {
        if (wl != null) {
            try {
                if (wl.isHeld()) wl.release();
            } catch (RuntimeException ignored) {
                // Cleanup must continue even if Android has already released the lock.
            } finally {
                wl = null;
            }
        }
    }

    void stopAll() {
        running = false;
        status = "നിർത്തി";
        Process current = proc;
        if (current != null) current.destroy();
        Thread currentWorker = worker;
        if (currentWorker != null) currentWorker.interrupt();
        releaseWakeLock();
    }

    @Override public void onDestroy() {
        stopAll();
        super.onDestroy();
    }
}
