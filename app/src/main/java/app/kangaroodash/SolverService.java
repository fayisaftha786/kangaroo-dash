package app.kangaroodash;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

public class SolverService extends Service {
    static final int MAX_CONSECUTIVE_ENGINE_FAILURES = 3;
    static volatile boolean running = false;
    private static volatile boolean workerBusy = false;
    private static volatile Thread activeWorker;
    // Guard the cleanup-failure latch and its process reference as one state.
    // A missing reference is not proof that the OS has reaped a stuck process.
    private static final Object PROCESS_STATE_LOCK = new Object();
    private static volatile boolean nativeProcessCleanupFailed = false;
    private static volatile java.lang.Process stuckNativeProcess;

    static boolean isBusy() {
        Thread current = activeWorker;
        synchronized (PROCESS_STATE_LOCK) {
            if (nativeProcessCleanupFailed) {
                java.lang.Process stuck = stuckNativeProcess;
                // Only clear the latch when the exact process we tracked is confirmed dead.
                // If the reference is unexpectedly missing, fail closed rather than guessing.
                if (stuck != null && !stuck.isAlive()) {
                    stuckNativeProcess = null;
                    nativeProcessCleanupFailed = false;
                }
            }
            return running || workerBusy || nativeProcessCleanupFailed
                    || (current != null && current.isAlive());
        }
    }

