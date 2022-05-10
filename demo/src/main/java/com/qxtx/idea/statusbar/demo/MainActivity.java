package com.qxtx.idea.statusbar.demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.qxtx.idea.statusbar.BuildConfig;
import com.qxtx.idea.statusbar.StatusBarLog;
import com.qxtx.idea.statusbar.StatusBarMgr;
import com.qxtx.idea.statusbar.tools.network.NetStateManager;
import com.qxtx.idea.statusbar.view.DefaultStatusBar;
import com.qxtx.idea.statusbar.view.SimSignalView;

import java.util.ArrayList;
import java.util.List;

 /**
 * @author QXTX-WORK
 * createDate 2021/9/17 14:15
 * <p><b>Description</b></p> 状态栏模块demo界面，提供两种模式展示状态栏
 */
public class MainActivity extends AppCompatActivity {

    private final int REQUEST_CODE_PERMISSION = 124;
    private List<String> permissionList = new ArrayList<>();

    private StatusBarMgr mgr = null;
    private DefaultStatusBar bar = null;

    private boolean isSecondUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_mode);

        ((TextView)findViewById(R.id.tipView)).setText("为状态栏选择一种模式");

        checkPermission();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.BLUETOOTH);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.ACCESS_NETWORK_STATE);
            }
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (!permissionList.isEmpty()) {
                requestPermissions(permissionList.toArray(new String[0]), REQUEST_CODE_PERMISSION);
            } else {
                doAction();
            }
        } else {
            doAction();
        }
    }

    private void doAction() {
        StatusBarLog.setDebug(BuildConfig.DEBUG, "StatusBar");
        chooseMode();
    }

    private void chooseMode() {
        Button btnDebugMode = findViewById(R.id.btnDebugMode);
        btnDebugMode.setOnClickListener(v -> {
            setContentView(R.layout.activity_main);
            bar = new DefaultStatusBar(this);
            mgr = new StatusBarMgr(getApplication(), bar, (int)Resources.getSystem().getDisplayMetrics().density * 60, true);
            showDebugUi();
            mgr.setStatusBarEnable(true);
            onResume();
            isSecondUi = true;
        });

        Button btnNormalMode = findViewById(R.id.btnNormalMode);
        btnNormalMode.setOnClickListener(v -> {
            if (mgr != null) {
                mgr.setStatusBarEnable(false);
            }

            bar = new DefaultStatusBar(this);
            btnDebugMode.setVisibility(View.GONE);
            btnNormalMode.setVisibility(View.GONE);
            ((TextView)findViewById(R.id.tipView)).setText("当前处于普通模式，可按返回键重新选择状态栏模式");
            bar.setThemeColor(Color.WHITE, Color.BLACK);

            mgr = new StatusBarMgr(getApplication(), bar, (int)Resources.getSystem().getDisplayMetrics().density * 60, false);
            mgr.setStatusBarEnable(true);
            onResume();
            isSecondUi = true;
        });
    }

    private void showDebugUi() {
        Button btnActChange = findViewById(R.id.activityChangeLayer);
        btnActChange.setOnClickListener(v -> {
            startActivity(new Intent(this, WhiteBgActivity.class));
        });

        EditText etThemeColor = findViewById(R.id.etColor);
        Button btnThemeColor = findViewById(R.id.btnThemeColor);
        btnThemeColor.setOnClickListener(v -> {
            CharSequence colorStr = etThemeColor.getText();
            if (colorStr != null && colorStr.length() == 6) {
                String s = colorStr.toString().toUpperCase();
                for (int i = 0; i < s.length(); i++) {
                    if (s.charAt(i) > 'F') {
                        Toast.makeText(MainActivity.this, "请输入正确的十六进制颜色值！如：FF0088", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                bar.setThemeColor(Color.parseColor("#" + colorStr));
            } else {
                Toast.makeText(MainActivity.this, "请输入正确的十六进制颜色值！如：FF0088", Toast.LENGTH_SHORT).show();
            }
        });

        EditText etPrimary = findViewById(R.id.etPrimarySignal);
        EditText etSub = findViewById(R.id.etSimSignal);
        Button simButton = findViewById(R.id.btnSim);

        Spinner snNetType = findViewById(R.id.spNetworkType);
        String[] arrays = getResources().getStringArray(R.array.networkType);
        snNetType.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, arrays));
        snNetType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        bar.onNetworkTypeChanged(NetStateManager.NetType.TYPE_NONE, Integer.MIN_VALUE, StatusBarMgr.TRANSFER_DUAL);
                        break;
                    case 1:
                    case 2:
                        float signalFraction = position == 1 ? 1f : 0.5f;
                        bar.onNetworkTypeChanged(NetStateManager.NetType.TYPE_WIFI, signalFraction, StatusBarMgr.TRANSFER_DUAL);
                        break;
                    case 3:
                        int primaryLevel = SimSignalView.LEVEL_NO_SIGNAL;
                        try {
                            CharSequence s1 = etPrimary.getText();
                            primaryLevel = Integer.parseInt(TextUtils.isEmpty(s1) ? SimSignalView.LEVEL_NO_SIGNAL + "" : s1.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (primaryLevel < 0) {
                            snNetType.setSelection(0);
                            return;
                        }
                        bar.onNetworkTypeChanged(NetStateManager.NetType.TYPE_3G, Integer.MIN_VALUE, StatusBarMgr.TRANSFER_DUAL);
                        break;
                    case 4:
                        primaryLevel = SimSignalView.LEVEL_NO_SIGNAL;
                        try {
                            CharSequence s1 = etPrimary.getText();
                            primaryLevel = Integer.parseInt(TextUtils.isEmpty(s1) ? SimSignalView.LEVEL_NO_SIGNAL + "" : s1.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (primaryLevel < 0) {
                            snNetType.setSelection(0);
                            Toast.makeText(MainActivity.this, "请检查是否输入了正确的sim信号，或者处于飞行模式", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        bar.onNetworkTypeChanged(NetStateManager.NetType.TYPE_4G, Integer.MIN_VALUE, StatusBarMgr.TRANSFER_DUAL);
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        etPrimary.setText("");
        etSub.setText("");
        bar.post(() -> {
            bar.findViewById(com.qxtx.idea.statusbar.R.id.sb_sim).setVisibility(View.VISIBLE);
            bar.onSimChanged(SimSignalView.LEVEL_NO_SIGNAL, SimSignalView.LEVEL_NO_SIGNAL);
        });
        simButton.setOnClickListener(v -> {
            int i1 = SimSignalView.LEVEL_NO_SIGNAL;
            int i2 = SimSignalView.LEVEL_NO_SIGNAL;
            try {
                CharSequence s1 = etPrimary.getText();
                CharSequence s2 = etSub.getText();
                i1 = Integer.parseInt(TextUtils.isEmpty(s1) ? SimSignalView.LEVEL_NO_SIGNAL + "" : s1.toString());
                i2 = Integer.parseInt(TextUtils.isEmpty(s2) ? SimSignalView.LEVEL_NO_SIGNAL + "" : s2.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (i1 < 0) {
                if (snNetType.getSelectedItemPosition() != 1) {
                    snNetType.setSelection(0);
                }
            }

            bar.onSimChanged(i1, i2);
        });

        Switch swAirplane = ((Switch)findViewById(R.id.swAirplane));
        swAirplane.setChecked(false);
        swAirplane.setOnCheckedChangeListener((buttonView, isChecked) -> {
            bar.onAirplaneChanged(isChecked);

            simButton.setEnabled(!isChecked);
            if (isChecked) {
                snNetType.setSelection(0);
            } else {
                bar.findViewById(com.qxtx.idea.statusbar.R.id.sb_sim).setVisibility(View.VISIBLE);
            }
        });

        EditText etBattery = findViewById(R.id.etBattery);
        etBattery.setText("50");
        CheckBox cbBatteryCharge = findViewById(R.id.cbBatteryCharge);
        cbBatteryCharge.setChecked(false);
        cbBatteryCharge.setOnCheckedChangeListener((buttonView, isChecked) -> {
            CharSequence level = etBattery.getText();
            if (level == null) {
                level = "0";
            }
            float fraction = 0f;
            try {
                fraction = Integer.parseInt(level.toString()) / 100f;
                fraction = Math.min(fraction, 1f);
            } catch (Exception e) {
                e.printStackTrace();
            }
            bar.onBatteryChanged(fraction, isChecked);
        });

        bar.post(() -> bar.onBatteryChanged(Integer.parseInt(etBattery.getText().toString()) / 100f, cbBatteryCharge.isChecked()));
        findViewById(R.id.btnBattery).setOnClickListener(v -> {
            boolean isCharge = cbBatteryCharge.isChecked();
            String level = etBattery.getText().toString();
            bar.onBatteryChanged((Integer.parseInt(TextUtils.isEmpty(level) ? "0" : level) / 100f), isCharge);
        });
        Switch swHeadset = findViewById(R.id.swHeadset);
        swHeadset.setChecked(false);
        swHeadset.setOnCheckedChangeListener((buttonView, isChecked) -> bar.onHeadSetChanged(isChecked));
    }

    @Override
    public void onBackPressed() {
        if (isSecondUi && mgr != null) {
            mgr.setStatusBarEnable(false);
            setContentView(R.layout.activity_main_mode);
            ((TextView) findViewById(R.id.tipView)).setText("为状态栏选择一种模式");
            isSecondUi = false;
            chooseMode();
        } else {
            super.onBackPressed();
        }
    }

        @Override
        public void onRequestPermissionsResult(int requestCode,  String[] permissions,  int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == REQUEST_CODE_PERMISSION) {
                permissionList.clear();
                checkPermission();
            }
        }
    }