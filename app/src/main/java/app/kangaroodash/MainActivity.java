package app.kangaroodash;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    private final int BG = 0xFF0B1020;
    private final int CARD = 0xFF151E31;
    private final int TEXT = 0xFFF1F5F9;
    private final int MUTED = 0xFF9AA9C0;
    private final int TEAL = 0xFF42E8C5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusText, opsText, dpsText, storageText;
    private TextView restartText, tempText, resultText, elapsedText, speedText;
    private EditText pub, start, range, dp, kangaroos;
    private Button startButton, stopButton;
    private TextView liveBadge;

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(shape(CARD, 18));
        return c;
    }

    private void gap(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, lp(1, height));
    }

    private EditText field(LinearLayout parent, String label, String value) {
        TextView title = text(label, 12, MUTED, true);
        LinearLayout.LayoutParams titleLp = lp(-1, -2);
        titleLp.bottomMargin = dp(6);
        parent.addView(title, titleLp);

        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setTextSize(13);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setBackground(shape(0xFF0B1324, 10));
        parent.addView(e, lp(-1, 46));
        gap(parent, 12);
        return e;
    }

    private TextView metric(LinearLayout parent, String label, String value) {
        TextView heading = text(label, 11, MUTED, true);
        TextView number = text(value, 21, TEXT, true);
        parent.addView(heading);
        gap(parent, 7);
        parent.addView(number);
        return number;
    }

    private void addSection(LinearLayout root, String title, View content) {
        TextView heading = text(title, 15, TEXT, true);
        LinearLayout.LayoutParams hp = lp(-1, -2);
        hp.topMargin = dp(20);
        hp.bottomMargin = dp(10);
        root.addView(heading, hp);
        root.addView(content, lp(-1, -2));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                new String[]{"android.permission.POST_NOTIFICATIONS"}, 1
            );
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = column();
        root.setPadding(dp(20), dp(22), dp(20), dp(28));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = column();
        brand.addView(text("KANGAROO", 12, TEAL, true));
        TextView heading = text("Solver Dashboard", 25, TEXT, true);
        LinearLayout.LayoutParams headingLp = lp(-1, -2);
        headingLp.topMargin = dp(5);
        brand.addView(heading, headingLp);
        brand.addView(text("Native engine • Live monitor", 12, MUTED, false));

        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        liveBadge = text("● STOPPED", 11, MUTED, true);
        TextView badge = liveBadge;
        badge.setPadding(dp(10), dp(8), dp(10), dp(8));
        badge.setBackground(shape(0xFF12362F, 30));
        header.addView(badge);
        root.addView(header);

        LinearLayout statusCard = card();
        LinearLayout.LayoutParams scp = lp(-1, -2);
        scp.topMargin = dp(22);
        root.addView(statusCard, scp);

        statusCard.addView(text("ENGINE STATUS", 11, MUTED, true));
        statusText = text("Checking status…", 20, TEAL, true);
        LinearLayout.LayoutParams stp = lp(-1, -2);
        stp.topMargin = dp(8);
        statusCard.addView(statusText, stp);
        TextView statusHint = text(
            "Status updates automatically", 12, MUTED, false
        );
        LinearLayout.LayoutParams shp = lp(-1, -2);
        shp.topMargin = dp(5);
        statusCard.addView(statusHint, shp);

        addSection(root, "Performance", makeMetrics());

        LinearLayout liveDetails = card();
        elapsedText = text("Elapsed time: —", 13, TEXT, true);
        speedText = text("Speed: —", 13, TEXT, true);
        liveDetails.addView(elapsedText);
        gap(liveDetails, 12);
        liveDetails.addView(speedText);
        addSection(root, "Live scan", liveDetails);

        LinearLayout details = card();
        storageText = text("DP storage: —", 13, TEXT, false);
        restartText = text("Restarts: —", 13, TEXT, false);
        tempText = text("Battery temperature: —", 13, TEXT, false);
        details.addView(storageText);
        gap(details, 12);
        details.addView(restartText);
        gap(details, 12);
        details.addView(tempText);
        addSection(root, "System details", details);

        LinearLayout config = card();
        pub = field(config, "PUBLIC KEY (HEX)", 
            "031f6a332d3c5c4f2de2378c012f429cd109ba07d69690c6c701b6bb87860d6640");
        start = field(config, "START (HEX)", 
            "80000000000000000000000000000000000");
        range = field(config, "RANGE (BITS)", "139");
        dp = field(config, "DP BITS", "20");
        kangaroos = field(config, "KANGAROOS", "256");
        addSection(root, "Solver configuration", config);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        startButton = new Button(this);
        startButton.setText("▶  START");
        startButton.setTextColor(0xFF071A18);
        startButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        startButton.setBackground(shape(TEAL, 14));
        actions.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        View spacer = new View(this);
        actions.addView(spacer, lp(12, 1));

        stopButton = new Button(this);
        stopButton.setText("■  STOP");
        stopButton.setTextColor(TEXT);
        stopButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        stopButton.setBackground(shape(0xFF343044, 14));
        actions.addView(stopButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        LinearLayout.LayoutParams actionLp = lp(-1, -2);
        actionLp.topMargin = dp(18);
        root.addView(actions, actionLp);

        resultText = text("", 12, TEAL, false);
        resultText.setTextIsSelectable(true);
        LinearLayout.LayoutParams resultLp = lp(-1, -2);
        resultLp.topMargin = dp(16);
        root.addView(resultText, resultLp);

        TextView footer = text(
            "Kangaroo • Local device monitoring", 11, MUTED, false
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = lp(-1, -2);
        footerLp.topMargin = dp(28);
        root.addView(footer, footerLp);

        startButton.setOnClickListener(v -> startSolver());
        stopButton.setOnClickListener(v -> {
            // Stopping an existing service does not attempt a new background service start.
            stopService(new Intent(this, SolverService.class));
            statusText.setText("Stopping…");
        });

        setContentView(scroll);

        handler.post(new Runnable() {
            @Override public void run() {
                refresh();
                handler.postDelayed(this, 2000);
            }
        });
    }

    private View makeMetrics() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout opsCard = card();
        opsText = metric(opsCard, "OPERATIONS", "—");
        LinearLayout dpsCard = card();
        dpsText = metric(dpsCard, "DISTINGUISHED POINTS", "—");

        row.addView(opsCard, new LinearLayout.LayoutParams(0, -1, 1f));
        View gap = new View(this);
        row.addView(gap, lp(12, 1));
        row.addView(dpsCard, new LinearLayout.LayoutParams(0, -1, 1f));
        return row;
    }

    private void startSolver() {
        if (SolverService.running) {
            statusText.setText("Engine is already running");
            return;
        }
        String pubValue = pub.getText().toString().trim();
        String startValue = start.getText().toString().trim();
        String rangeValue = range.getText().toString().trim();
        String dpValue = dp.getText().toString().trim();
        String kangarooValue = kangaroos.getText().toString().trim();

        if (pubValue.isEmpty() || startValue.isEmpty() || rangeValue.isEmpty()
                || dpValue.isEmpty() || kangarooValue.isEmpty()) {
            statusText.setText("Please fill in every field");
            return;
        }
        if (!isPublicKeyHex(pubValue)) {
            statusText.setText("Invalid public-key encoding: compressed keys must start 02/03; uncompressed keys must start 04");
            return;
        }
        if (!isHex(startValue)) {
            statusText.setText("Invalid start value: use hexadecimal characters only");
            return;
        }
        try {
            int rangeBits = Integer.parseInt(rangeValue);
            int dpBits = Integer.parseInt(dpValue);
            int count = Integer.parseInt(kangarooValue);
            if (rangeBits < 1 || rangeBits > 256) {
                statusText.setText("Range bits must be between 1 and 256");
                return;
            }
            if (dpBits < 1 || dpBits > 256) {
                statusText.setText("DP bits must be between 1 and 256");
                return;
            }
            if (count < 1 || count > 4096) {
                statusText.setText("Kangaroos must be between 1 and 4096");
                return;
            }
        } catch (NumberFormatException e) {
            statusText.setText("Range, DP bits and kangaroos must be whole numbers");
            return;
        }

        Intent i = new Intent(this, SolverService.class);
        i.putExtra("pub", pubValue);
        i.putExtra("start", startValue);
        i.putExtra("range", rangeValue);
        i.putExtra("dp", dpValue);
        i.putExtra("k", kangarooValue);

        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            statusText.setText("Starting engine…");
        } catch (Exception e) {
            statusText.setText("Could not start: " + e.getMessage());
        }
    }

    static boolean isPublicKeyHex(String value) {
        if (!isHex(value)) return false;
        if (value.length() == 66) {
            String prefix = value.substring(0, 2);
            return "02".equalsIgnoreCase(prefix) || "03".equalsIgnoreCase(prefix);
        }
        return value.length() == 130 && "04".equalsIgnoreCase(value.substring(0, 2));
    }

    static boolean isHex(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) return false;
        }
        return true;
    }

    private void refresh() {
        statusText.setText(SolverService.status);
        boolean active = SolverService.running;
        if (startButton != null) startButton.setEnabled(!active);
        if (stopButton != null) stopButton.setEnabled(active);
        liveBadge.setText(active ? "● RUNNING" : "● STOPPED");
        liveBadge.setTextColor(active ? TEAL : MUTED);
        liveBadge.setBackground(shape(active ? 0xFF12362F : 0xFF252C3A, 30));
        opsText.setText(SolverService.ops);
        dpsText.setText(SolverService.dps);

        File dpFile = new File(getFilesDir(), "dp.json");
        long bytes = dpFile.exists() ? dpFile.length() : 0;
        String size = bytes < 1024
            ? bytes + " B"
            : String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        String dpState = !dpFile.exists() ? "missing"
            : bytes == 0 ? "present but empty" : "present; native save/load unverified";
        long modifiedMs = dpFile.exists() ? dpFile.lastModified() : 0;
        String modified = modifiedMs > 0
            ? String.format(java.util.Locale.US, "%.0f min ago",
                Math.max(0, (System.currentTimeMillis() - modifiedMs) / 60000.0))
            : "unknown";

        storageText.setText("DP file: " + dpState + " • " + size + " • modified " + modified);
        restartText.setText("Restarts: " + SolverService.restarts);
        long elapsed = SolverService.elapsedMs;
        if (SolverService.running && SolverService.sessionStartMs > 0) {
            elapsed = android.os.SystemClock.elapsedRealtime()
                    - SolverService.sessionStartMs;
        }
        long seconds = Math.max(0, elapsed / 1000);
        elapsedText.setText(String.format(java.util.Locale.US,
                "Elapsed time: %02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60));
        long telemetryAge = SolverService.lastTelemetryAtMs > 0
                ? android.os.SystemClock.elapsedRealtime() - SolverService.lastTelemetryAtMs
                : Long.MAX_VALUE;
        String liveSpeed = SolverService.running && telemetryAge <= 5000
                ? SolverService.speed : "—";
        speedText.setText("Speed: " + liveSpeed);

        tempText.setText(String.format(
            java.util.Locale.US, "Battery temperature: %.1f°C", SolverService.temp
        ));

        if (SolverService.found == null || SolverService.found.isEmpty()) {
            resultText.setText("");
        } else {
            resultText.setText("RESULT\n" + SolverService.found);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