    private static void markNativeProcessCleanupFailed(java.lang.Process process) {
        synchronized (PROCESS_STATE_LOCK) {
            // Publish the process reference before setting the latch, under the same lock.
            stuckNativeProcess = process;
            nativeProcessCleanupFailed = true;
        }
    }
    static volatile String status = "നിർത്തി", ops = "-", dps = "-", found = "";
    static volatile int restarts = 0;
    static volatile long sessionStartMs = 0, elapsedMs = 0;
    static volatile String speed = "—";
    static volatile boolean resultVerified = false;
    static volatile long lastTelemetryAtMs = 0;
    static volatile float temp = Float.NaN;
    volatile java.lang.Process proc;
    volatile Thread worker;
    private PowerManager.WakeLock wl;


    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int f, int id) {
        if (in != null && "stop".equals(in.getAction())) {
            stopAll();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (in == null) return START_NOT_STICKY;

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
            // A startForegroundService request must promote the service before we return.
            // Do this before duplicate-start checks so every start request is acknowledged safely.
            // Use shared worker state, not this Service instance's worker field:
            // Android may recreate the Service while the previous instance's worker
            // is still cleaning up. Never start a second native engine in that window.
            Thread previousWorker = activeWorker;
            if (isBusy() || (previousWorker != null && previousWorker.isAlive())) {
                if (!running) {
                    status = "Previous session is still shutting down; try starting again shortly";
                }
                // The existing worker still owns process/wake-lock/foreground cleanup.
                // Keep the service foreground until that worker's finally block completes.
                return START_NOT_STICKY;
            }
            wl = ((PowerManager) getSystemService(POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "k:run");
            wl.acquire();
        } catch (RuntimeException e) {
            // A duplicate start request must never tear down resources owned by an
            // existing worker if foreground promotion or setup for that request fails.
            if (isBusy()) {
                status = "Additional start request failed; existing worker retained: "
                        + safeMessage(e);
                return START_NOT_STICKY;
            }
            status = "Service start failed: " + safeMessage(e);
            releaseWakeLock();
            try {
                stopForeground(true);
            } catch (RuntimeException ignored) {
                // Preserve the original startup failure and still request service shutdown.
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        final String[] a = { in.getStringExtra("pub"), in.getStringExtra("start"),
                in.getStringExtra("range"), in.getStringExtra("dp"), in.getStringExtra("k") };
        running = true;
        workerBusy = true;
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
        activeWorker = worker;
        try {
            worker.start();
        } catch (RuntimeException startError) {
            // Thread.start() can fail before loop() takes ownership of cleanup.
            // Restore every state item here so the dashboard can retry safely.
            running = false;
            workerBusy = false;
            activeWorker = null;
            worker = null;
            status = "Worker start failed: " + safeMessage(startError);
            releaseWakeLock();
            try {
                stopForeground(true);
            } catch (RuntimeException ignored) {
                // Continue service cleanup even if foreground teardown fails.
            }
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName() : message;
    }

    float batt() {
        try {
            Intent battery = registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return Float.NaN;
            int tenthsCelsius = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            return tenthsCelsius < 0 ? Float.NaN : tenthsCelsius / 10f;
        } catch (RuntimeException e) {
            // OEM/system-service failures must not crash the worker. Unknown is not 0°C.
            return Float.NaN;
        }
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
        long nextTemperatureCheckAt = 0;

        try {
            File engine = new File(bin);
            if (!engine.isFile() || !engine.canExecute()) {
                status = "Native engine missing or not executable: " + engine.getName();
                appendLog(logF, status + " (path=" + engine.getAbsolutePath() + ")");
                return;
            }
            if (foundF.exists() && !foundF.delete()) {
                status = "Cannot clear stale result file; engine not started";
                appendLog(logF, status);
                running = false;
                return;
            }
            while (running && !Thread.currentThread().isInterrupted()) {
                boolean processVerified = false;
                int exitCode = Integer.MIN_VALUE;
                boolean thermalStopRequested = false;
                Thread outputReader = null;
                long thermalCheckNow = SystemClock.elapsedRealtime();
                if (SolverPerformancePolicy.isDue(thermalCheckNow, nextTemperatureCheckAt)) {
                    temp = batt();
                    nextTemperatureCheckAt = SolverPerformancePolicy.nextDueAt(
                            thermalCheckNow,
                            SolverPerformancePolicy.BATTERY_TEMPERATURE_SAMPLE_INTERVAL_MS);
                }
                if (temp >= 45f) {
                    status = "തണുക്കുന്നു " + temp + "°C";
                    pause(30000);
                    nextTemperatureCheckAt = 0;
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
                    appendLog(logF, "DP file before start: " + fileState(new File(dir, "dp.json")));
                    if (!running || Thread.currentThread().isInterrupted()) break;
                    status = "ഓടുന്നു";
                    proc = pb.start();
                    if (!running || Thread.currentThread().isInterrupted()) {
                        terminateProcess(proc);
                        break;
                    }
                    appendLog(logF, "--- Solver process started: " + new java.util.Date() + " ---");
                    long lastProgressAt = SystemClock.elapsedRealtime();
                    long lastTelemetryLogAt = SolverPerformancePolicy.NO_PREVIOUS_TELEMETRY_LOG;
                    String lastProgressDps = dps;
                    long lastProgressOps = previousOps;
                    boolean staleWarningLogged = false;

                    final Object eof = new Object();
                    final BlockingQueue<Object> outputLines = new LinkedBlockingQueue<>(1024);
                    final java.lang.Process currentProcess = proc;
                    outputReader = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                            String outputLine;
                            while ((outputLine = reader.readLine()) != null) {
                                outputLines.put(outputLine);
                            }
                        } catch (Exception readerError) {
                            try {
                                outputLines.put("Engine output reader error: " + safeMessage(readerError));
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
                        Object output = outputLines.poll(5, TimeUnit.SECONDS);
                        thermalCheckNow = SystemClock.elapsedRealtime();
                        if (SolverPerformancePolicy.isDue(thermalCheckNow, nextTemperatureCheckAt)) {
                            temp = batt();
                            nextTemperatureCheckAt = SolverPerformancePolicy.nextDueAt(
                                    thermalCheckNow,
                                    SolverPerformancePolicy.BATTERY_TEMPERATURE_SAMPLE_INTERVAL_MS);
                        }
                        if (temp >= 45f) {
                            status = "High temperature; stopping engine temporarily";
                            thermalStopRequested = true;
                            terminateProcess(proc);
                            break;
                        }
                        long progressCheckAt = SystemClock.elapsedRealtime();
                        if (progressCheckAt - lastProgressAt >= 30000 && !staleWarningLogged) {
                            status = "No telemetry/progress for 30s — engine may be stalled; not auto-stopped";
                            appendLog(logF, status);
                            staleWarningLogged = true;
                        }
                        if (output == null) continue;
                        if (output == eof) {
                            outputEnded = true;
                            continue;
                        }
                        String line = (String) output;
                        if (line.contains("Verification: SUCCESS")) processVerified = true;
                        if (line.contains("Verification FAILED")
                                || line.contains("Collision candidate failed verification")) {
                            processVerified = false;
                        }
                        line = line.replaceAll("\u001B\\[[0-9;]*m", "");
                        if (!line.trim().isEmpty() && !staleWarningLogged) status = "Engine: " + line.trim();

                        Matcher m = SolverTelemetry.find(line);
                        if (m == null) {
                            // Preserve all non-telemetry diagnostics. High-frequency progress
                            // records are sampled below to avoid reopening/writing the log per line.
                            appendLog(logF, line);
                        } else {
                            long now = SystemClock.elapsedRealtime();
                            if (SolverPerformancePolicy.shouldLogTelemetry(now, lastTelemetryLogAt)) {
                                appendLog(logF, line);
                                lastTelemetryLogAt = now;
                            }
                            lastTelemetryAtMs = now;
                            long currentOps;
                            try {
                                currentOps = Long.parseLong(m.group(1));
                            } catch (NumberFormatException ex) {
                                currentOps = -1;
                            }
                            elapsedMs = Math.max(0, now - sessionStartMs);
                            if (currentOps >= 0 && previousOps >= 0
                                    && currentOps > previousOps && now > previousOpsAt) {
                                double rate = (currentOps - previousOps) * 1000.0
                                        / (now - previousOpsAt);
                                speed = String.format(java.util.Locale.US, "%.2f M ops/s", rate);
                            } else {
                                speed = "—";
                            }
                            String currentDpsText = m.group(2);
                            if (currentOps != lastProgressOps || !currentDpsText.equals(lastProgressDps)) {
                                lastProgressAt = now;
                                lastProgressOps = currentOps;
                                lastProgressDps = currentDpsText;
                                staleWarningLogged = false;
                            }
                            previousOps = currentOps;
                            previousOpsAt = now;
                            ops = m.group(1) + "M";
                            dps = currentDpsText;
                            if (!staleWarningLogged) status = "Telemetry active: Ops " + ops + " | DPs " + dps;
                        }
                    }
                    if (proc != null) {
                        java.lang.Process completedProcess = proc;
                        if (!completedProcess.waitFor(500, TimeUnit.MILLISECONDS)) {
                            appendLog(logF, "Native process did not exit within 500ms; forcing termination");
                            terminateProcess(completedProcess);
                        }
                        if (completedProcess.isAlive()) {
                            // Keep the safety latch set even after this worker exits:
                            // isBusy() must prevent a new engine from overlapping a stuck process.
                            markNativeProcessCleanupFailed(completedProcess);
                            status = "Native process is still alive after forced termination; automatic restart disabled";
                            appendLog(logF, status);
                            running = false;
                        } else {
                            exitCode = completedProcess.exitValue();
                            appendLog(logF, "Solver process exit code: " + exitCode);
                        }
                        appendLog(logF, "DP file after exit: " + fileState(new File(dir, "dp.json")));
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
                    java.lang.Process current = proc;
                    proc = null;
                    if (terminateProcess(current)) {
                        markNativeProcessCleanupFailed(current);
                        status = "Native process remains alive after loop cleanup; automatic restart disabled";
                        appendLog(logF, status);
                    }
                    // Closing the process pipe helps unblock a reader stuck in readLine();
                    // interrupt alone does not reliably cancel blocking stream I/O.
                    closeProcessOutputStream(current);
                    if (outputReader != null) {
                        outputReader.interrupt();
                        try {
                            outputReader.join(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        if (outputReader.isAlive()) {
                            appendLog(logF, "Output reader did not exit within 500ms after stream close");
                        }
                    }
                }

                if (foundF.exists() && foundF.length() > 0) {
                    try (BufferedReader resultReader = new BufferedReader(new FileReader(foundF))) {
                        found = resultReader.readLine();
                    } catch (Exception e) {
                        found = "found.txt വായിക്കാൻ കഴിഞ്ഞില്ല";
                        appendLog(logF, "Could not read found.txt: " + safeMessage(e));
                    }
                    resultVerified = false;
                    if (processVerified) {
                        status = "ENGINE REPORTED SUCCESS — independent verification unavailable";
                    } else {
                        status = "UNVERIFIED RESULT — scan.log പരിശോധിക്കുക";
                    }
                    running = false;
                    break;
                }

                if (running && !Thread.currentThread().isInterrupted()) {
                    if (thermalStopRequested) {
                        appendLog(logF, "Engine paused for high temperature; retrying after cooldown");
                        pause(30000);
                        continue;
                    }
                    restarts++;
                    if (!SolverPerformancePolicy.shouldRetryAfterExit(
                            restarts, MAX_CONSECUTIVE_ENGINE_FAILURES)) {
                        running = false;
                        status = "Engine failed " + MAX_CONSECUTIVE_ENGINE_FAILURES
                                + " times; automatic restart stopped";
                        appendLog(logF, status);
                        break;
                    }
                    status = exitCode == Integer.MIN_VALUE
                            ? "Engine stopped unexpectedly; restart pending (" + restarts + "/"
                                    + MAX_CONSECUTIVE_ENGINE_FAILURES + ")"
                            : "Engine exited (" + exitCode + "); restart pending (" + restarts + "/"
                                    + MAX_CONSECUTIVE_ENGINE_FAILURES + ")";
                    appendLog(logF, status);
                    pause(30000);
                }
            }
        } finally {
            running = false;
            elapsedMs = sessionStartMs > 0
                    ? Math.max(elapsedMs, SystemClock.elapsedRealtime() - sessionStartMs) : elapsedMs;
            java.lang.Process current = proc;
            proc = null;
            if (terminateProcess(current)) {
                markNativeProcessCleanupFailed(current);
                status = "Native process remains alive after worker cleanup; automatic restart disabled";
                appendLog(logF, status);
            }
            releaseWakeLock();
            try {
                stopForeground(true);
            } catch (RuntimeException ignored) {}
            workerBusy = false;
            stopSelf();
        }
    }


    private static String fileState(File file) {
        if (!file.exists()) return "missing";
        return "exists, bytes=" + file.length() + ", modifiedMs=" + file.lastModified();
    }

    // Returns true only when the OS still reports the process alive after all bounded attempts.
    // Package-private so unit tests can exercise the shutdown contract with fake processes.
    // Best-effort pipe close to unblock outputReader when it is blocked in readLine().
    // This is intentionally separate from native-process termination state.
    static void closeProcessOutputStream(java.lang.Process process) {
        if (process == null) return;
        try {
            process.getInputStream().close();
        } catch (IOException | RuntimeException ignored) {
            // Cleanup must continue even if the process stream is already closed or broken.
        }
    }

    static boolean terminateProcess(java.lang.Process process) {
        if (process == null || !process.isAlive()) return false;

        // Preserve cancellation semantics without letting an already-set interrupt
        // skip the bounded wait needed to reap the native process.
        boolean interrupted = Thread.interrupted();
        try {
            process.destroy();
            try {
                if (!process.waitFor(500, TimeUnit.MILLISECONDS) && process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(1500, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                interrupted = true;
                try {
                    process.destroyForcibly();
                    // InterruptedException clears the flag, so still make a bounded
                    // attempt to wait for the forcibly terminated process.
                    process.waitFor(1500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException secondInterrupt) {
                    interrupted = true;
                } catch (RuntimeException ignored) {
                    // Preserve interruption and continue the worker's remaining cleanup.
                }
            }
        } catch (RuntimeException ignored) {
            try {
                process.destroyForcibly();
                try {
                    process.waitFor(1500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            } catch (RuntimeException ignoredAgain) {}
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        return process.isAlive();
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

        // onDestroy/onStartCommand run on the main thread. Do not wait here for
        // native process termination; the worker's finally block performs bounded cleanup.
        java.lang.Process current = proc;
        if (current != null && current.isAlive()) {
            try {
                current.destroy();
            } catch (RuntimeException ignored) {
                // The worker will still attempt forced termination during cleanup.
            }
        }

        // The worker may belong to a previous Service instance after Android recreates it.
        // Use the process-wide worker reference so Stop still reaches that active session.
        Thread currentWorker = activeWorker;
        if (currentWorker != null && currentWorker.isAlive()) {
            currentWorker.interrupt();
            // Keep the CPU awake while the worker reaps the native process and
            // closes its output reader. The worker's finally block releases it.
            return;
        }
        releaseWakeLock();
    }

    @Override public void onDestroy() {
        stopAll();
        super.onDestroy();
    }
}
