package com.anomaly.goldiesettings.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.anomaly.goldiesettings.App;
import com.anomaly.goldiesettings.R;
import com.anomaly.goldiesettings.convai.ConvaiBridge;
import com.anomaly.goldiesettings.convai.ConvaiEvent;
import com.anomaly.goldiesettings.convai.ConvaiStatus;
import com.anomaly.goldiesettings.databinding.ActivityServerConfigBinding;
import com.anomaly.goldiesettings.model.Settings;
import com.anomaly.goldiesettings.util.L;

import org.json.JSONObject;

/**
 * Edit server URL / agent_id / product credentials. Save to Settings, then
 * optionally test-connect.
 */
public class ServerConfigActivity extends AppCompatActivity {

    private ActivityServerConfigBinding b;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityServerConfigBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        Settings s = Settings.get();
        b.inputUrl.setText(s.wsUrl());
        b.inputAgent.setText(s.agentId());
        b.inputProductId.setText(s.productId());
        b.inputProductKey.setText(s.productKey());
        b.inputProductSecret.setText(s.productSecret());
        b.inputDeviceName.setText(s.deviceName());

        b.btnSave.setOnClickListener(v -> {
            s.setWsUrl(text(b.inputUrl))
                .setAgentId(text(b.inputAgent))
                .setProductId(text(b.inputProductId))
                .setProductKey(text(b.inputProductKey))
                .setProductSecret(text(b.inputProductSecret))
                .setDeviceName(text(b.inputDeviceName));
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        });

        b.btnTest.setOnClickListener(v -> testConnect());
    }

    private String text(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void testConnect() {
        b.testResult.setVisibility(View.VISIBLE);
        b.testResult.setText("正在测试…");

        final String url = text(b.inputUrl);
        final String agent = text(b.inputAgent);
        if (TextUtils.isEmpty(url)) {
            b.testResult.setText("URL 不能为空");
            return;
        }

        final ConvaiBridge test = new ConvaiBridge(getApplicationContext());
        final boolean[] done = {false};

        test.setListener(new ConvaiBridge.Listener() {
            @Override public void onEvent(ConvaiEvent e, String details) {
                L.i("ServerTest", "event: %s %s", e, details);
                runOnUiThread(() -> {
                    if (done[0]) return;
                    if (e == ConvaiEvent.CONNECTED) {
                        done[0] = true;
                        b.testResult.setText(getString(R.string.server_test_ok) + " (" + details + ")");
                        test.stop();
                    } else if (e == ConvaiEvent.FAILED || e == ConvaiEvent.DISCONNECTED) {
                        done[0] = true;
                        b.testResult.setText(getString(R.string.server_test_fail_fmt, String.valueOf(details)));
                    }
                });
            }
            @Override public void onStatus(ConvaiStatus s) {}
            @Override public void onServerMessage(com.anomaly.goldiesettings.convai.ServerMessage m) {}
        });
        test.start(url, agent);
        // 8s timeout
        b.testResult.postDelayed(() -> {
            if (!done[0]) {
                done[0] = true;
                test.stop();
                b.testResult.setText("测试超时（8s）");
            }
        }, 8_000);
    }
}
