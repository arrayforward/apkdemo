package com.anomaly.goldiesettings.ui;

import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anomaly.goldiesettings.App;
import com.anomaly.goldiesettings.databinding.ActivityVolumeBinding;
import com.anomaly.goldiesettings.model.Settings;

/**
 * System volume page. Mirrors {@code FrameView_volume} in main_ui.h:291.
 */
public class VolumeActivity extends AppCompatActivity {

    private ActivityVolumeBinding b;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityVolumeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.toolbar.setNavigationOnClickListener(v -> finish());

        AudioManager am = App.get().audioManager();
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int pct = (int) Math.round(cur * 100.0 / Math.max(1, max));
        b.volumeText.setText(String.valueOf(pct));
        b.volumeSeek.setValue(pct);
        b.volumeSeek.addOnChangeListener((slider, value, fromUser) -> {
            int p = (int) value;
            b.volumeText.setText(String.valueOf(p));
            if (fromUser) {
                int v = (int) Math.round(p * max / 100.0);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
                Settings.get().setVolume(p);
            }
        });
    }
}
