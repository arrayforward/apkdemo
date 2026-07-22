package com.anomaly.goldiesettings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anomaly.goldiesettings.App;
import com.anomaly.goldiesettings.R;
import com.anomaly.goldiesettings.databinding.ActivityMainBinding;

/**
 * Home screen. Four big entry buttons + a server-config shortcut.
 *
 * <p>Mirrors the original {@code FrameView_0} main page in
 * {@code D:\vit\apkdemo\examples\goldieos\apps\settings\main_ui.h:455}.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get().setCurrentActivity(this);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.entryAi.setOnClickListener(v ->
            startActivity(new Intent(this, AiSettingsActivity.class)));
        b.entryVolume.setOnClickListener(v ->
            startActivity(new Intent(this, VolumeActivity.class)));
        b.entrySle.setOnClickListener(v ->
            startActivity(new Intent(this, SleSettingsActivity.class)));
        b.entryWifi.setOnClickListener(v ->
            startActivity(new Intent(this, WifiSettingsActivity.class)));
        b.entryServer.setOnClickListener(v ->
            startActivity(new Intent(this, ServerConfigActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        App.get().setCurrentActivity(this);
    }

    @Override protected void onPause() {
        super.onPause();
    }
}
