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
import android.view.WindowInsets;
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
    private TextView puzzleHint, puzzlePreview;
    private final List<JSONObject> puzzleEntries = new ArrayList<>();
    private boolean catalogDataParsed = false;
    private int catalogRawRecordCount = 0;
    private int catalogEligibleEntryCount = 0;
    private String catalogLoadError = "";
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
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ enforces edge-to-edge for apps targeting API 35+.
            // Keep the scrollable controls clear of status/navigation bars and cutouts.
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

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
        puzzlePreview = text(getString(R.string.puzzle_preview_choose), 12, MUTED, false);
        puzzlePreview.setTextIsSelectable(true);
        LinearLayout.LayoutParams previewLp = lp(-1, -2);
        previewLp.topMargin = dp(8);
        puzzlePicker.addView(puzzlePreview, previewLp);
        Button loadPuzzleButton = new Button(this);
        loadPuzzleButton.setText(getString(R.string.load_selected_puzzle));
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
        startButton.setText(getString(R.string.start_button));
        startButton.setTextColor(0xFF100C2B);
        startButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        startButton.setBackground(shape(ACCENT, 14));
        actions.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        View spacer = new View(this);
        actions.addView(spacer, lp(12, 1));

        stopButton = new Button(this);
        stopButton.setText(getString(R.string.stop_button));
        stopButton.setTextColor(TEXT);
        stopButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        stopButton.setBackground(shape(0xFF34264A, 14));
        actions.addView(stopButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        LinearLayout.LayoutParams actionLp = lp(-1, -2);
        actionLp.topMargin = dp(18);
        root.addView(actions, actionLp);

        Button selfTestButton = new Button(this);
        selfTestButton.setText(getString(R.string.safe_self_test_button));
        selfTestButton.setTextColor(TEXT);
        selfTestButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        selfTestButton.setBackground(shape(0xFF164E63, 14));
        LinearLayout.LayoutParams selfTestLp = lp(-1, 50);
        selfTestLp.topMargin = dp(10);
        root.addView(selfTestButton, selfTestLp);
        selfTestButton.setOnClickListener(v -> startSafeSelfTest());

        Button diagnosticsButton = new Button(this);
        diagnosticsButton.setText(getString(R.string.diagnostics_button));
        diagnosticsButton.setTextColor(TEXT);
        diagnosticsButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        diagnosticsButton.setBackground(shape(0xFF1C2942, 14));
        LinearLayout.LayoutParams diagnosticsLp = lp(-1, 50);
        diagnosticsLp.topMargin = dp(10);
        root.addView(diagnosticsButton, diagnosticsLp);
        diagnosticsButton.setOnClickListener(v -> showDiagnostics());

        Button exportButton = new Button(this);
        exportButton.setText(getString(R.string.export_button));
        exportButton.setTextColor(ACCENT);
        exportButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        exportButton.setBackground(shape(0xFF25204B, 14));
        LinearLayout.LayoutParams exportLp = lp(-1, 50);
        exportLp.topMargin = dp(10);
        root.addView(exportButton, exportLp);
        exportButton.setOnClickListener(v -> chooseExportFile());

        Button healthButton = new Button(this);
        healthButton.setText(getString(R.string.health_check_button));
        healthButton.setTextColor(ACCENT);
        healthButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        healthButton.setBackground(shape(0xFF25204B, 14));
        LinearLayout.LayoutParams healthLp = lp(-1, 50);
        healthLp.topMargin = dp(10);
        root.addView(healthButton, healthLp);
        healthButton.setOnClickListener(v -> showHealthCenter());

        Button puzzleDataButton = new Button(this);
        puzzleDataButton.setText(getString(R.string.audit_catalog_button));
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
            statusText.setText(getString(R.string.stopping));
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
        catalogDataParsed = false;
        catalogRawRecordCount = 0;
        catalogEligibleEntryCount = 0;
        catalogLoadError = "";
        puzzleEntries.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("puzzles.json"), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            JSONArray records = new JSONArray(json.toString());
            catalogDataParsed = true;
            catalogRawRecordCount = records.length();
            List<String> labels = new ArrayList<>();
            int locallyEligibleRecords = 0;
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record == null) continue;
                int id = record.optInt("id", -1);
                int bits = record.optInt("bits", -1);
                if (id < 1 || bits < 1) continue;
                puzzleEntries.add(record);

                String state = record.optString("status", "unknown");
                Object publicKeyValue = record.opt("public_key");
                String publicKey = publicKeyValue instanceof String
                        ? (String) publicKeyValue : "";
                boolean hasKey = publicKeyValue instanceof String && isPublicKeyHex(publicKey);
                String startHex = record.optString("range_start_hex", "");
                String endHex = record.optString("range_end_hex", "");
                String rangeSize = record.optString("range_size_decimal", "");
                int searchBits = record.optInt("search_bits", -1);
                boolean bitsMatch = bits == id;
                boolean startValid = isValidStartValue(startHex);
                boolean rangeMetadataValid = bitsMatch
                        && PuzzleCatalogPolicy.matchesRangeMetadata(
                                id, startHex, endHex, rangeSize, searchBits);
                boolean supportedRange = PuzzleCatalogPolicy.hasSupportedSearchBits(searchBits);
                boolean blockedBySafety = PuzzleCatalogPolicy.isBlocked(state,
                        record.opt("private_key_already_public"),
                        record.opt("unsafe_for_wallets"));
                String sourceUrl = record.optString("source_url", "");
                String sourceLabel = record.optString("source_label", "");
                String sourceScope = record.optString("source_scope", "missing");
                String datasetSourceUrl = record.optString("dataset_source_url", "");
                String datasetRecordUrl = record.optString("dataset_record_url", "");
                boolean hasDatasetReference = PuzzleCatalogAuditPolicy.isHttpsSource(datasetSourceUrl)
                        || PuzzleCatalogAuditPolicy.isHttpsSource(datasetRecordUrl);
                boolean hasSourceReference = PuzzleCatalogAuditPolicy.hasSourceReference(
                        sourceUrl, sourceLabel, sourceScope);

                if (PuzzleCatalogAuditPolicy.isLocallyRunnable(state,
                        record.opt("private_key_already_public"),
                        record.opt("unsafe_for_wallets"), hasKey, startValid,
                        rangeMetadataValid, searchBits, sourceUrl, sourceLabel, sourceScope)) {
                    locallyEligibleRecords++;
                }

                StringBuilder availability = new StringBuilder();
                if (blockedBySafety) availability.append(" • blocked: status/safety");
                if (!hasSourceReference) {
                    availability.append(hasDatasetReference
                            ? " • primary record source missing (secondary atlas link only)"
                            : " • primary source missing");
                }
                if (!hasKey) availability.append(" • no usable public key");
                if (!startValid || !rangeMetadataValid) {
                    availability.append(" • invalid range metadata");
                } else if (!supportedRange) {
                    availability.append(" • unsupported search range");
                }
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
                    puzzleHint.setText(getString(R.string.choose_catalog_record));
                }
            });
            String loadedCount = getResources().getQuantityString(
                    R.plurals.catalog_records_loaded, puzzleEntries.size(), puzzleEntries.size());
            String eligibleCount = getResources().getQuantityString(
                    R.plurals.catalog_records_eligible, locallyEligibleRecords, locallyEligibleRecords);
            catalogEligibleEntryCount = locallyEligibleRecords;
            if (puzzleEntries.size() != 160 || catalogRawRecordCount != 160) {
                catalogLoadError = "Expected 160 valid records; parsed JSON records="
                        + catalogRawRecordCount + ", usable records=" + puzzleEntries.size();
            }
            puzzleHint.setText(getString(R.string.catalog_load_summary, loadedCount, eligibleCount));
        } catch (Exception e) {
            catalogDataParsed = false;
            catalogLoadError = e.getClass().getSimpleName() + ": " + safeDiagnosticMessage(e);
            puzzleEntries.clear();
            catalogRawRecordCount = 0;
            catalogEligibleEntryCount = 0;
            puzzleHint.setText(getString(R.string.catalog_unavailable, e.getClass().getSimpleName()));
            if (puzzlePreview != null) {
                puzzlePreview.setText(getString(R.string.puzzle_preview_unavailable));
            }
            statusText.setText(getString(R.string.catalog_load_failed));
        }
    }

    /**
     * Display catalog metadata even when a record is not eligible for native execution.
     * This is informational only: it never fills solver fields or changes the execution gate.
     */
    private void updateSelectedPuzzlePreview() {
        if (puzzlePreview == null) return;

        int position = puzzleSpinner == null ? -1 : puzzleSpinner.getSelectedItemPosition();
        if (position < 0 || position >= puzzleEntries.size()) {
            puzzlePreview.setText(getString(R.string.puzzle_preview_choose));
            return;
        }

        JSONObject record = puzzleEntries.get(position);
        int id = record.optInt("id", -1);
        int bits = record.optInt("bits", -1);
        String state = record.optString("status", "unknown");
        String key = record.optString("public_key", "");
        String startHex = record.optString("range_start_hex", "");
        String endHex = record.optString("range_end_hex", "");
        String rangeSize = record.optString("range_size_decimal", "");
        int searchBits = record.optInt("search_bits", -1);

        boolean blockedBySafety = PuzzleCatalogPolicy.isBlocked(state,
                record.opt("private_key_already_public"),
                record.opt("unsafe_for_wallets"));
        boolean keyValid = isPublicKeyHex(key);
        boolean rangeValid = bits == id && PuzzleCatalogPolicy.matchesRangeMetadata(
                id, startHex, endHex, rangeSize, searchBits)
                && PuzzleCatalogPolicy.hasSupportedSearchBits(searchBits);
        boolean hasSourceReference = PuzzleCatalogAuditPolicy.hasSourceReference(
                record.optString("source_url", ""),
                record.optString("source_label", ""),
                record.optString("source_scope", "missing"));

        String safetyState = getString(blockedBySafety
                ? R.string.puzzle_preview_safety_blocked
                : R.string.puzzle_preview_safety_passed);
        String keyState = getString(keyValid
                ? R.string.puzzle_preview_key_valid
                : R.string.puzzle_preview_key_missing);
        String rangeState = getString(rangeValid
                ? R.string.puzzle_preview_range_valid
                : R.string.puzzle_preview_range_invalid);
        String sourceState = getString(hasSourceReference
                ? R.string.puzzle_preview_source_present
                : R.string.puzzle_preview_source_missing);

        puzzlePreview.setText(getString(R.string.puzzle_preview_details,
                id, state, safetyState, keyState,
                startHex.isEmpty() ? "—" : startHex,
                endHex.isEmpty() ? "—" : endHex,
                rangeState, sourceState));
    }

    private void applySelectedPuzzle() {
        // Any failed/unsupported selection remains blocked until a valid entry loads.
        selectedPuzzleBlocked = true;
        loadedPuzzlePublicKey = loadedPuzzleStart = loadedPuzzleRange = null;
        updateSelectedPuzzlePreview();
        int position = puzzleSpinner == null ? -1 : puzzleSpinner.getSelectedItemPosition();
        if (position < 0 || position >= puzzleEntries.size()) {
            puzzleHint.setText(getString(R.string.choose_valid_catalog_record));
            return;
        }
        JSONObject record = puzzleEntries.get(position);
        String key = record.optString("public_key", "");
        String startHex = record.optString("range_start_hex", "");
        String endHex = record.optString("range_end_hex", "");
        String rangeSize = record.optString("range_size_decimal", "");
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
            puzzleHint.setText(getString(R.string.puzzle_blocked_safety, Integer.toString(id)));
            return;
        }
        if (!PuzzleCatalogAuditPolicy.hasSourceReference(
                record.optString("source_url", ""),
                record.optString("source_label", ""),
                record.optString("source_scope", "missing"))) {
            puzzleHint.setText(getString(R.string.puzzle_missing_source_reference, Integer.toString(id)));
            return;
        }
        if (!isPublicKeyHex(key)) {
            puzzleHint.setText(getString(R.string.puzzle_missing_public_key, Integer.toString(id)));
            return;
        }
        if (!isValidStartValue(startHex)) {
            puzzleHint.setText(getString(R.string.puzzle_invalid_start, Integer.toString(id)));
            return;
        }
        if (!PuzzleCatalogPolicy.matchesRangeMetadata(id, startHex, endHex, rangeSize, rangeBits)) {
            puzzleHint.setText(getString(R.string.puzzle_invalid_range_metadata, Integer.toString(id)));
            return;
        }
        if (!PuzzleCatalogPolicy.hasSupportedSearchBits(rangeBits)) {
            puzzleHint.setText(getString(R.string.puzzle_unsupported_range, Integer.toString(id)));
            return;
        }

        pub.setText(key);
        start.setText(startHex);
        range.setText(getString(R.string.numeric_field_value, rangeBits));
        loadedPuzzlePublicKey = key;
        loadedPuzzleStart = startHex;
        loadedPuzzleRange = Integer.toString(rangeBits);
        selectedPuzzleBlocked = false;
        puzzleHint.setText(getString(R.string.puzzle_loaded_summary, id, state));
    }

    private void startSafeSelfTest() {
        if (SolverService.isBusy()) {
            statusText.setText(getString(R.string.self_test_unavailable));
            return;
        }
        Intent testIntent = new Intent(this, SolverService.class);
        testIntent.putExtra("diagnostics_test", true);
        try {
            startForegroundService(testIntent);
            statusText.setText(getString(R.string.self_test_starting));
        } catch (Exception e) {
            statusText.setText(getString(R.string.self_test_start_failed, e.getMessage()));
        }
    }

    private void startSolver() {
        if (selectedPuzzleBlocked || !PuzzleCatalogPolicy.matchesLoadedValues(
                loadedPuzzlePublicKey, loadedPuzzleStart, loadedPuzzleRange,
                pub.getText().toString(), start.getText().toString(), range.getText().toString())) {
            statusText.setText(getString(R.string.start_blocked_reselect));
            return;
        }
        if (SolverService.isBusy()) {
            statusText.setText(SolverService.running
                    ? getString(R.string.engine_already_running)
                    : getString(R.string.previous_session_stopping));
            return;
        }
        String pubValue = pub.getText().toString().trim();
        String startValue = start.getText().toString().trim();
        String rangeValue = range.getText().toString().trim();
        String dpValue = dp.getText().toString().trim();
        String kangarooValue = kangaroos.getText().toString().trim();

        if (pubValue.isEmpty() || startValue.isEmpty() || rangeValue.isEmpty()
                || dpValue.isEmpty() || kangarooValue.isEmpty()) {
            statusText.setText(getString(R.string.input_all_fields_required));
            return;
        }
        if (!isPublicKeyHex(pubValue)) {
            statusText.setText(getString(R.string.invalid_public_key));
            return;
        }
        if (!isValidStartValue(startValue)) {
            statusText.setText(getString(R.string.invalid_start_value));
            return;
        }
        try {
            int rangeBits = Integer.parseInt(rangeValue);
            int dpBits = Integer.parseInt(dpValue);
            int count = Integer.parseInt(kangarooValue);
            if (rangeBits < 1 || rangeBits > 256) {
                statusText.setText(getString(R.string.invalid_range_bits));
                return;
            }
            if (dpBits < 1 || dpBits > 256) {
                statusText.setText(getString(R.string.invalid_dp_bits));
                return;
            }
            if (count < 1 || count > 4096) {
                statusText.setText(getString(R.string.invalid_kangaroo_count));
                return;
            }
        } catch (NumberFormatException e) {
            statusText.setText(getString(R.string.invalid_numeric_fields));
            return;
        }

        Intent i = new Intent(this, SolverService.class);
        i.putExtra("pub", pubValue);
        i.putExtra("start", startValue);
        i.putExtra("range", rangeValue);
        i.putExtra("dp", dpValue);
        i.putExtra("k", kangarooValue);

        try {
            startForegroundService(i);
            statusText.setText(getString(R.string.starting_engine));
        } catch (Exception e) {
            statusText.setText(getString(R.string.engine_start_failed, e.getMessage()));
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
                .append("Engine status: ").append(SolverService.status).append('\n')
                .append("Engine running: ").append(SolverService.running).append('\n')
                .append("Operations: ").append(SolverService.ops).append('\n')
                .append("Distinguished points: ").append(SolverService.dps).append('\n')
                .append("Dashboard message: ")
                .append(statusText == null ? "unavailable" : statusText.getText()).append('\n')
                .append("Catalog JSON parsed: ").append(catalogDataParsed).append('\n')
                .append("Catalog records in JSON: ").append(catalogRawRecordCount).append('\n')
                .append("Usable catalog entries: ").append(puzzleEntries.size()).append("/160\n")
                .append("Entries passing current execution gates: ")
                .append(catalogEligibleEntryCount).append('\n')
                .append("Selected entry gate: ").append(selectedPuzzleBlocked ? "BLOCKED" : "PASSED").append('\n')
                .append("Catalog selection message: ")
                .append(puzzleHint == null ? "unavailable" : puzzleHint.getText()).append('\n');
        if (!catalogLoadError.isEmpty()) {
            report.append("Catalog load detail: ").append(catalogLoadError).append('\n');
        }
        report.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("\nNo engine log entries can be normal when START is rejected in the Activity before the Service starts.\n")
                .append("Review this text before sharing; engine logs may contain target or configuration details.\n");

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
        final String inputReport = buildCurrentInputValidationReport();
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Full puzzle catalog audit")
                .setMessage("Checking every bundled record, range, safety flag, public key and source URL…")
                .setCancelable(false)
                .create();
        progress.show();

        Thread auditThread = new Thread(() -> {
            final String catalogReport = buildBundledCatalogAuditReport();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    progress.dismiss();
                    return;
                }
                progress.dismiss();
                final String validationReport = catalogReport + "\n\n" + inputReport;
                new AlertDialog.Builder(this)
                        .setTitle("Full Puzzle Data Audit")
                        .setMessage(validationReport)
                        .setPositiveButton("Copy report", (dialog, which) -> {
                            ClipboardManager clipboard =
                                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText(
                                        "Puzzle data audit", validationReport));
                                Toast.makeText(this, "Audit report copied", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Close", null)
                        .show();
            });
        }, "puzzle-catalog-audit");
        auditThread.start();
    }

    private String buildCurrentInputValidationReport() {
        StringBuilder report = new StringBuilder("CURRENT INPUT / STORAGE CHECK\n\n");
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

        report.append("\nCURRENT INPUT SUMMARY: ")
                .append(inputsValid
                        ? "input format checks passed; DP file content unverified"
                        : "input errors found")
                .append("\nNote: field format and file metadata do not verify native search semantics or result correctness.");
        return report.toString();
    }

    private String buildBundledCatalogAuditReport() {
        StringBuilder report = new StringBuilder("BUNDLED JSON CATALOG AUDIT\n");
        final int expectedCount = 160;
        int total = 0;
        int nonObjectRecords = 0;
        int invalidIds = 0;
        int duplicateIds = 0;
        int unexpectedIds = 0;
        int bitsMismatches = 0;
        int solved = 0;
        int unsolved = 0;
        int unknownStatuses = 0;
        int validPublicKeys = 0;
        int missingOrInvalidPublicKeys = 0;
        int validRanges = 0;
        int invalidRanges = 0;
        int unsupportedRanges = 0;
        int missingSafetyFlags = 0;
        int safetyBlocked = 0;
        int httpsSources = 0;
        int missingOrNonHttpsSources = 0;
        int datasetSourceReferences = 0;
        int datasetRecordReferences = 0;
        int chainTransactionReferences = 0;
        int chainExplorerMirrors = 0;
        int locallyRunnable = 0;
        int locallyRunnableWithHttpsSource = 0;

        java.util.Set<Integer> ids = new java.util.HashSet<>();
        java.util.List<Integer> invalidIdExamples = new java.util.ArrayList<>();
        java.util.List<Integer> duplicateIdExamples = new java.util.ArrayList<>();
        java.util.List<Integer> missingKeyExamples = new java.util.ArrayList<>();
        java.util.List<Integer> invalidRangeExamples = new java.util.ArrayList<>();
        java.util.List<Integer> missingSourceExamples = new java.util.ArrayList<>();
        java.util.List<Integer> localCandidateExamples = new java.util.ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("puzzles.json"), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            JSONArray records = new JSONArray(json.toString());
            total = records.length();

            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record == null) {
                    nonObjectRecords++;
                    continue;
                }

                Integer idValue = strictCatalogInteger(record.opt("id"));
                int id = idValue == null ? -1 : idValue;
                if (idValue == null || id < 1) {
                    invalidIds++;
                    addExample(invalidIdExamples, id);
                } else {
                    if (!ids.add(id)) {
                        duplicateIds++;
                        addExample(duplicateIdExamples, id);
                    }
                    if (id > expectedCount) unexpectedIds++;
                }

                Integer bitsValue = strictCatalogInteger(record.opt("bits"));
                if (idValue == null || bitsValue == null || !bitsValue.equals(idValue)) {
                    bitsMismatches++;
                }

                String state = record.opt("status") instanceof String
                        ? record.optString("status") : "unknown";
                if ("solved".equalsIgnoreCase(state)) solved++;
                else if ("unsolved".equalsIgnoreCase(state)) unsolved++;
                else unknownStatuses++;

                String key = record.opt("public_key") instanceof String
                        ? record.optString("public_key") : "";
                boolean keyValid = isPublicKeyHex(key);
                if (keyValid) validPublicKeys++;
                else {
                    missingOrInvalidPublicKeys++;
                    addExample(missingKeyExamples, id);
                }

                String startHex = record.opt("range_start_hex") instanceof String
                        ? record.optString("range_start_hex") : "";
                String endHex = record.opt("range_end_hex") instanceof String
                        ? record.optString("range_end_hex") : "";
                String sizeDecimal = record.opt("range_size_decimal") instanceof String
                        ? record.optString("range_size_decimal") : "";
                Integer searchBitsValue = strictCatalogInteger(record.opt("search_bits"));
                int searchBits = searchBitsValue == null ? -1 : searchBitsValue;
                boolean rangeValid = idValue != null && id >= 1 && id <= expectedCount
                        && bitsValue != null && bitsValue == id
                        && searchBitsValue != null
                        && PuzzleCatalogPolicy.matchesRangeMetadata(
                                id, startHex, endHex, sizeDecimal, searchBits);
                if (rangeValid) {
                    validRanges++;
                    if (!PuzzleCatalogPolicy.hasSupportedSearchBits(searchBits)) {
                        unsupportedRanges++;
                    }
                } else {
                    invalidRanges++;
                    addExample(invalidRangeExamples, id);
                }

                Object privateKeyFlag = record.opt("private_key_already_public");
                Object unsafeFlag = record.opt("unsafe_for_wallets");
                if (!(privateKeyFlag instanceof Boolean) || !(unsafeFlag instanceof Boolean)) {
                    missingSafetyFlags++;
                }
                if (PuzzleCatalogPolicy.isBlocked(state, privateKeyFlag, unsafeFlag)) {
                    safetyBlocked++;
                }

                Object sourceValue = record.opt("source_url");
                String sourceUrl = sourceValue instanceof String ? (String) sourceValue : null;
                Object sourceLabelValue = record.opt("source_label");
                String sourceLabel = sourceLabelValue instanceof String
                        ? (String) sourceLabelValue : null;
                Object sourceScopeValue = record.opt("source_scope");
                String sourceScope = sourceScopeValue instanceof String
                        ? (String) sourceScopeValue : "missing";
                boolean httpsSource = PuzzleCatalogAuditPolicy.isHttpsSource(sourceUrl);
                if (httpsSource) httpsSources++;
                else {
                    missingOrNonHttpsSources++;
                    addExample(missingSourceExamples, id);
                }

                if (PuzzleCatalogAuditPolicy.isHttpsSource(
                        record.optString("dataset_source_url", null))) {
                    datasetSourceReferences++;
                }
                if (PuzzleCatalogAuditPolicy.isHttpsSource(
                        record.optString("dataset_record_url", null))) {
                    datasetRecordReferences++;
                }
                if (PuzzleCatalogAuditPolicy.isHttpsSource(
                        record.optString("chain_tx_url", null))) {
                    chainTransactionReferences++;
                }
                if (PuzzleCatalogAuditPolicy.isHttpsSource(
                        record.optString("chain_tx_url_secondary", null))) {
                    chainExplorerMirrors++;
                }

                boolean startValid = isValidStartValue(startHex);
                boolean candidate = PuzzleCatalogAuditPolicy.isLocallyRunnable(
                        state, privateKeyFlag, unsafeFlag, keyValid, startValid,
                        rangeValid, searchBits, sourceUrl, sourceLabel, sourceScope);
                if (candidate) {
                    locallyRunnable++;
                    addExample(localCandidateExamples, id);
                    if (httpsSource) locallyRunnableWithHttpsSource++;
                }
            }

            int missingIds = 0;
            for (int id = 1; id <= expectedCount; id++) {
                if (!ids.contains(id)) missingIds++;
            }

            report.append("\nRecords: ").append(total)
                    .append(" (expected ").append(expectedCount).append(")\n")
                    .append("Non-object records: ").append(nonObjectRecords).append('\n')
                    .append("Invalid/missing IDs: ").append(invalidIds).append('\n')
                    .append("Duplicate ID occurrences: ").append(duplicateIds).append('\n')
                    .append("IDs outside 1–").append(expectedCount).append(": ")
                    .append(unexpectedIds).append('\n')
                    .append("Missing IDs in 1–").append(expectedCount).append(": ")
                    .append(missingIds).append('\n')
                    .append("bits != id or malformed bits: ").append(bitsMismatches).append('\n')
                    .append("Status counts: solved=").append(solved)
                    .append(", unsolved=").append(unsolved)
                    .append(", unknown=").append(unknownStatuses).append('\n')
                    .append("Usable secp256k1 public keys: ").append(validPublicKeys)
                    .append("; missing/invalid: ").append(missingOrInvalidPublicKeys).append('\n')
                    .append("Internally consistent range metadata: ").append(validRanges)
                    .append("; invalid/inconsistent: ").append(invalidRanges).append('\n')
                    .append("Metadata-valid but unsupported search width: ").append(unsupportedRanges).append('\n')
                    .append("Missing/malformed safety flag pairs: ").append(missingSafetyFlags).append('\n')
                    .append("Records blocked by current safety gates: ").append(safetyBlocked).append('\n')
                    .append("Per-record HTTPS source URLs present: ").append(httpsSources)
                    .append("; missing/non-HTTPS per-record sources: ").append(missingOrNonHttpsSources).append('\n')
                    .append("Secondary dataset index references (not per-record proof): ")
                    .append(datasetSourceReferences).append('\n')
                    .append("Secondary per-record atlas pages (not primary-source proof): ")
                    .append(datasetRecordReferences).append('\n')
                    .append("On-chain transaction references: ").append(chainTransactionReferences)
                    .append("; secondary explorer mirrors: ").append(chainExplorerMirrors).append('\n')
                    .append("Locally eligible after per-record source-reference gate: ").append(locallyRunnable).append('\n')
                    .append("Locally eligible and with an HTTPS per-record source plus label: ")
                    .append(locallyRunnableWithHttpsSource).append('\n');

            report.append("\nExamples (up to 12 IDs each):\n");
            report.append("Missing/invalid ID records: ").append(exampleList(invalidIdExamples)).append('\n');
            report.append("Duplicate IDs: ").append(exampleList(duplicateIdExamples)).append('\n');
            report.append("Missing/invalid public key: ").append(exampleList(missingKeyExamples)).append('\n');
            report.append("Invalid range metadata: ").append(exampleList(invalidRangeExamples)).append('\n');
            report.append("Missing/non-HTTPS per-record source: ").append(exampleList(missingSourceExamples)).append('\n');
            report.append("Locally eligible records: ").append(exampleList(localCandidateExamples)).append('\n');
        } catch (Exception e) {
            report.append("\nAUDIT FAILED: could not parse bundled puzzles.json (")
                    .append(e.getClass().getSimpleName()).append(")\n")
                    .append("Details: ").append(e.getMessage());
        }

        report.append("\n\nLIMITS: HTTPS URL presence does not prove source authenticity. "
                + "These checks do not query the live blockchain, prove a target is correctly labelled, "
                + "confirm native --range semantics, verify DP save/load, or independently validate a result.");
        return report.toString();
    }

    private static Integer strictCatalogInteger(Object value) {
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) {
            long candidate = (Long) value;
            if (candidate >= Integer.MIN_VALUE && candidate <= Integer.MAX_VALUE) {
                return (int) candidate;
            }
        }
        return null;
    }

    private static void addExample(java.util.List<Integer> list, int id) {
        if (list.size() < 12) list.add(id);
    }

    private static String exampleList(java.util.List<Integer> ids) {
        return ids.isEmpty() ? "none" : ids.toString();
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
        liveBadge.setText(getString(active ? R.string.badge_running : busy ? R.string.badge_stopping : R.string.badge_stopped));
        liveBadge.setTextColor(active ? ACCENT : MUTED);
        liveBadge.setBackground(shape(active ? 0xFF25204B : 0xFF1C2942, 30));
        opsText.setText(SolverService.ops);
        dpsText.setText(SolverService.dps);

        File dpFile = new File(getFilesDir(), "dp.json");
        long bytes = dpFile.exists() ? dpFile.length() : 0;
        String size = bytes < 1024
            ? getString(R.string.size_bytes, bytes)
            : getString(R.string.size_kilobytes, bytes / 1024.0);
        String dpState = !dpFile.exists() ? getString(R.string.dp_state_missing)
            : bytes == 0 ? getString(R.string.dp_state_empty)
            : getString(R.string.dp_state_unverified);
        long modifiedMs = dpFile.exists() ? dpFile.lastModified() : 0;
        String modified = modifiedMs > 0
            ? getString(R.string.modified_minutes_ago,
                Math.max(0, (System.currentTimeMillis() - modifiedMs) / 60000.0))
            : getString(R.string.value_unknown);

        storageText.setText(getString(R.string.dp_file_details, dpState, size, modified));
        restartText.setText(getString(R.string.restart_count, SolverService.restarts));
        long elapsed = SolverService.elapsedMs;
        if (SolverService.running && SolverService.sessionStartMs > 0) {
            elapsed = android.os.SystemClock.elapsedRealtime()
                    - SolverService.sessionStartMs;
        }
        long seconds = Math.max(0, elapsed / 1000);
        elapsedText.setText(getString(R.string.elapsed_time_format,
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
        speedText.setText(getString(R.string.speed_label, liveSpeed));

        tempText.setText(getString(R.string.temperature_label,
                AppHealthCheck.formatTemperature(SolverService.temp)));

        if (SolverService.found == null || SolverService.found.isEmpty()) {
            resultText.setText("");
        } else {
            resultText.setText(getString(R.string.result_header, SolverService.found));
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
