package com.heartsync.app.activity;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivitySettingsBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.net.HttpClient;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.PrefUtils;

/**
 * 设置页。
 *
 * <p>技术点对应：SharedPreferences 数据存储、网络开发（服务器连通性测试）、
 * 大模型 API 配置。</p>
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private HeartSyncDb db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = HeartSyncDb.getInstance(this);

        loadConfig();
        setupProviderChips();
        setupActions();
    }

    /**
     * 从 SharedPreferences 读取配置并回填界面。
     */
    private void loadConfig() {
        binding.etApiKey.setText(PrefUtils.getApiKey(this));
        binding.etApiBase.setText(PrefUtils.getApiBase(this));
        binding.etModel.setText(PrefUtils.getModel(this));
        binding.etServerBase.setText(PrefUtils.getServerBase(this));
    }

    /**
     * 服务商快捷选择。
     */
    private void setupProviderChips() {
        binding.cgProvider.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chipDeepSeek) {
                PrefUtils.applyPreset(this, PrefUtils.PRESET_DEEPSEEK);
            } else if (id == R.id.chipZhipu) {
                PrefUtils.applyPreset(this, PrefUtils.PRESET_ZHIPU);
            } else if (id == R.id.chipMoonshot) {
                PrefUtils.applyPreset(this, PrefUtils.PRESET_MOONSHOT);
            } else {
                PrefUtils.applyPreset(this, PrefUtils.PRESET_QWEN);
            }
            // 回填新的接口地址与模型名
            binding.etApiBase.setText(PrefUtils.getApiBase(this));
            binding.etModel.setText(PrefUtils.getModel(this));
        });
    }

    /**
     * 绑定页面操作。
     */
    private void setupActions() {
        binding.btnSaveSettings.setOnClickListener(v -> saveSettings());
        binding.btnClearCache.setOnClickListener(v -> confirmClearCache());
        binding.btnTestServer.setOnClickListener(v -> testServer());
    }

    /**
     * 保存配置。
     */
    private void saveSettings() {
        String apiKey = binding.etApiKey.getText().toString().trim();
        String apiBase = binding.etApiBase.getText().toString().trim();
        String model = binding.etModel.getText().toString().trim();
        String serverBase = binding.etServerBase.getText().toString().trim();

        if (TextUtils.isEmpty(apiBase)) {
            CommonUtils.toast(this, "接口地址不能为空");
            return;
        }
        if (TextUtils.isEmpty(model)) {
            CommonUtils.toast(this, "模型名称不能为空");
            return;
        }
        if (TextUtils.isEmpty(serverBase)) {
            CommonUtils.toast(this, "服务器地址不能为空");
            return;
        }

        PrefUtils.setApiKey(this, apiKey);
        PrefUtils.setApiBase(this, apiBase);
        PrefUtils.setModel(this, model);
        PrefUtils.setServerBase(this, serverBase);

        CommonUtils.toast(this, getString(R.string.common_success));
    }

    /**
     * 确认后清除本地数据。
     */
    private void confirmClearCache() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("清除本地数据")
                .setMessage("将清除全部睡眠、运动、情绪记录，此操作不可撤销。确定继续吗？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_confirm, (dialog, which) -> {
                    db.clearAllRecords();
                    CommonUtils.toast(this, "已清除本地数据");
                })
                .show();
    }

    /**
     * 测试业务服务器连通性。
     */
    private void testServer() {
        String serverBase = binding.etServerBase.getText().toString().trim();
        if (TextUtils.isEmpty(serverBase)) {
            binding.tvTestResult.setText("请先填写服务器地址");
            return;
        }
        PrefUtils.setServerBase(this, serverBase);

        binding.tvTestResult.setText("正在测试连接…");
        String url = PrefUtils.serverUrl(this, "/api/health");
        HttpClient.get(url, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                binding.tvTestResult.setText("连接成功：服务器响应正常");
            }

            @Override
            public void onFailure(String error) {
                binding.tvTestResult.setText("连接失败：" + error
                        + "\n提示：模拟器请使用 10.0.2.2，真机请使用电脑局域网 IP，"
                        + "或执行 adb reverse tcp:8080 tcp:8080");
            }
        });
    }
}
