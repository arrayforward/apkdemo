package com.anomaly.goldiesettings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anomaly.goldiesettings.App;
import com.anomaly.goldiesettings.R;
import com.anomaly.goldiesettings.convai.ConvaiBridge;
import com.anomaly.goldiesettings.convai.ConvaiEvent;
import com.anomaly.goldiesettings.convai.ConvaiStatus;
import com.anomaly.goldiesettings.databinding.ActivityAiSettingsBinding;
import com.anomaly.goldiesettings.model.Settings;
import com.anomaly.goldiesettings.model.VoicePresets;

/**
 * AI settings page: avatar, voice, personality, relationship, API key.
 *
 * <p>Mirrors {@code FrameView_cloud} in
 * {@code D:\vit\apkdemo\examples\goldieos\apps\settings\main_ui.h:602}.
 */
public class AiSettingsActivity extends AppCompatActivity implements ConvaiBridge.Listener {

    private ActivityAiSettingsBinding b;
    private Settings settings;
    private ConvaiBridge bridge;
    private boolean suppressSwitch;

    public static final String EXTRA_CFG_TYPE = "cfg_type";
    public static final int CFG_AVATAR = 0, CFG_VOICE = 1, CFG_PERSON = 2, CFG_RELAT = 3, CFG_APIKEY = 4;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        settings = Settings.get();
        bridge = App.get().bridge();
        bridge.setListener(this);

        b.toolbar.setNavigationOnClickListener(v -> finish());

        b.btnAvatar.setOnClickListener(v ->
            openConfig(CFG_AVATAR, getString(R.string.ai_avatar)));
        b.btnVoice.setOnClickListener(v ->
            openConfig(CFG_VOICE, getString(R.string.ai_voice)));
        b.btnPerson.setOnClickListener(v ->
            openConfig(CFG_PERSON, getString(R.string.ai_personality)));
        b.btnRelat.setOnClickListener(v ->
            openConfig(CFG_RELAT, getString(R.string.ai_relationship)));
        b.btnApikey.setOnClickListener(v ->
            openConfig(CFG_APIKEY, getString(R.string.ai_apikey)));

        b.btnTalk.setOnClickListener(v ->
            startActivity(new Intent(this, TalkActivity.class)));

        b.swEngine.setChecked(bridge.engine().isStarted());
        b.swEngine.setOnCheckedChangeListener((v, checked) -> {
            if (suppressSwitch) return;
            if (checked) startEngine();
            else stopEngine();
        });

        refreshUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void openConfig(int type, String title) {
        Intent it = new Intent(this, AiConfigListActivity.class);
        it.putExtra(EXTRA_CFG_TYPE, type);
        it.putExtra("title", title);
        startActivity(it);
    }

    private void startEngine() {
        try {
            bridge.start(settings.wsUrl(), settings.agentId());
            bridge.engine().update(settings.buildConvaiConfigJson());
        } catch (Throwable t) {
            Toast.makeText(this, "启动失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopEngine() {
        bridge.stop();
        runOnUiThread(this::refreshUi);
    }

    private void refreshUi() {
        // Avatar
        int avatar = settings.avatarIdx();
        if (avatar == Settings.AVATAR_FEMALE) {
            b.imgAvatar.setImageResource(R.drawable.avatar_female);
            b.txtAvatarName.setText(R.string.avatar_female);
        } else {
            b.imgAvatar.setImageResource(R.drawable.avatar_male);
            b.txtAvatarName.setText(R.string.avatar_male);
        }
        // Status bars
        boolean started = bridge.engine().isStarted();
        if (started) {
            b.txtStatusConn.setText(R.string.ai_status_connected);
            b.txtStatusConn.setBackgroundColor(getColor(R.color.status_answering));
        } else {
            b.txtStatusConn.setText(R.string.ai_status_disconnected);
            b.txtStatusConn.setBackgroundColor(getColor(R.color.black));
        }
        ConvaiStatus cs = bridge.engine().status();
        b.txtStatusConv.setText(cs.displayName());
        b.txtStatusConv.setBackgroundColor(colorFor(cs));
        b.btnTalk.setVisibility(started ? View.VISIBLE : View.GONE);
        suppressSwitch = true;
        b.swEngine.setChecked(started);
        suppressSwitch = false;
    }

    private int colorFor(ConvaiStatus s) {
        switch (s) {
            case IDLE: return getColor(R.color.status_idle);
            case LISTENING: return getColor(R.color.status_listening);
            case THINKING: return getColor(R.color.status_thinking);
            case ANSWERING: return getColor(R.color.status_answering);
            case INTERRUPTED: return getColor(R.color.status_interrupted);
            case ANSWER_FINISHED: return getColor(R.color.status_listening);
        }
        return getColor(R.color.status_idle);
    }

    // --- ConvaiBridge.Listener ---
    @Override public void onEvent(ConvaiEvent e, String details) { runOnUiThread(this::refreshUi); }
    @Override public void onStatus(ConvaiStatus s) { runOnUiThread(this::refreshUi); }
    @Override public void onServerMessage(com.anomaly.goldiesettings.convai.ServerMessage m) {
        runOnUiThread(this::refreshUi);
    }
}
