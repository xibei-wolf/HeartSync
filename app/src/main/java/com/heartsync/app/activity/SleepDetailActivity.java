package com.heartsync.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.heartsync.app.R;
import com.heartsync.app.ai.AiClient;
import com.heartsync.app.databinding.ActivitySleepDetailBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.SleepRecord;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;
import com.heartsync.app.util.JsonUtils;

import java.util.List;

/**
 * 睡眠记录详情页。
 *
 * <p>展示单条睡眠记录的时长、质量、深睡占比等明细，并调用大模型生成 AI 作息分析，
 * 支持编辑与删除记录。</p>
 *
 * <p>技术点对应：Activity 页面跳转与传值（Intent 传递 Serializable 对象）、
 * 网络开发（大模型 API 调用）、JSON 数据解析。</p>
 */
public class SleepDetailActivity extends AppCompatActivity {

    /** Intent 传递睡眠记录的 key，与 SleepActivity 保持一致 */
    public static final String EXTRA_SLEEP = "extra_sleep";

    private ActivitySleepDetailBinding binding;

    /** 当前展示的记录 */
    private SleepRecord record;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySleepDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Object extra = getIntent().getSerializableExtra(EXTRA_SLEEP);
        if (!(extra instanceof SleepRecord)) {
            CommonUtils.toast(this, "没有找到这条睡眠记录");
            finish();
            return;
        }
        record = (SleepRecord) extra;

        bindData();
        setupListeners();
        requestAiAnalysis();
    }

    /**
     * 把记录内容绑定到界面。
     */
    private void bindData() {
        binding.tvDuration.setText(record.getDurationText());

        binding.tvQuality.setText(record.getQualityText());
        binding.tvQuality.setTextColor(
                ContextCompat.getColor(this, qualityColorRes(record.getQualityLevel())));

        String date = CommonUtils.safe(record.getDate(), "");
        binding.tvDate.setText(date + " " + DateUtils.weekday(date));

        binding.tvSleepStart.setText(CommonUtils.safe(record.getSleepStart(), "--"));
        binding.tvWakeTime.setText(CommonUtils.safe(record.getWakeTime(), "--"));
        binding.tvDeepTime.setText(record.getDeepMinutes() + " 分钟");

        int duration = record.getDurationMinutes();
        int ratio = duration > 0 ? Math.round(record.getDeepMinutes() * 100f / duration) : 0;
        binding.tvDeepRatio.setText("深睡占比 " + ratio + "%");

        String note = record.getNote();
        binding.tvNote.setText(CommonUtils.isEmpty(note) ? "无备注" : note);
    }

    /**
     * 睡眠质量等级对应的文字颜色：2 好 / 1 中 / 0 差。
     */
    private static int qualityColorRes(int level) {
        if (level == 2) {
            return R.color.sleep_excellent;
        } else if (level == 1) {
            return R.color.sleep_fair;
        }
        return R.color.sleep_poor;
    }

    /**
     * 绑定按钮事件。
     */
    private void setupListeners() {
        binding.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, SleepActivity.class);
            intent.putExtra(EXTRA_SLEEP, record);
            startActivity(intent);
            finish();
        });

        binding.btnDelete.setOnClickListener(v -> confirmDelete());
    }

    /**
     * 删除前二次确认。
     */
    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除睡眠记录")
                .setMessage("确定要删除这条睡眠记录吗？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    HeartSyncDb.getInstance(this).deleteSleep(record.getId());
                    CommonUtils.toast(this, "已删除");
                    finish();
                })
                .show();
    }

    /**
     * 请求大模型生成作息分析。
     */
    private void requestAiAnalysis() {
        binding.pbAi.setVisibility(View.VISIBLE);
        binding.tvAiResult.setText("");

        String prompt = "请分析以下睡眠记录：\n"
                + "日期：" + CommonUtils.safe(record.getDate(), "--") + "\n"
                + "入睡：" + CommonUtils.safe(record.getSleepStart(), "--") + "\n"
                + "起床：" + CommonUtils.safe(record.getWakeTime(), "--") + "\n"
                + "时长：" + record.getDurationText() + "\n"
                + "深睡：" + record.getDeepMinutes() + " 分钟\n"
                + "主观质量：" + record.getQualityText() + "\n"
                + "备注：" + (CommonUtils.isEmpty(record.getNote()) ? "无" : record.getNote());

        new AiClient(this).chat(AiClient.PROMPT_SLEEP, prompt, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String content) {
                binding.pbAi.setVisibility(View.GONE);
                binding.tvAiResult.setText(buildAnalysisText(content));
            }

            @Override
            public void onError(String message) {
                binding.pbAi.setVisibility(View.GONE);
                if ("NO_API_KEY".equals(message)) {
                    binding.tvAiResult.setText("尚未配置大模型 API Key，可在「我的 - 设置」中填写后再试");
                } else {
                    binding.tvAiResult.setText("AI 分析暂不可用：" + message);
                }
            }
        });
    }

    /**
     * 把模型返回的内容整理成可读文本，解析失败时直接展示原文。
     */
    private String buildAnalysisText(String content) {
        JsonUtils.SleepAnalysis analysis = JsonUtils.parseSleepAnalysis(content);
        if (!analysis.isValid()) {
            return CommonUtils.safe(content, "暂无分析结果");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(CommonUtils.safe(analysis.summary, "暂无总结"));
        if (!CommonUtils.isEmpty(analysis.problem)) {
            sb.append("\n\n主要问题：").append(analysis.problem);
        }
        List<String> advice = analysis.advice;
        if (advice != null && !advice.isEmpty()) {
            sb.append("\n\n建议：\n");
            for (int i = 0; i < advice.size(); i++) {
                sb.append(i + 1).append(". ").append(advice.get(i));
                if (i < advice.size() - 1) {
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }
}
