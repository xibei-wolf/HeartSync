package com.heartsync.app.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivityExerciseBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.ExerciseRecord;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;

import java.util.Locale;

/**
 * 运动记录新增 / 编辑页。
 *
 * <p>支持选择运动类型、时长、强度，自动估算热量消耗；编辑模式下会先删除旧记录
 * 再插入新记录（数据库层未提供运动记录的更新方法）。</p>
 *
 * <p>技术点对应：界面控件使用（ChipGroup / RadioGroup / TextWatcher）、
 * Activity 页面跳转与传值（Intent 传递 Serializable 对象）。</p>
 */
public class ExerciseActivity extends AppCompatActivity {

    /** Intent 传递运动记录的 key */
    public static final String EXTRA_EXERCISE = "extra_exercise";

    private ActivityExerciseBinding binding;

    /** 正在编辑的记录，新增时为 null */
    private ExerciseRecord editing;

    /** 运动类型对应的 Chip 控件，与 ExerciseRecord.TYPES 顺序一致 */
    private final Chip[] typeChips = new Chip[ExerciseRecord.TYPES.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExerciseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Object extra = getIntent().getSerializableExtra(EXTRA_EXERCISE);
        if (extra instanceof ExerciseRecord) {
            editing = (ExerciseRecord) extra;
        }

        bindTypeChips();
        setupListeners();

        if (editing != null) {
            fillForm(editing);
            binding.btnDelete.setVisibility(View.VISIBLE);
        }
        updateEstimate();
    }

    /**
     * 把运动类型 Chip 收集起来，便于统一处理选中状态。
     */
    private void bindTypeChips() {
        typeChips[0] = binding.chipType0;
        typeChips[1] = binding.chipType1;
        typeChips[2] = binding.chipType2;
        typeChips[3] = binding.chipType3;
        typeChips[4] = binding.chipType4;
        typeChips[5] = binding.chipType5;
        typeChips[6] = binding.chipType6;
        typeChips[7] = binding.chipType7;
        typeChips[8] = binding.chipType8;
        typeChips[9] = binding.chipType9;
        typeChips[10] = binding.chipType10;
        typeChips[11] = binding.chipType11;
    }

    /**
     * 绑定控件事件。
     */
    private void setupListeners() {
        binding.etDuration.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateEstimate();
            }
        });

        binding.rgIntensity.setOnCheckedChangeListener((group, checkedId) -> updateEstimate());

        binding.cgExerciseType.setOnCheckedChangeListener(
                (group, checkedId) -> updateEstimate());

        binding.btnSave.setOnClickListener(v -> save());
        binding.btnDelete.setOnClickListener(v -> confirmDelete());
    }

    /**
     * 用已有记录填充表单。
     */
    private void fillForm(ExerciseRecord record) {
        checkType(record.getType());
        binding.etDuration.setText(String.valueOf(record.getDurationMinutes()));
        binding.etCalories.setText(String.valueOf(record.getCalories()));
        binding.etNote.setText(CommonUtils.safe(record.getNote(), ""));
        switch (record.getIntensity()) {
            case 1:
                binding.rbIntensity1.setChecked(true);
                break;
            case 3:
                binding.rbIntensity3.setChecked(true);
                break;
            default:
                binding.rbIntensity2.setChecked(true);
                break;
        }
    }

    /**
     * 勾选指定运动类型对应的 Chip。
     */
    private void checkType(String type) {
        for (int i = 0; i < typeChips.length; i++) {
            if (typeChips[i] != null && ExerciseRecord.TYPES[i].equals(type)) {
                typeChips[i].setChecked(true);
                return;
            }
        }
    }

    /**
     * 当前选中的运动类型名称。
     */
    private String currentType() {
        for (int i = 0; i < typeChips.length; i++) {
            if (typeChips[i] != null && typeChips[i].isChecked()) {
                return typeChips[i].getText().toString();
            }
        }
        return ExerciseRecord.TYPES[0];
    }

    /**
     * 当前选中的强度：1 轻松 2 适中 3 高强度。
     */
    private int currentIntensity() {
        int checkedId = binding.rgIntensity.getCheckedRadioButtonId();
        if (checkedId == R.id.rbIntensity1) {
            return 1;
        }
        if (checkedId == R.id.rbIntensity3) {
            return 3;
        }
        return 2;
    }

    /**
     * 解析时长输入。
     */
    private int currentDuration() {
        try {
            return Integer.parseInt(binding.etDuration.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 根据类型、时长与强度更新热量估算提示。
     */
    private void updateEstimate() {
        int minutes = currentDuration();
        if (minutes <= 0) {
            binding.tvEstimate.setText("留空将根据运动类型与时长自动估算消耗热量");
            return;
        }
        int estimate = ExerciseRecord.estimateCalories(currentType(), minutes, currentIntensity());
        binding.tvEstimate.setText(String.format(Locale.CHINA, "预计消耗约 %d 千卡", estimate));
    }

    /**
     * 保存记录。
     */
    private void save() {
        int duration = currentDuration();
        if (duration <= 0) {
            CommonUtils.toast(this, "请输入运动时长");
            return;
        }
        int intensity = currentIntensity();
        int estimate = ExerciseRecord.estimateCalories(currentType(), duration, intensity);

        int calories = estimate;
        try {
            int input = Integer.parseInt(binding.etCalories.getText().toString().trim());
            if (input > 0) {
                calories = input;
            }
        } catch (Exception ignored) {
            // 热量留空时使用自动估算值
        }

        ExerciseRecord record = new ExerciseRecord();
        record.setDate(DateUtils.today());
        record.setType(currentType());
        record.setDurationMinutes(duration);
        record.setIntensity(intensity);
        record.setCalories(calories);
        record.setNote(binding.etNote.getText().toString().trim());
        record.setCreateTime(System.currentTimeMillis());

        HeartSyncDb db = HeartSyncDb.getInstance(this);
        if (editing != null) {
            // 数据库层没有运动记录的更新方法，采用「先删后插」的方式覆盖
            db.deleteExercise(editing.getId());
        }
        db.insertExercise(record);

        CommonUtils.toast(this, "保存成功");
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
                .setTitle("删除运动记录")
                .setMessage("确定要删除这条运动记录吗？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    HeartSyncDb.getInstance(this).deleteExercise(editing.getId());
                    CommonUtils.toast(this, "已删除");
                    finish();
                })
                .show();
    }
}
