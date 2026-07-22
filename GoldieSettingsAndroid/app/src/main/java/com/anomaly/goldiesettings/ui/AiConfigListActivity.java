package com.anomaly.goldiesettings.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anomaly.goldiesettings.App;
import com.anomaly.goldiesettings.databinding.ActivityAiConfigListBinding;
import com.anomaly.goldiesettings.model.Settings;
import com.anomaly.goldiesettings.model.VoicePresets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One of: avatar / voice / personality / relationship / api-key sub-lists.
 *
 * <p>Mirrors {@code FrameView_config_wm} and the {@code ListView_cfgwmlist} in
 * {@code D:\vit\apkdemo\examples\goldieos\apps\settings\main_ui.h:550}.
 */
public class AiConfigListActivity extends AppCompatActivity {

    private ActivityAiConfigListBinding b;
    private Settings settings;
    private int cfgType;
    private String[] items;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAiConfigListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        settings = Settings.get();
        cfgType = getIntent().getIntExtra(AiSettingsActivity.EXTRA_CFG_TYPE, 0);
        b.cfgTitle.setText(getIntent().getStringExtra("title"));

        switch (cfgType) {
            case AiSettingsActivity.CFG_AVATAR: {
                items = VoicePresets.avatarList();
                b.cfgList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, items));
                b.cfgList.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE);
                b.cfgList.setItemChecked(settings.avatarIdx(), true);
                b.cfgList.setOnItemClickListener((parent, view, position, id) -> {
                    int prev = settings.avatarIdx();
                    settings.setAvatarIdx(position);
                    if (prev != position) {
                        // Reset voice/person/relat to defaults for the new gender
                        if (position == Settings.AVATAR_FEMALE) {
                            settings.setVoiceIdx(1).setPersonIdx(0).setRelatIdx(0);
                        } else {
                            settings.setVoiceIdx(0).setPersonIdx(2).setRelatIdx(0);
                        }
                    }
                    applyAndBack();
                });
                b.btnCancel.setOnClickListener(v -> finish());
                b.btnYes.setVisibility(View.GONE);
                b.cfgEdit.setVisibility(View.GONE);
                break;
            }
            case AiSettingsActivity.CFG_VOICE: {
                items = VoicePresets.voiceList(settings);
                b.cfgList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, items));
                b.cfgList.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE);
                b.cfgList.setItemChecked(settings.voiceIdx(), true);
                b.cfgList.setOnItemClickListener((parent, view, position, id) -> {
                    settings.setVoiceIdx(position);
                    applyAndBack();
                });
                b.btnCancel.setOnClickListener(v -> finish());
                b.btnYes.setVisibility(View.GONE);
                b.cfgEdit.setVisibility(View.GONE);
                break;
            }
            case AiSettingsActivity.CFG_PERSON: {
                items = VoicePresets.personalityList();
                b.cfgList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, items));
                b.cfgList.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE);
                b.cfgList.setItemChecked(settings.personIdx(), true);
                b.cfgList.setOnItemClickListener((parent, view, position, id) -> {
                    settings.setPersonIdx(position);
                    applyAndBack();
                });
                b.btnCancel.setOnClickListener(v -> finish());
                b.btnYes.setVisibility(View.GONE);
                b.cfgEdit.setVisibility(View.GONE);
                break;
            }
            case AiSettingsActivity.CFG_RELAT: {
                items = VoicePresets.relationshipList(settings);
                b.cfgList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, items));
                b.cfgList.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE);
                b.cfgList.setItemChecked(settings.relatIdx(), true);
                b.cfgList.setOnItemClickListener((parent, view, position, id) -> {
                    settings.setRelatIdx(position);
                    applyAndBack();
                });
                b.btnCancel.setOnClickListener(v -> finish());
                b.btnYes.setVisibility(View.GONE);
                b.cfgEdit.setVisibility(View.GONE);
                break;
            }
            case AiSettingsActivity.CFG_APIKEY: {
                items = new String[0];
                b.cfgList.setVisibility(View.GONE);
                b.cfgEdit.setVisibility(View.VISIBLE);
                b.cfgEdit.setText(settings.apiKey());
                b.btnYes.setVisibility(View.VISIBLE);
                b.btnYes.setOnClickListener(v -> {
                    settings.setApiKey(b.cfgEdit.getText() == null ? "" : b.cfgEdit.getText().toString());
                    Toast.makeText(this, "已保存 APIKey", Toast.LENGTH_SHORT).show();
                    finish();
                });
                b.btnCancel.setOnClickListener(v -> finish());
                break;
            }
        }
    }

    private void applyAndBack() {
        App.get().bridge().engine().update(settings.buildConvaiConfigJson());
        Toast.makeText(this, "已应用", Toast.LENGTH_SHORT).show();
        finish();
    }
}
