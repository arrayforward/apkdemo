package com.anomaly.goldiesettings.model;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.anomaly.goldiesettings.App;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Persisted user settings: avatar/voice/personality/relationship indices,
 * API key, server URL, product credentials, volume.
 *
 * <p>Single source of truth for the UI. Backed by SharedPreferences.
 */
public final class Settings {

    public static final String PREF = "goldie_settings";

    public static final String KEY_AVATAR_IDX   = "avatar_idx";   // 0=female, 1=male
    public static final String KEY_VOICE_IDX     = "voice_idx";
    public static final String KEY_PERSON_IDX    = "person_idx";
    public static final String KEY_RELAT_IDX     = "relat_idx";
    public static final String KEY_API_KEY       = "api_key";

    public static final String KEY_WS_URL        = "ws_url";
    public static final String KEY_AGENT_ID      = "agent_id";
    public static final String KEY_PRODUCT_ID    = "product_id";
    public static final String KEY_PRODUCT_KEY   = "product_key";
    public static final String KEY_PRODUCT_SECRET= "product_secret";
    public static final String KEY_DEVICE_NAME   = "device_name";

    public static final String KEY_VOLUME        = "volume";          // 0..100
    /** Debug-only: inject res/raw/test_voice.wav instead of mic audio. */
    public static final String KEY_DEBUG_WAV_INJECT = "debug_wav_inject";
    public static final String KEY_WIFI_ON       = "wifi_on";
    public static final String KEY_WIFI_SSID     = "wifi_ssid";
    public static final String KEY_WIFI_PASS     = "wifi_pass";
    public static final String KEY_SLE_MODE      = "sle_mode";        // 0=master,1=slave

    public static final int AVATAR_FEMALE = 0;
    public static final int AVATAR_MALE = 1;

    private static volatile Settings instance;

    public static Settings get() {
        Settings s = instance;
        if (s == null) {
            synchronized (Settings.class) {
                s = instance;
                if (s == null) {
                    s = new Settings(App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE));
                    instance = s;
                }
            }
        }
        return s;
    }

    private final SharedPreferences sp;

    private Settings(SharedPreferences sp) { this.sp = sp; }

    public int avatarIdx() { return sp.getInt(KEY_AVATAR_IDX, 0); }
    public int voiceIdx() { return sp.getInt(KEY_VOICE_IDX, 1); }
    public int personIdx() { return sp.getInt(KEY_PERSON_IDX, 0); }
    public int relatIdx() { return sp.getInt(KEY_RELAT_IDX, 0); }
    public String apiKey() { return sp.getString(KEY_API_KEY, ""); }

    public Settings setAvatarIdx(int v) { sp.edit().putInt(KEY_AVATAR_IDX, v).apply(); return this; }
    public Settings setVoiceIdx(int v) { sp.edit().putInt(KEY_VOICE_IDX, v).apply(); return this; }
    public Settings setPersonIdx(int v) { sp.edit().putInt(KEY_PERSON_IDX, v).apply(); return this; }
    public Settings setRelatIdx(int v) { sp.edit().putInt(KEY_RELAT_IDX, v).apply(); return this; }
    public Settings setApiKey(String v) { sp.edit().putString(KEY_API_KEY, v).apply(); return this; }

    public String wsUrl()   { return sp.getString(KEY_WS_URL, "ws://10.0.2.2:9000"); }
    public String agentId() { return sp.getString(KEY_AGENT_ID, "your_agent_id"); }
    public String productId() { return sp.getString(KEY_PRODUCT_ID, "your_product_id"); }
    public String productKey() { return sp.getString(KEY_PRODUCT_KEY, "goldie-dev-key-2026"); }
    public String productSecret() { return sp.getString(KEY_PRODUCT_SECRET, "your_product_secret"); }
    public String deviceName() { return sp.getString(KEY_DEVICE_NAME, "android-client"); }

    public Settings setWsUrl(String v) { sp.edit().putString(KEY_WS_URL, v).apply(); return this; }
    public Settings setAgentId(String v) { sp.edit().putString(KEY_AGENT_ID, v).apply(); return this; }
    public Settings setProductId(String v) { sp.edit().putString(KEY_PRODUCT_ID, v).apply(); return this; }
    public Settings setProductKey(String v) { sp.edit().putString(KEY_PRODUCT_KEY, v).apply(); return this; }
    public Settings setProductSecret(String v) { sp.edit().putString(KEY_PRODUCT_SECRET, v).apply(); return this; }
    public Settings setDeviceName(String v) { sp.edit().putString(KEY_DEVICE_NAME, v).apply(); return this; }

    public int volume() { return sp.getInt(KEY_VOLUME, 50); }
    public Settings setVolume(int v) { sp.edit().putInt(KEY_VOLUME, Math.max(0, Math.min(100, v))).apply(); return this; }

    public boolean wifiOn() { return sp.getBoolean(KEY_WIFI_ON, false); }
    public Settings setWifiOn(boolean v) { sp.edit().putBoolean(KEY_WIFI_ON, v).apply(); return this; }
    public String wifiSsid() { return sp.getString(KEY_WIFI_SSID, ""); }
    public Settings setWifiSsid(String v) { sp.edit().putString(KEY_WIFI_SSID, v).apply(); return this; }
    public String wifiPass() { return sp.getString(KEY_WIFI_PASS, ""); }
    public Settings setWifiPass(String v) { sp.edit().putString(KEY_WIFI_PASS, v).apply(); return this; }

    public int sleMode() { return sp.getInt(KEY_SLE_MODE, 0); }
    public Settings setSleMode(int v) { sp.edit().putInt(KEY_SLE_MODE, v).apply(); return this; }

    public boolean debugWavInject() { return sp.getBoolean(KEY_DEBUG_WAV_INJECT, false); }
    public Settings setDebugWavInject(boolean v) { sp.edit().putBoolean(KEY_DEBUG_WAV_INJECT, v).apply(); return this; }

    // ---------------------------------------------------------------------
    //  ConvAI config JSON (mirrors main_app.cpp::generate_convai_config_json)
    // ---------------------------------------------------------------------

    /**
     * Build the {@code config} JSON to send as a {@code config_update} envelope
     * (or to stash at engine start time).
     */
    public JSONObject buildConvaiConfigJson() {
        try {
            String base = "你的名字叫小荷，你可以帮小朋友解决小烦恼哦。";
            String personality = VoicePresets.personalityPrompt()[personIdx()];
            String voice = (avatarIdx() == AVATAR_FEMALE)
                ? VoicePresets.voiceTypeFemale()[voiceIdx()]
                : VoicePresets.voiceTypeMale()[voiceIdx()];
            String relat = (avatarIdx() == AVATAR_FEMALE)
                ? VoicePresets.relationshipPromptFemale()[relatIdx()]
                : VoicePresets.relationshipPromptMale()[relatIdx()];

            return new JSONObject()
                .put("config", new JSONObject()
                    .put("llm_config", new JSONObject()
                        .put("system_messages", new org.json.JSONArray()
                            .put(base)
                            .put(personality)
                            .put(relat)))
                    .put("tts_config", new JSONObject()
                        .put("provider_params", new JSONObject()
                            .put("audio", new JSONObject()
                                .put("voice_type", voice)))));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /** Apply current settings: send a config_update to the live engine, if any. */
    public void applyTo(@NonNull com.anomaly.goldiesettings.convai.ConvaiBridge bridge) {
        bridge.engine().update(buildConvaiConfigJson());
    }
}
