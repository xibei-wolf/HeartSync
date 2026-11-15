package com.heartsync.app.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivityFirstaidDetailBinding;
import com.heartsync.app.model.FirstAidItem;
import com.heartsync.app.util.CommonUtils;

import java.util.List;

/**
 * 急救知识详情页。
 *
 * <p>展示条目的紧急程度、症状表现、处理步骤与注意事项，
 * 并提供一键拨打 120 急救电话的入口。</p>
 *
 * <p>技术点对应：Activity 页面跳转与传值（Intent 传递 Serializable 对象）、
 * 动态创建界面控件。</p>
 */
public class FirstAidDetailActivity extends AppCompatActivity {

    /** Intent 传递急救条目的 key */
    public static final String EXTRA_AID = "extra_aid";

    /** 步骤序号徽章的尺寸（dp） */
    private static final int BADGE_SIZE_DP = 22;

    private ActivityFirstaidDetailBinding binding;

    /** 当前展示的条目 */
    private FirstAidItem item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFirstaidDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Object extra = getIntent().getSerializableExtra(EXTRA_AID);
        if (!(extra instanceof FirstAidItem)) {
            CommonUtils.toast(this, "没有找到这条急救知识");
            finish();
            return;
        }
        item = (FirstAidItem) extra;

        bindData();
        bindSteps();
        setupListeners();
    }

    /**
     * 把条目内容绑定到界面。
     */
    private void bindData() {
        binding.tvTitle.setText(CommonUtils.safe(item.getTitle(), "--"));
        binding.tvCategory.setText(CommonUtils.safe(item.getCategory(), "--"));

        binding.tvSeverity.setText(item.getSeverityText());
        binding.tvSeverity.setTextColor(
                ContextCompat.getColor(this, severityColorRes(item.getSeverity())));

        String symptom = CommonUtils.safe(item.getSymptom(), "");
        binding.tvSymptom.setText(symptom);
        binding.tvSymptomDetail.setText(symptom);

        String caution = item.getCaution();
        binding.tvCaution.setText(
                CommonUtils.isEmpty(caution) ? "无特别注意事项" : caution);
    }

    /**
     * 紧急程度对应的文字颜色：3 危急 / 2 中度 / 1 轻度。
     */
    private static int severityColorRes(int severity) {
        if (severity == 3) {
            return R.color.status_error;
        } else if (severity == 2) {
            return R.color.status_warning;
        }
        return R.color.status_success;
    }

    /**
     * 动态生成处理步骤列表，并隐藏布局中的静态兜底步骤。
     */
    private void bindSteps() {
        binding.llSteps.removeAllViews();

        List<String> steps = item.getSteps();
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                binding.llSteps.addView(createStepRow(i + 1, steps.get(i)));
            }
        }

        // 静态步骤只是兜底，动态步骤存在时不再显示，避免内容重复
        binding.stepRow1.setVisibility(View.GONE);
        binding.stepRow2.setVisibility(View.GONE);
        binding.stepRow3.setVisibility(View.GONE);
        binding.stepRow4.setVisibility(View.GONE);
    }

    /**
     * 创建一行「序号徽章 + 步骤文本」。
     */
    private View createStepRow(int number, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = 10;
        row.setLayoutParams(rowLp);

        int badgeSize = (int) (BADGE_SIZE_DP * getResources().getDisplayMetrics().density);
        TextView badge = new TextView(this);
        badge.setText(String.valueOf(number));
        badge.setTextSize(12f);
        badge.setTextColor(ContextCompat.getColor(this, R.color.white));
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(R.drawable.bg_circle_primary);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                badgeSize, badgeSize);
        row.addView(badge, badgeLp);

        TextView content = new TextView(this);
        content.setText(text);
        content.setTextSize(13f);
        content.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        contentLp.setMarginStart(10);
        row.addView(content, contentLp);

        return row;
    }

    /**
     * 绑定按钮事件。
     */
    private void setupListeners() {
        binding.btnCall120.setOnClickListener(v -> confirmCall());
    }

    /**
     * 拨打 120 前二次确认。
     */
    private void confirmCall() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("拨打急救电话")
                .setMessage("确认拨打 120 急救电话？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton("拨打", (dialog, which) -> call120())
                .show();
    }

    /**
     * 调起系统拨号盘拨打 120，使用 ACTION_DIAL 无需运行时权限。
     */
    private void call120() {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:120"));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            CommonUtils.toast(this, "未找到可用的拨号应用，请手动拨打 120");
        }
    }
}
