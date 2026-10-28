package app.kangaroodash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private final int BG = 0xFF080D1A;
    private final int CARD = 0xFF111A2E;
    private final int TEXT = 0xFFF7FAFF;
    private final int MUTED = 0xFFA2B0CA;
    private final int ACCENT = 0xFF8B7CFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusText, opsText, dpsText, storageText;
    private TextView restartText, tempText, resultText, elapsedText, speedText;
    private EditText pub, start, range, dp, kangaroos;
    private Button startButton, stopButton;
    private TextView liveBadge;
    private Spinner puzzleSpinner;
    private TextView puzzleHint;
    private final List<JSONObject> puzzleEntries = new ArrayList<>();
    // Fail closed until a catalog entry passes the execution-safety checks.
    private boolean selectedPuzzleBlocked = true;
    private String loadedPuzzlePublicKey, loadedPuzzleStart, loadedPuzzleRange;
    private static final int REQUEST_EXPORT_FILE = 7101;
    private String pendingExportName;

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
        e.setBackground(shape(0xFF090F20, 10));
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
        brand.addView(text("KANGAROO  /  CONTROL CENTER", 12, ACCENT, true));
        TextView heading = text("Kangaroo Dashboard", 25, TEXT, true);
        LinearLayout.LayoutParams headingLp = lp(-1, -2);
        headingLp.topMargin = dp(5);
        brand.addView(heading, headingLp);
        brand.addView(text("NATIVE ENGINE  •  LIVE MONITOR", 12, MUTED, false));

        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        liveBadge = text("● STOPPED", 11, MUTED, true);
        TextView badge = liveBadge;
        badge.setPadding(dp(10), dp(8), dp(10), dp(8));
        badge.setBackground(shape(0xFF25204B, 30));
        header.addView(badge);
        root.addView(header);

        LinearLayout statusCard = card();
        LinearLayout.LayoutParams scp = lp(-1, -2);
        scp.topMargin = dp(22);
        root.addView(statusCard, scp);

        statusCard.addView(text("ENGINE STATUS", 11, MUTED, true));
        statusText = text("Checking status…", 20, ACCENT, true);
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
        pub = field(config, "PUBLIC KEY (HEX)", "");
        pub.setHint("Enter the intended public key in hexadecimal");
        // The key is longer than the visible field. Start at the prefix so the
        // dashboard does not misleadingly show only the trailing characters.
        pub.setHorizontallyScrolling(true);
        pub.setSelection(0);
        start = field(config, "START (HEX)", 
            "80000000000000000000000000000000000");
        range = field(config, "RANGE (BITS)", "139");
        dp = field(config, "DP BITS", "20");
        kangaroos = field(config, "KANGAROOS", "256");
        addSection(root, "Solver configuration", config);

        LinearLayout puzzlePicker = card();
        puzzleHint = text("Loading catalog…", 12, MUTED, false);
        puzzlePicker.addView(puzzleHint, lp(-1, -2));
        gap(puzzlePicker, 10);
        puzzleSpinner = new Spinner(this);
        puzzlePicker.addView(puzzleSpinner, lp(-1, 48));
        Button loadPuzzleButton = new Button(this);
        loadPuzzleButton.setText("LOAD SELECTED PUZZLE");
        loadPuzzleButton.setTextColor(TEXT);
        loadPuzzleButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        loadPuzzleButton.setBackground(shape(0xFF25204B, 14));
        LinearLayout.LayoutParams loadPuzzleLp = lp(-1, 48);
        loadPuzzleLp.topMargin = dp(10);
        puzzlePicker.addView(loadPuzzleButton, loadPuzzleLp);
        loadPuzzleButton.setOnClickListener(v -> applySelectedPuzzle());
        addSection(root, "Puzzle catalog (160 records)", puzzlePicker);
        loadPuzzleCatalog();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        startButton = new Button(this);
        startButton.setText("▶  START");
        startButton.setTextColor(0xFF100C2B);
        startButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        startButton.setBackground(shape(ACCENT, 14));
        actions.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        View spacer = new View(this);
        actions.addView(spacer, lp(12, 1));

        stopButton = new Button(this);
        stopButton.setText("■  STOP");
        stopButton.setTextColor(TEXT);
        stopButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        stopButton.setBackground(shape(0xFF34264A, 14));
        actions.addView(stopButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        LinearLayout.LayoutParams actionLp = lp(-1, -2);
        actionLp.topMargin = dp(18);
        root.addView(actions, actionLp);

        Button diagnosticsButton = new Button(this);
        diagnosticsButton.setText("VIEW / COPY DIAGNOSTICS");
        diagnosticsButton.setTextColor(TEXT);
        diagnosticsButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        diagnosticsButton.setBackground(shape(0xFF1C2942, 14));
        LinearLayout.LayoutParams diagnosticsLp = lp(-1, 50);
        diagnosticsLp.topMargin = dp(10);
        root.addView(diagnosticsButton, diagnosticsLp);
        diagnosticsButton.setOnClickListener(v -> showDiagnostics());

        Button exportButton = new Button(this);
        exportButton.setText("EXPORT dp.json / scan.log");
        exportButton.setTextColor(ACCENT);
        exportButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        exportButton.setBackground(shape(0xFF25204B, 14));
        LinearLayout.LayoutParams exportLp = lp(-1, 50);
        exportLp.topMargin = dp(10);
        root.addView(exportButton, exportLp);
        exportButton.setOnClickListener(v -> chooseExportFile());

        Button healthButton = new Button(this);
        healthButton.setText("RUN APP HEALTH CHECK");
        healthButton.setTextColor(ACCENT);
        healthButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        healthButton.setBackground(shape(0xFF25204B, 14));
        LinearLayout.LayoutParams healthLp = lp(-1, 50);
        healthLp.topMargin = dp(10);
        root.addView(healthButton, healthLp);
        healthButton.setOnClickListener(v -> showHealthCenter());

        Button puzzleDataButton = new Button(this);
        puzzleDataButton.setText("CHECK PUZZLE DATA");
        puzzleDataButton.setTextColor(TEXT);
        puzzleDataButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        puzzleDataButton.setBackground(shape(0xFF1C2942, 14));
        LinearLayout.LayoutParams puzzleDataLp = lp(-1, 50);
        puzzleDataLp.topMargin = dp(10);
        root.addView(puzzleDataButton, puzzleDataLp);
        puzzleDataButton.setOnClickListener(v -> showPuzzleDataValidation());

        resultText = text("", 12, ACCENT, false);
        resultText.setTextIsSelectable(true);
        LinearLayout.LayoutParams resultLp = lp(-1, -2);
        resultLp.topMargin = dp(16);
        root.addView(resultText, resultLp);

        TextView footer = text(
            "KANGAROO  •  DEVICE CONTROL & MONITORING", 11, MUTED, false
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

    private void loadPuzzleCatalog() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("puzzles.json"), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            JSONArray records = new JSONArray(json.toString());
            List<String> labels = new ArrayList<>();
            puzzleEntries.clear();
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record == null) continue;
                int id = record.optInt("id", -1);
                int bits = record.optInt("bits", -1);
                if (id < 1 || bits < 1) continue;
                puzzleEntries.add(record);
                String state = record.optString("status", "unknown");
                boolean hasKey = record.opt("public_key") instanceof String
                        && isPublicKeyHex(record.optString("public_key", ""));
                int searchBits = record.optInt("search_bits", -1);
                boolean supportedRange = searchBits >= 1 && searchBits <= 256;
                String availability = !hasKey ? " • no usable public key"
                        : !supportedRange ? " • metadata only (no supported range)" : "";
                labels.add("#" + id + " • " + bits + "-bit • " + state + availability);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            puzzleSpinner.setAdapter(adapter);
            puzzleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(
                        AdapterView<?> parent, View view, int position, long id) {
                    // Selecting a catalog entry loads its fields; it never starts the engine.
                    applySelectedPuzzle();
                }

                @Override public void onNothingSelected(AdapterView<?> parent) {
                    puzzleHint.setText("Choose a catalog record.");
                }
            });
            puzzleHint.setText(puzzleEntries.size() + " catalog records loaded. Selecting a record loads its fields; the engine starts only when START is pressed.");
        } catch (Exception e) {
            puzzleEntries.clear();
            puzzleHint.setText("Catalog unavailable: " + e.getClass().getSimpleName());
            statusText.setText("Puzzle catalog could not be loaded");
        }
    }

    private void applySelectedPuzzle() {
        // Any failed/unsupported selection remains blocked until a valid entry loads.
        selectedPuzzleBlocked = true;
        loadedPuzzlePublicKey = loadedPuzzleStart = loadedPuzzleRange = null;
        int position = puzzleSpinner == null ? -1 : puzzleSpinner.getSelectedItemPosition();
        if (position < 0 || position >= puzzleEntries.size()) {
            puzzleHint.setText("Choose a valid catalog record first.");
            return;
        }
        JSONObject record = puzzleEntries.get(position);
        String key = record.optString("public_key", "");
        String startHex = record.optString("range_start_hex", "");
        int rangeBits = record.optInt("search_bits", -1);
        int id = record.optInt("id", -1);
        String state = record.optString("status", "unknown");

        // Clear previously loaded catalog values so an invalid selection cannot
        // leave a different puzzle's key/range visible as if it belonged here.
        pub.setText("");
        start.setText("");
        range.setText("");

        if (PuzzleCatalogPolicy.isBlocked(state,
                record.opt("private_key_already_public"),
                record.opt("unsafe_for_wallets"))) {
            puzzleHint.setText("Puzzle #" + id + " is solved or marked unsafe; START is blocked for this record.");
            return;
        }
        if (!isPublicKeyHex(key)) {
            puzzleHint.setText("Puzzle #" + id + " has no usable public key; nothing was changed.");
            return;
        }
        if (!isValidStartValue(startHex)) {
            puzzleHint.setText("Puzzle #" + id + " has an invalid start value; nothing was changed.");
            return;
        }
        if (!PuzzleCatalogPolicy.hasSupportedSearchBits(rangeBits)) {
            puzzleHint.setText("Puzzle #" + id + " has no supported positive search range; nothing was changed.");
            return;
        }

        pub.setText(key);
        start.setText(startHex);
        range.setText(Integer.toString(rangeBits));
        loadedPuzzlePublicKey = key;
        loadedPuzzleStart = startHex;
        loadedPuzzleRange = Integer.toString(rangeBits);
        selectedPuzzleBlocked = false;
        puzzleHint.setText("Loaded Puzzle #" + id + " (" + state + "). This entry passed local checks only; source and native-engine behavior are not independently verified.");
    }

    private void startSolver() {
        if (selectedPuzzleBlocked || !PuzzleCatalogPolicy.matchesLoadedValues(
                loadedPuzzlePublicKey, loadedPuzzleStart, loadedPuzzleRange,
                pub.getText().toString(), start.getText().toString(), range.getText().toString())) {
            statusText.setText("START blocked: reselect an eligible catalog entry. Changing its key/start/range breaks the catalog match.");
            return;
        }
        if (SolverService.isBusy()) {
            statusText.setText(SolverService.running
                    ? "Engine is already running"
                    : "Previous session is still shutting down; try again shortly");
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
            statusText.setText("Invalid secp256k1 public key: check encoding and curve point");
            return;
        }
        if (!isValidStartValue(startValue)) {
            statusText.setText("Invalid start value: use 1–64 hexadecimal digits (up to 256 bits)");
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

    private static final java.math.BigInteger SECP256K1_P =
            new java.math.BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
    private static final java.math.BigInteger SECP256K1_SQRT_EXP =
            SECP256K1_P.add(java.math.BigInteger.ONE).shiftRight(2);

    static boolean isPublicKeyHex(String value) {
        if (!isHex(value)) return false;
        try {
            if (value.length() == 66) {
                String prefix = value.substring(0, 2);
                if (!"02".equalsIgnoreCase(prefix) && !"03".equalsIgnoreCase(prefix)) {
                    return false;
                }
                java.math.BigInteger x = new java.math.BigInteger(value.substring(2), 16);
                if (x.compareTo(SECP256K1_P) >= 0) return false;
                java.math.BigInteger rhs = x.modPow(java.math.BigInteger.valueOf(3), SECP256K1_P)
                        .add(java.math.BigInteger.valueOf(7)).mod(SECP256K1_P);
                java.math.BigInteger y = rhs.modPow(SECP256K1_SQRT_EXP, SECP256K1_P);
                if (!y.multiply(y).mod(SECP256K1_P).equals(rhs)) return false;
                // The other root is (p - y), which has opposite parity for nonzero y.
                // Either prefix is valid when a curve point exists; the prefix selects the root.
                return true;
            }
            if (value.length() != 130 || !"04".equalsIgnoreCase(value.substring(0, 2))) {
                return false;
            }
            java.math.BigInteger x = new java.math.BigInteger(value.substring(2, 66), 16);
            java.math.BigInteger y = new java.math.BigInteger(value.substring(66), 16);
            if (x.compareTo(SECP256K1_P) >= 0 || y.compareTo(SECP256K1_P) >= 0) return false;
            java.math.BigInteger left = y.multiply(y).mod(SECP256K1_P);
            java.math.BigInteger right = x.modPow(java.math.BigInteger.valueOf(3), SECP256K1_P)
                    .add(java.math.BigInteger.valueOf(7)).mod(SECP256K1_P);
            return left.equals(right);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // The native engine's start value represents a secp256k1-sized integer.
    // Accept 1–64 hexadecimal digits, while allowing leading zeroes.
    static boolean isValidStartValue(String value) {
        return value != null && value.length() <= 64 && isHex(value);
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

    private void showDiagnostics() {
        StringBuilder report = new StringBuilder();
        report.append("Kangaroo local diagnostics\n")
                .append("Status: ").append(SolverService.status).append('\n')
                .append("Running: ").append(SolverService.running).append('\n')
                .append("Operations: ").append(SolverService.ops).append('\n')
                .append("Distinguished points: ").append(SolverService.dps).append('\n')
                .append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("\nReview this text before sharing; engine logs may contain target or configuration details.\n");

        File dir = getFilesDir();
        try {
            String current = DiagnosticLog.readTail(new File(dir, "scan.log"), 80);
            report.append("\n--- Current log (last 80 lines) ---\n")
                    .append(current.isEmpty() ? "No current log entries\n" : current);
            String backup = DiagnosticLog.readTail(new File(dir, "scan.log.1"), 40);
            if (!backup.isEmpty()) {
                report.append("\n--- Previous log (last 40 lines) ---\n").append(backup);
            }
        } catch (java.io.IOException e) {
            report.append("\nCould not read diagnostic log: ").append(e.getMessage());
        }

        final String diagnosticText = report.toString();
        new AlertDialog.Builder(this)
                .setTitle("Local diagnostics")
                .setMessage(diagnosticText)
                .setPositiveButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("Kangaroo diagnostics", diagnosticText));
                        Toast.makeText(this, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showPuzzleDataValidation() {
        StringBuilder report = new StringBuilder("PUZZLE DATA VALIDATION\n\n");
        boolean inputsValid = true;

        String publicKey = pub.getText().toString().trim();
        boolean keyValid = isPublicKeyHex(publicKey);
        report.append(keyValid ? "[VALID] Public-key encoding and curve point\n"
                : "[INVALID] Public key: encoding or curve point is invalid\n");
        inputsValid &= keyValid;

        String startValue = start.getText().toString().trim();
        boolean startValid = isValidStartValue(startValue);
        report.append(startValid ? "[VALID FORMAT] Start value is hexadecimal\n"
                : "[INVALID] Start value must be 1–64 hexadecimal digits\n");
        inputsValid &= startValid;

        boolean numericValid = true;
        try {
            int rangeBits = Integer.parseInt(range.getText().toString().trim());
            int dpBits = Integer.parseInt(dp.getText().toString().trim());
            int count = Integer.parseInt(kangaroos.getText().toString().trim());
            boolean rangeOk = rangeBits >= 1 && rangeBits <= 256;
            boolean dpOk = dpBits >= 1 && dpBits <= 256;
            boolean countOk = count >= 1 && count <= 4096;
            report.append(rangeOk ? "[VALID FORMAT] Range field is within UI limits\n"
                    : "[INVALID] Range field must be 1–256\n");
            report.append(dpOk ? "[VALID FORMAT] DP bits field is within UI limits\n"
                    : "[INVALID] DP bits must be 1–256\n");
            report.append(countOk ? "[VALID FORMAT] Kangaroo count is within UI limits\n"
                    : "[INVALID] Kangaroo count must be 1–4096\n");
            numericValid = rangeOk && dpOk && countOk;
        } catch (NumberFormatException e) {
            report.append("[INVALID] Range, DP bits and kangaroo count must be whole numbers\n");
            numericValid = false;
        }
        inputsValid &= numericValid;

        File dpFile = new File(getFilesDir(), "dp.json");
        if (!dpFile.exists()) {
            report.append("[UNVERIFIED] dp.json is missing; no stored DP file to inspect\n");
        } else if (!dpFile.isFile() || !dpFile.canRead()) {
            report.append("[INVALID] dp.json is not a readable regular file\n");
            inputsValid = false;
        } else if (dpFile.length() == 0) {
            report.append("[INVALID] dp.json exists but is empty\n");
            inputsValid = false;
        } else {
            report.append("[UNVERIFIED] dp.json readable; size=" + dpFile.length()
                    + " bytes. Native schema/content not verified\n");
        }

        report.append("\nOVERALL: ").append(inputsValid ? "INPUT FORMAT CHECK PASSED (dp.json content unverified)" : "INPUT ERRORS FOUND")
                .append("\n\nIMPORTANT: This checks input format and file metadata only. It does not verify that the public key belongs to the intended puzzle, that the native engine can load/save this file, or that any reported result is correct.");

        final String validationReport = report.toString();
        new AlertDialog.Builder(this)
                .setTitle("Puzzle Data Validation")
                .setMessage(validationReport)
                .setPositiveButton("Copy report", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Puzzle data validation", validationReport));
                        Toast.makeText(this, "Validation report copied", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showHealthCenter() {
        final String report = AppHealthCheck.createReport(this);
        new AlertDialog.Builder(this)
                .setTitle("App Health Center")
                .setMessage(report)
                .setPositiveButton("Copy report", (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("Kangaroo health report", report));
                        Toast.makeText(this, "Health report copied", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void refresh() {
        statusText.setText(SolverService.status);
        boolean active = SolverService.running;
        // Keep the display awake only while the solver is running and this Activity is visible.
        if (active) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        boolean busy = SolverService.isBusy();
        if (startButton != null) startButton.setEnabled(!busy);
        if (stopButton != null) stopButton.setEnabled(busy);
        liveBadge.setText(active ? "● RUNNING" : busy ? "● STOPPING" : "● STOPPED");
        liveBadge.setTextColor(active ? ACCENT : MUTED);
        liveBadge.setBackground(shape(active ? 0xFF25204B : 0xFF1C2942, 30));
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
        String liveSpeed = SolverService.running
                && SolverPerformancePolicy.isTelemetryFresh(
                        android.os.SystemClock.elapsedRealtime(),
                        SolverService.lastTelemetryAtMs,
                        SolverPerformancePolicy.NO_PROGRESS_TIMEOUT_MS)
                ? SolverService.speed : "—";
        speedText.setText("Speed: " + liveSpeed);

        tempText.setText("Battery temperature: "
                + AppHealthCheck.formatTemperature(SolverService.temp));

        if (SolverService.found == null || SolverService.found.isEmpty()) {
            resultText.setText("");
        } else {
            resultText.setText("RESULT\n" + SolverService.found);
        }
    }


    private void chooseExportFile() {
        final String[] names = {"dp.json", "scan.log"};
        new AlertDialog.Builder(this)
                .setTitle("Export a private app file")
                .setItems(names, (dialog, which) -> {
                    pendingExportName = names[which];
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_TITLE, pendingExportName);
                    try {
                        startActivityForResult(intent, REQUEST_EXPORT_FILE);
                    } catch (android.content.ActivityNotFoundException e) {
                        pendingExportName = null;
                        Toast.makeText(this,
                                "No file picker is available on this device",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_FILE) return;

        String filename = pendingExportName;
        pendingExportName = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (!"dp.json".equals(filename) && !"scan.log".equals(filename)) {
            Toast.makeText(this, "Invalid export selection", Toast.LENGTH_LONG).show();
            return;
        }

        File source = new File(getFilesDir(), filename);
        if (!source.isFile() || !source.canRead()) {
            Toast.makeText(this, filename + " is missing or unreadable",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try (java.io.InputStream in = new java.io.FileInputStream(source);
             java.io.OutputStream out =
                     getContentResolver().openOutputStream(data.getData(), "w")) {
            if (out == null) throw new java.io.IOException("Could not open destination");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
            Toast.makeText(this, filename + " exported",
                    Toast.LENGTH_LONG).show();
        } catch (java.io.IOException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
