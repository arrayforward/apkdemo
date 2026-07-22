package com.anomaly.goldiesettings.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.anomaly.goldiesettings.databinding.ActivityWifiBinding;
import com.anomaly.goldiesettings.databinding.DialogWifiPasswordBinding;
import com.anomaly.goldiesettings.model.Settings;
import com.anomaly.goldiesettings.util.L;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * WiFi settings page.
 *
 * <p>The original goldieos app drives an embedded WS63 WiFi service via
 * a service manager; on Android we use the platform WifiManager to scan,
 * list, connect. Functionally equivalent to the original screen.
 *
 * <p>Scan results are de-duplicated by BSSID and SSID, sorted by signal
 * strength, and only one scan runs at a time (subsequent calls cancel
 * the in-flight task).
 */
public class WifiSettingsActivity extends AppCompatActivity {

    private ActivityWifiBinding b;
    private WifiManager wm;
    private final List<String> apLabels = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile Future<?> scanTask;
    private volatile long lastScanAt;
    private static final long SCAN_DEBOUNCE_MS = 2500;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityWifiBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.toolbar.setNavigationOnClickListener(v -> finish());
        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, apLabels);
        b.wifiList.setAdapter(adapter);

        b.swWifi.setOnCheckedChangeListener((v, checked) -> {
            if (checked) {
                if (!wm.isWifiEnabled()) wm.setWifiEnabled(true);
                scanNow();
            } else {
                wm.setWifiEnabled(false);
                clearList();
            }
        });

        b.wifiList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apLabels.size()) return;
            String ssid = apLabels.get(position);
            showPasswordDialog(ssid);
        });

        b.swWifi.setChecked(wm.isWifiEnabled());
        updateConnectionState();
    }

    @Override protected void onResume() {
        super.onResume();
        if (wm.isWifiEnabled()) scanNow();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        Future<?> t = scanTask;
        if (t != null) t.cancel(true);
        exec.shutdownNow();
    }

    private void clearList() {
        apLabels.clear();
        adapter.notifyDataSetChanged();
        b.wifiEmpty.setVisibility(View.VISIBLE);
        b.wifiList.setVisibility(View.GONE);
    }

    private void scanNow() {
        long now = System.currentTimeMillis();
        if (now - lastScanAt < SCAN_DEBOUNCE_MS) return;
        lastScanAt = now;

        Future<?> prev = scanTask;
        if (prev != null) prev.cancel(true);
        scanTask = exec.submit(this::doScan);
    }

    private void doScan() {
        try {
            boolean ok = wm.startScan();
            L.i("WiFi", "startScan ok=%b", ok);
            if (!ok) {
                runOnUiThread(this::renderEmpty);
                return;
            }
            // Poll briefly for results; startScan is async and may take a moment
            List<ScanResult> results = null;
            for (int i = 0; i < 5; i++) {
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(400);
                results = wm.getScanResults();
                if (results != null && !results.isEmpty()) break;
            }
            List<String> labels = dedupeAndSort(results);
            runOnUiThread(() -> renderList(labels));
        } catch (InterruptedException ie) {
            // cancelled
        } catch (Throwable t) {
            L.e("WiFi", "scan: %s", t.getMessage());
            runOnUiThread(this::renderEmpty);
        }
    }

    private List<String> dedupeAndSort(List<ScanResult> results) {
        if (results == null || results.isEmpty()) return Collections.emptyList();
        // Keep the strongest result per (SSID, BSSID pair) — i.e. one entry
        // per BSSID. Multiple APs with the same SSID (5G vs 2.4G) are kept
        // as separate entries with a channel tag.
        java.util.Map<String, ScanResult> bestByBssid = new java.util.HashMap<>();
        for (ScanResult r : results) {
            if (r.SSID == null || r.SSID.isEmpty()) continue;     // skip hidden
            String key = r.BSSID == null ? r.SSID : r.BSSID;
            ScanResult cur = bestByBssid.get(key);
            if (cur == null || r.level > cur.level) bestByBssid.put(key, r);
        }
        // Then merge by SSID, keeping the strongest only — most users don't
        // care about the secondary 5G radio. This eliminates the "duplicated
        // many times" symptom in the list.
        java.util.Map<String, ScanResult> bestBySsid = new java.util.HashMap<>();
        Set<String> ssidSeen = new HashSet<>();
        for (ScanResult r : bestByBssid.values()) {
            String ssid = r.SSID;
            ScanResult cur = bestBySsid.get(ssid);
            if (cur == null || r.level > cur.level) bestBySsid.put(ssid, r);
            ssidSeen.add(ssid);
        }
        List<ScanResult> sorted = new ArrayList<>(bestBySsid.values());
        Collections.sort(sorted, new Comparator<ScanResult>() {
            @Override public int compare(ScanResult a, ScanResult b) { return b.level - a.level; }
        });
        List<String> labels = new ArrayList<>();
        for (ScanResult r : sorted) {
            String label = r.SSID;
            if (r.capabilities != null && r.capabilities.contains("WPA")) label += " 🔒";
            label += "  (" + r.level + "dBm)";
            labels.add(label);
        }
        return labels;
    }

    private void renderList(List<String> labels) {
        apLabels.clear();
        apLabels.addAll(labels);
        if (apLabels.isEmpty()) {
            b.wifiEmpty.setVisibility(View.VISIBLE);
            b.wifiList.setVisibility(View.GONE);
        } else {
            b.wifiEmpty.setVisibility(View.GONE);
            b.wifiList.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private void renderEmpty() {
        renderList(Collections.emptyList());
    }

    private void updateConnectionState() {
        WifiInfo info = wm.getConnectionInfo();
        if (info != null && info.getNetworkId() != -1) {
            String ssid = info.getSSID();
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            if (ssid != null && !ssid.isEmpty() && !ssid.equals("<unknown ssid>")) {
                Settings.get().setWifiSsid(ssid);
            }
        }
    }

    private void showPasswordDialog(String ssid) {
        // The label has a trailing "  (-NNdBm)" and possibly a lock icon. Strip
        // these so we connect to the real SSID.
        String real = ssid;
        int lock = real.indexOf(" 🔒");
        if (lock > 0) real = real.substring(0, lock);
        int sig = real.indexOf("  (");
        if (sig > 0) real = real.substring(0, sig);
        final String realSsid = real.trim();

        // Pre-fill with saved password if any
        DialogWifiPasswordBinding dlg = DialogWifiPasswordBinding.inflate(LayoutInflater.from(this));
        String saved = Settings.get().wifiPass();
        if (saved != null && !saved.isEmpty()) dlg.dlgPassword.setText(saved);
        AlertDialog ad = new AlertDialog.Builder(this)
            .setView(dlg.getRoot())
            .create();
        dlg.dlgConnect.setOnClickListener(v -> {
            String pwd = dlg.dlgPassword.getText() == null ? "" : dlg.dlgPassword.getText().toString();
            connectTo(realSsid, pwd);
            ad.dismiss();
        });
        dlg.dlgCancel.setOnClickListener(v -> ad.dismiss());
        ad.show();
    }

    private void connectTo(String ssid, String pwd) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 9002);
            Toast.makeText(this, "需要定位权限", Toast.LENGTH_SHORT).show();
            return;
        }
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + ssid + "\"";
        conf.preSharedKey = "\"" + pwd + "\"";
        conf.status = WifiConfiguration.Status.ENABLED;
        conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        int netId = wm.addNetwork(conf);
        if (netId == -1) {
            Toast.makeText(this, "无法添加网络", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = wm.enableNetwork(netId, true);
        boolean saved = wm.saveConfiguration();
        Settings.get().setWifiSsid(ssid).setWifiPass(pwd);
        Toast.makeText(this, "连接" + (ok ? "请求已发送" : "失败") + " saved=" + saved, Toast.LENGTH_LONG).show();
    }
}
