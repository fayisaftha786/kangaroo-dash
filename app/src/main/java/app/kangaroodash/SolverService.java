package app.kangaroodash;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
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
    java.lang.Process proc;
    Thread worker;
    PowerManager.WakeLock wl;
    static final Pattern OPS = Pattern.compile("Ops: (\\d+)M \\| DPs: (\\d+)");

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int f, int id) {
        if (in != null && "stop".equals(in.getAction())) { stopAll(); stopSelf(); return START_NOT_STICKY; }
        if (in == null || running) return START_NOT_STICKY;
        NotificationChannel ch = new NotificationChannel("k", "Kangaroo", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "k")
            .setContentTitle("Kangaroo ഓടുന്നു").setSmallIcon(android.R.drawable.ic_media_play).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
        wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "k:run");
        wl.acquire();
        final String[] a = { in.getStringExtra("pub"), in.getStringExtra("start"), in.getStringExtra("range"),
                             in.getStringExtra("dp"), in.getStringExtra("k") };
        running = true; restarts = 0; found = ""; resultVerified = false; lastTelemetryAtMs = 0; sessionStartMs = SystemClock.elapsedRealtime(); elapsedMs = 0; speed = "—";
        worker = new Thread(() -> loop(a));
        worker.start();
        return START_NOT_STICKY;
    }

    float batt() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
    }

    void pause(long ms) { try { Thread.sleep(ms); } catch (Exception e) {} }

    void loop(String[] a) {
        String bin = getApplicationInfo().nativeLibraryDir + "/libkangaroo.so";
        File dir = getFilesDir();
        File foundF = new File(dir, "found.txt");
        long previousOps = -1, previousOpsAt = 0;
        File logF = new File(dir, "scan.log");
        try { if (foundF.exists()) foundF.delete(); } catch (Exception ignored) {}
        while (running) {
            boolean processVerified = false;
            temp = batt();
            if (temp >= 45f) { status = "തണുക്കുന്നു " + temp + "°C"; pause(30000); continue; }
            try {
                ProcessBuilder pb = new ProcessBuilder(bin, "--pubkey", a[0], "--start", a[1], "--range", a[2],
                    "--dp-bits", a[3], "--output", foundF.getAbsolutePath(), "--kangaroos", a[4]);
                pb.directory(dir);
                pb.environment().put("KANGAROO_DP_FILE", new File(dir, "dp.json").getAbsolutePath());
                pb.redirectErrorStream(true);
                status = "ഓടുന്നു";
                proc = pb.start();
                BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                try (FileWriter log = new FileWriter(logF, true)) {
                    log.write("\n--- Solver process started: " + new java.util.Date() + " ---\n");
                }
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("Verification: SUCCESS")) processVerified = true;
                    if (line.contains("Verification FAILED") || line.contains("Collision candidate failed verification")) processVerified = false;
                    line = line.replaceAll("\u001B\\[[0-9;]*m", "");
                    if (!line.trim().isEmpty()) status = "Engine: " + line.trim();
                    try (FileWriter log = new FileWriter(logF, true)) { log.write(line + "\n"); } catch (Exception ignored) {}
                    Matcher m = OPS.matcher(line);
                    if (m.find()) {
                        long now = SystemClock.elapsedRealtime();
                        lastTelemetryAtMs = now;
                        long currentOps;
                        try { currentOps = Long.parseLong(m.group(1)); }
                        catch (NumberFormatException ex) { currentOps = -1; }
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
                        ops = m.group(1) + "M"; dps = m.group(2);
                        temp = batt();
                        if (temp >= 45f) { proc.destroy(); break; }
                    }
                }
                proc.waitFor();
            } catch (Exception e) { status = "പിശക്: " + e.getMessage(); pause(10000); }
            if (foundF.exists() && foundF.length() > 0) {
                try (BufferedReader b = new BufferedReader(new FileReader(foundF))) { found = b.readLine(); }
                catch (Exception e) { found = "found.txt കാണുക"; }
                if (processVerified) {
                    resultVerified = true;
                    status = "KEY FOUND — verification success";
                } else {
                    resultVerified = false;
                    status = "UNVERIFIED RESULT — scan.log പരിശോധിക്കുക";
                }
                running = false;
                break;
            }
            if (running) { restarts++; status = "restart-ന് കാത്തിരിക്കുന്നു"; pause(30000); }
        }
        stopForeground(true); stopSelf();
    }

    void stopAll() {
        running = false; status = "നിർത്തി";
        if (proc != null) proc.destroy();
        if (worker != null) worker.interrupt();
        if (wl != null && wl.isHeld()) wl.release();
    }

    @Override public void onDestroy() { stopAll(); }
}
