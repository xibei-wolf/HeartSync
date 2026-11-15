package com.heartsync.app.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.heartsync.app.R;
import com.heartsync.app.ai.AiClient;
import com.heartsync.app.databinding.ActivityMoodDetailBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.MoodRecord;
import com.heartsync.app.net.ServerApi;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;
import com.heartsync.app.util.JsonUtils;

import java.util.List;

/**
 * 情绪记录详情页。
 *
 * <p>展示情绪分值、标签与日记，并调用大模型生成结构化的情绪分析
 * （总结、可能原因、行动建议、风险等级、鼓励语）。</p>
 *
 * <p>技术点对应：Activity 页面跳转与传值（Intent 传递 Serializable 对象）、
 * 网络开发（大模型 API 调用）、JSON 数据解析、动态创建界面控件。</p>
 */
public class MoodDetailActivity extends AppCompatActivity {

    /** Intent 传递情绪记录的 key */
    public static final String EXTRA_MOOD = "extra_mood";

    private ActivityMoodDetailBinding binding;

    /** 当前展示的记录 */
    private MoodRecord record;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMoodDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Object extra = getIntent().getSerializableExtra(EXTRA_MOOD);
        if (!(extra instanceof MoodRecord)) {
            CommonUtils.toast(this, "没有找到这条情绪记录");
            finish();
            return;
        }
        record = (MoodRecord) extra;

        bindData();
        setupListeners();
        requestAiAnalysis();
    }

    /**
     * 把记录内容绑定到界面。
     */
    private void bindData() {
        binding.tvScore.setText(record.getScore() + " 分");
        binding.tvMoodText.setText(record.getScoreText());

        String date = CommonUtils.safe(record.getDate(), "");
        binding.tvDate.setText(date + " " + DateUtils.weekday(date));

        String diary = record.getDiary();
        binding.tvDiary.setText(CommonUtils.isEmpty(diary) ? "这次没有写日记" : diary);

        bindTags();
    }

    /**
     * 用代码动态生成标签 Chip，避免在布局里写死。
     */
    private void bindTags() {
        binding.cgDetailTags.removeAllViews();
        List<String> tags = record.getTagList();
        for (String tag : tags) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setTextSize(11f);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.brand_primary_container)));
            chip.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            chip.setChipStrokeWidth(0f);
            binding.cgDetailTags.addView(chip);
        }
    }

    /**
     * 绑定按钮事件。
     */
    private void setupListeners() {
        binding.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, MoodActivity.class);
            intent.putExtra(EXTRA_MOOD, record);
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
                .setTitle("删除情绪记录")
                .setMessage("确定要删除这条情绪记录吗？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    HeartSyncDb.getInstance(this).deleteMood(record.getId());
                    CommonUtils.toast(this, "已删除");
                    finish();
                })
                .show();
    }

    /**
     * 请求大模型生成情绪分析。
     *
     * <p>优先调用大模型 API；若未配置 API Key 或调用失败，
     * 则降级到自建服务器的规则化情绪分析接口，保证功能链路完整。</p>
     */
    private void requestAiAnalysis() {
        binding.pbAi.setVisibility(View.VISIBLE);

        String prompt = "请分析以下情绪记录：\n"
                + "日期：" + CommonUtils.safe(record.getDate(), "--") + "\n"
                + "心情分值：" + record.getScore() + " 分（" + record.getScoreText() + "）\n"
                + "情绪标签：" + (CommonUtils.isEmpty(record.getTags()) ? "无" : record.getTags()) + "\n"
                + "情绪日记：" + (CommonUtils.isEmpty(record.getDiary()) ? "未填写" : record.getDiary());

        new AiClient(this).chat(AiClient.PROMPT_MOOD, prompt, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String content) {
                binding.pbAi.setVisibility(View.GONE);
                showAnalysis(content);
            }

            @Override
            public void onError(String message) {
                // 降级：改用服务端规则引擎
                requestServerAnalysis(message);
            }
        });
    }

    /**
     * 降级方案：调用服务端规则化情绪分析。
     */
    private void requestServerAnalysis(String aiError) {
        ServerApi.analyzeMood(this, record.getScore(), record.getTags(), record.getDiary(),
                new ServerApi.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String data) {
                        binding.pbAi.setVisibility(View.GONE);
                        showAnalysis(data);
                    }

                    @Override
                    public void onFailure(String error) {
                        binding.pbAi.setVisibility(View.GONE);
                        if ("NO_API_KEY".equals(aiError)) {
                            binding.tvAiSummary.setText(
                                    "尚未配置大模型 API Key，且未能连接分析服务器。\n"
                                            + "可在「我的 - 设置」中填写 API Key 后重试，"
                                            + "或启动配套服务（server 目录下 node server.js）。");
                        } else {
                            binding.tvAiSummary.setText("AI 分析暂不可用：" + aiError);
                        }
                    }
                });
    }

    /**
     * 解析并展示结构化的情绪分析结果，解析失败时直接展示原文。
     */
    private void showAnalysis(String content) {
        JsonUtils.MoodAnalysis analysis = JsonUtils.parseMoodAnalysis(content);
        if (!analysis.isValid()) {
            binding.tvAiSummary.setText(CommonUtils.safe(content, "暂无分析结果"));
            return;
        }
        binding.tvAiSummary.setText(CommonUtils.safe(analysis.summary, "暂无总结"));
        binding.tvAiCause.setText("可能原因：" + CommonUtils.safe(analysis.cause, "暂无"));
        binding.tvAiRisk.setText("风险等级：" + CommonUtils.safe(analysis.risk, "低"));
        binding.tvAiQuote.setText(CommonUtils.safe(analysis.quote, ""));

        binding.llSuggestions.removeAllViews();
        List<String> suggestions = analysis.suggestions;
        if (suggestions == null) {
            return;
        }
        for (String suggestion : suggestions) {
            TextView tv = new TextView(this);
            tv.setText("· " + suggestion);
            tv.setTextSize(13f);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = 8;
            tv.setLayoutParams(lp);
            binding.llSuggestions.addView(tv);
        }
    }
}
