package com.anomaly.goldiesettings.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.anomaly.goldiesettings.App;
import com.anomaly.goldiesettings.R;
import com.anomaly.goldiesettings.convai.ConvaiBridge;
import com.anomaly.goldiesettings.convai.ConvaiEmotion;
import com.anomaly.goldiesettings.convai.ConvaiEvent;
import com.anomaly.goldiesettings.convai.ConvaiStatus;
import com.anomaly.goldiesettings.convai.ServerMessage;
import com.anomaly.goldiesettings.databinding.ActivityTalkBinding;
import com.anomaly.goldiesettings.model.Settings;
import com.anomaly.goldiesettings.util.L;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Conversation page. Avatar eyes + bow + status text.
 *
 * <p>Mirrors {@code FrameView_talk} in
 * {@code D:\vit\apkdemo\examples\goldieos\apps\settings\main_ui.h:652}.
 */
public class TalkActivity extends AppCompatActivity implements ConvaiBridge.Listener {

    private static final int REQ_RECORD = 9001;
    private ActivityTalkBinding b;
    private ConvaiBridge bridge;
    private volatile boolean running;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTalkBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        Settings s = Settings.get();
        b.avatarView.setGender(s.avatarIdx() == Settings.AVATAR_MALE);

        bridge = App.get().bridge();
        bridge.setListener(this);

        // Seed the UI from the engine's CURRENT status: the engine may
        // already be LISTENING (started from the AI-settings page) long
        // before this activity is opened; onStatus alone would leave the
        // page stuck on the layout default ("准备中") forever.
        onStatus(bridge.engine().status());

        b.btnBack.setOnClickListener(v -> finish());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
        } else {
            startSession();
        }
    }

    private void startSession() {
        Settings s = Settings.get();
        running = true;
        if (!bridge.engine().isStarted()) {
            bridge.start(s.wsUrl(), s.agentId());
            bridge.engine().update(s.buildConvaiConfigJson());
        }
        b.avatarView.setRunning(true);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSession();
            } else {
                b.talkText.setText("麦克风权限被拒绝");
            }
        }
    }

    @Override protected void onPause() {
        super.onPause();
        b.avatarView.setRunning(false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (running) b.avatarView.setRunning(true);
        // Re-seed in case the engine status changed while we were paused.
        onStatus(bridge.engine().status());
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        b.avatarView.setRunning(false);
    }

    // --- ConvaiBridge.Listener ---

    @Override public void onEvent(ConvaiEvent e, String details) {
        L.i(L.TAG, "Talk event: %s %s", e, details);
    }

    @Override public void onStatus(ConvaiStatus s) {
        runOnUiThread(() -> {
            b.avatarView.setStatus(s);
            switch (s) {
                case IDLE: b.talkText.setText(R.string.talk_idle); break;
                case LISTENING: b.talkText.setText(R.string.talk_listening); break;
                case THINKING: b.talkText.setText(R.string.talk_thinking); break;
                case ANSWERING: b.talkText.setText(R.string.talk_answering); break;
                case INTERRUPTED: b.talkText.setText(R.string.talk_interrupted); break;
                default: b.talkText.setText(R.string.talk_ready);
            }
        });
    }

    @Override public void onServerMessage(ServerMessage m) {
        if (m.isFunctionCall()) {
            // emotion function call
            String name = m.functionName;
            if ("emotion".equals(name) && m.functionArgs != null) {
                String e = m.functionArgs.optString("emotion", "neutral");
                ConvaiEmotion ce = ConvaiEmotion.fromWire(e);
                runOnUiThread(() -> b.avatarView.setEmotion(ce));
            }
        }
    }
}
