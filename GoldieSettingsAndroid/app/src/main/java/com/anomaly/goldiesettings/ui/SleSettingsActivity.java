package com.anomaly.goldiesettings.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anomaly.goldiesettings.R;
import com.anomaly.goldiesettings.databinding.ActivitySleBinding;
import com.anomaly.goldiesettings.model.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * StarFlash (SLE) settings page — placeholder.
 *
 * <p>The original goldieos app talks to a WS63 SLE SDP service for
 * peer-to-peer NearLink. There's no equivalent on a stock Android
 * device, so this page provides the same UI affordances:
 * master/slave mode, pair code, device list, and pair/unpair dialog.
 *
 * <p>Mirrors {@code FrameView_sle} in
 * {@code D:\vit\apkdemo\examples\goldieos\apps\settings\main_ui.h:259}.
 */
public class SleSettingsActivity extends AppCompatActivity {

    private ActivitySleBinding b;
    private final List<String> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySleBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.toolbar.setNavigationOnClickListener(v -> finish());

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{getString(R.string.sle_mode_master), getString(R.string.sle_mode_slave)});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spinnerMode.setAdapter(modeAdapter);
        b.spinnerMode.setSelection(Settings.get().sleMode());
        b.spinnerMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Settings.get().setSleMode(position);
                if (position == 0) {
                    b.txtPairCode.setVisibility(View.GONE);
                    b.sleList.setVisibility(View.VISIBLE);
                } else {
                    int code = 100000 + new Random().nextInt(900000);
                    b.txtPairCode.setText(getString(R.string.sle_pair_code_fmt, code));
                    b.txtPairCode.setVisibility(View.VISIBLE);
                    b.sleList.setVisibility(View.GONE);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devices);
        b.sleList.setAdapter(adapter);
        b.sleList.setOnItemClickListener((p, v, pos, id) -> showPairDialog(devices.get(pos)));

        b.swSle.setOnCheckedChangeListener((v, checked) -> {
            if (checked) {
                if (Settings.get().sleMode() == 0) {
                    // mock: simulate finding 3 devices
                    devices.clear();
                    for (int i = 0; i < 3; i++) {
                        devices.add(String.format("SLE_%02X%02X%02X", new Random().nextInt(256), new Random().nextInt(256), new Random().nextInt(256)));
                    }
                    adapter.notifyDataSetChanged();
                }
            } else {
                devices.clear();
                adapter.notifyDataSetChanged();
            }
        });

        b.sleLocalAddr.setText(String.format(getString(R.string.sle_local_addr_fmt),
            new Random().nextInt(256), new Random().nextInt(256), new Random().nextInt(256)));
    }

    private void showPairDialog(String ssid) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint(R.string.sle_pair_dialog_title);
        new AlertDialog.Builder(this)
            .setTitle(R.string.sle_pair_dialog_title)
            .setView(et)
            .setPositiveButton(R.string.sle_yes, (d, w) -> Toast.makeText(this, "已配对 " + ssid, Toast.LENGTH_SHORT).show())
            .setNegativeButton(R.string.sle_no, null)
            .show();
    }
}
