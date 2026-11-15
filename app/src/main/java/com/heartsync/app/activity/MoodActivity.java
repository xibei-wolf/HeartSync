package com.heartsync.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RatingBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivityMoodBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.MoodRecord;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 情绪记录新增 / 编辑页。
 *
 * <p>选择心情分值与情绪标签、填写情绪日记后保存；保存成功后自动跳转到情绪详情页，
 * 让用户立刻看到大模型生成的情绪分析。</p>
 *
 * <p>技术点对应：界面控件使用（RatingBar / ChipGroup）、Activity 页面跳转与传值、
 * SQLite 数据持久化。</p>
 */
public class MoodActivity extends AppCompatActivity {

    /** Intent 传递情绪记录的 key */
    public static final String EXTRA_MOOD = "extra_mood";

    private ActivityMoodBinding binding;

    /** 正在编辑的记录，新增时为 null */
    private MoodRecord editing;

    /** 情绪标签对应的 Chip 控件，与 MoodRecord.TAGS 顺序一致 */
    private final Chip[] tagChips = new Chip[MoodRecord.TAGS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMoodBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Object extra = getIntent().getSerializableExtra(EXTRA_MOOD);
        if (extra instanceof MoodRecord) {
            editing = (MoodRecord) extra;
        }

        bindTagChips();
        setupListeners();

        if (editing != null) {
            fillForm(editing);
            binding.btnDelete.setVisibility(View.VISIBLE);
        }
        updateMoodText(binding.rbMoodScore.getRating());
    }

    /**
     * 把情绪标签 Chip 收集起来，便于统一处理选中状态。
     */
    private void bindTagChips() {
        tagChips[0] = binding.chipTag0;
        tagChips[1] = binding.chipTag1;
        tagChips[2] = binding.chipTag2;
        tagChips[3] = binding.chipTag3;
        tagChips[4] = binding.chipTag4;
        tagChips[5] = binding.chipTag5;
        tagChips[6] = binding.chipTag6;
        tagChips[7] = binding.chipTag7;
        tagChips[8] = binding.chipTag8;
        tagChips[9] = binding.chipTag9;
        tagChips[10] = binding.chipTag10;
        tagChips[11] = binding.chipTag11;
    }

    /**
     * 绑定控件事件。
     */
    private void setupListeners() {
        binding.rbMoodScore.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                updateMoodText(rating);
            }
        });

        binding.btnSave.setOnClickListener(v -> save());
        binding.btnDelete.setOnClickListener(v -> confirmDelete());
    }

    /**
     * 用已有记录填充表单。
     */
    private void fillForm(MoodRecord record) {
        binding.rbMoodScore.setRating(record.getScore());
        binding.etDiary.setText(CommonUtils.safe(record.getDiary(), ""));

        List<String> tags = record.getTagList();
        for (int i = 0; i < tagChips.length; i++) {
            if (tagChips[i] != null && tags.contains(MoodRecord.TAGS[i])) {
                tagChips[i].setChecked(true);
            }
        }
    }

    /**
     * 更新心情分值对应的文案。
     */
    private void updateMoodText(float rating) {
        MoodRecord temp = new MoodRecord();
        temp.setScore(Math.round(rating));
        binding.tvMoodText.setText(temp.getScoreText());
    }

    /**
     * 收集所有被勾选的标签文本。
     */
    private String collectTags() {
        List<String> selected = new ArrayList<>();
        ChipGroup group = binding.cgMoodTags;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                selected.add(((Chip) child).getText().toString());
            }
        }
        return String.join(",", selected);
    }

    /**
     * 保存记录，并跳转到详情页查看 AI 分析。
     */
    private void save() {
        MoodRecord record = new MoodRecord();
        record.setDate(DateUtils.today());
        record.setScore(Math.round(binding.rbMoodScore.getRating()));
        record.setTags(collectTags());
        record.setDiary(binding.etDiary.getText().toString().trim());
        record.setCreateTime(System.currentTimeMillis());

        HeartSyncDb db = HeartSyncDb.getInstance(this);
        if (editing != null) {
            record.setId(editing.getId());
            db.updateMood(record);
        } else {
            db.insertMood(record);
        }

        CommonUtils.toast(this, "记录成功");

        Intent intent = new Intent(this, MoodDetailActivity.class);
        intent.putExtra(EXTRA_MOOD, record);
        startActivity(intent);
        finish();
    }

    /**
     * 删除记录。
     */
    private void confirmDelete() {
        if (editing == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除情绪记录")
                .setMessage("确定要删除这条情绪记录吗？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    HeartSyncDb.getInstance(this).deleteMood(editing.getId());
                    CommonUtils.toast(this, "已删除");
                    finish();
                })
                .show();
    }
}
