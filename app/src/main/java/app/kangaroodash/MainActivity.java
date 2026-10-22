package app.kangaroodash;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    TextView info;
    Handler h = new Handler(Looper.getMainLooper());

    EditText field(LinearLayout p, String hint, String val) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(val); e.setTextSize(13);
        e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY);
        p.addView(e);
        return e;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0F1115);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(32, 64, 32, 32);
        sv.addView(p);
        TextView t = new TextView(this);
        t.setText("Kangaroo"); t.setTextSize(22); t.setTextColor(Color.WHITE);
        p.addView(t);
        final EditText pub = field(p, "public key", "031f6a332d3c5c4f2de2378c012f429cd109ba07d69690c6c701b6bb87860d6640");
        final EditText st = field(p, "start (hex)", "80000000000000000000000000000000000");
        final EditText rg = field(p, "range (bits)", "139");
        final EditText dp = field(p, "dp-bits", "20");
        final EditText kg = field(p, "kangaroos", "256");
        Button go = new Button(this);
        go.setText("Start");
        go.setOnClickListener(v -> {
            Intent i = new Intent(this, SolverService.class);
            i.putExtra("pub", pub.getText().toString().trim());
            i.putExtra("start", st.getText().toString().trim());
            i.putExtra("range", rg.getText().toString().trim());
            i.putExtra("dp", dp.getText().toString().trim());
            i.putExtra("k", kg.getText().toString().trim());
            startForegroundService(i);
        });
        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, SolverService.class);
            i.setAction("stop");
            startService(i);
        });
        p.addView(go); p.addView(stop);
        info = new TextView(this);
        info.setTextColor(Color.WHITE); info.setTextSize(16); info.setPadding(0, 32, 0, 0);
        p.addView(info);
        setContentView(sv);
        h.post(new Runnable() {
            public void run() { refresh(); h.postDelayed(this, 2000); }
        });
    }

    void refresh() {
        long kb = new File(getFilesDir(), "dp.json").length() / 1024;
        info.setText("നില: " + SolverService.status
            + "\nOps: " + SolverService.ops
            + "\nDPs: " + SolverService.dps
            + "\nസേവ് ചെയ്ത ഫയൽ: " + kb + " KB"
            + "\nRestarts: " + SolverService.restarts
            + "\nതാപനില: " + SolverService.temp + "°C"
            + (SolverService.found.isEmpty() ? "" : "\n\nKEY: " + SolverService.found));
    }
}
