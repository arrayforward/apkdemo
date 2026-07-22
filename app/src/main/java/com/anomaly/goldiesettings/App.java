package com.anomaly.goldiesettings;

import android.app.Application;
import android.media.AudioManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.anomaly.goldiesettings.convai.ConvaiBridge;
import com.anomaly.goldiesettings.model.Settings;
import com.anomaly.goldiesettings.util.L;

/**
 * Application root. Owns the singleton {@link ConvaiBridge} for the
 * app's lifetime and exposes a few helpers (current activity, last
 * connected WiFi, audio manager).
 */
public class App extends Application {

    private static volatile App instance;
    private ConvaiBridge bridge;
    private AudioManager audioManager;
    private volatile android.app.Activity currentActivity;

    public static App get() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        bridge = new ConvaiBridge(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStop(@NonNull LifecycleOwner owner) {
                L.i(L.TAG_BRIDGE, "app to background — keep engine running");
            }
        });
        L.i(L.TAG, "App.onCreate done; settings: %s", Settings.get().wsUrl());
    }

    public ConvaiBridge bridge() { return bridge; }
    public AudioManager audioManager() { return audioManager; }
    public void setCurrentActivity(android.app.Activity a) { this.currentActivity = a; }
    public android.app.Activity currentActivity() { return currentActivity; }
}
