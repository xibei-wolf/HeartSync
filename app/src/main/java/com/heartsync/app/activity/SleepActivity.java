package com.heartsync.app.activity;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivitySleepBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.SleepRecord;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;

import java.util.Calendar;
import java.util.Locale;

/**
 * 睡眠记录新增 / 编辑页。
 *
 * <p>技术点对应：Activity 页面跳转与传值（通过 Intent 传递 SleepRecord 对象）、
 * 界面控件使用（DatePickerDialog / TimePickerDialog / SeekBar / RatingBar）。</p>
 */
public class SleepActivity extends AppCompatActivity {

    /** Intent 传递记录的 key */
    public static final String EXTRA_SLEEP = "extra_sleep";

    private ActivitySleepBinding binding;
    private HeartSyncDb db;
    /** 正在编辑的记录，新增时为 null */
    private SleepRecord editing;
    /** 当前选择的日期 */
    private String selectedDate;
    /** 入睡时间 */
    private String sleepStart = "23:00";
    /** 起床时间 */
    private String wakeTime = "07:00";
    /** 睡眠总时长（分钟） */
    private int durationMinutes = 480;
    /** 深睡时长（分钟） */
    private int deepMinutes = 96;

    /**
     * 创建「新增睡眠记录」的 Intent。
     */
    public static Intent newAddIntent(android.content.Context context) {
        return new Intent(context, SleepActivity.class);
    }

    /**
     * 创建「编辑睡眠记录」的 Intent，通过 Intent 传入记录对象。
     */
    public static Intent newEditIntent(android.content.Context context, SleepRecord record) {
        Intent intent = new Intent(context, SleepActivity.class);
        intent.putExtra(EXTRA_SLEEP, record);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySleepBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = HeartSyncDb.getInstance(this);

        // 接收 Intent 传入的记录，判断是新增还是编辑
        Object extra = getIntent().getSerializableExtra(EXTRA_SLEEP);
        if (extra instanceof SleepRecord) {
            editing = (SleepRecord) extra;
        }

        setTitle(editing == null ? R.string.sleep_add : R.string.common_edit);

        if (editing != null) {
            fillForm(editing);
            binding.btnDelete.setVisibility(View.VISIBLE);
        } else {
            selectedDate = DateUtils.today();
            binding.tvDate.setText(selectedDate + " " + DateUtils.weekday(selectedDate));
            // 界面显示的时间必须来自变量本身，避免出现"显示一个值、存下另一个值"的不一致
            binding.tvSleepStart.setText(sleepStart);
            binding.tvWakeTime.setText(wakeTime);
            updateDurationText();
            binding.tvDeepTime.setText(deepMinutes + " 分钟");
            binding.sbDeepTime.setMax(Math.max(30, durationMinutes / 2));
            binding.sbDeepTime.setProgress(deepMinutes);
        }

        // 绑定控件事件
        binding.tvDate.setOnClickListener(v -> showDatePicker());
        binding.tvSleepStart.setOnClickListener(v -> showTimePicker(true));
        binding.tvWakeTime.setOnClickListener(v -> showTimePicker(false));
        binding.sbDeepTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 深睡时长范围 0 ~ 总时长的一半
                int max = Math.max(30, durationMinutes / 2);
                deepMinutes = Math.min(progress, max);
                binding.tvDeepTime.setText(deepMinutes + " 分钟");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnDelete.setOnClickListener(v -> deleteRecord());
    }

    /**
     * 用已有记录填充表单。
     */
    private void fillForm(SleepRecord record) {
        selectedDate = record.getDate();
        binding.tvDate.setText(selectedDate + " " + DateUtils.weekday(selectedDate));
        sleepStart = record.getSleepStart();
        wakeTime = record.getWakeTime();
        binding.tvSleepStart.setText(sleepStart);
        binding.tvWakeTime.setText(wakeTime);
        durationMinutes = record.getDurationMinutes();
        deepMinutes = record.getDeepMinutes();
        binding.rbQuality.setRating(record.getQuality());
        if (record.getNote() != null) {
            binding.etNote.setText(record.getNote());
        }
        binding.sbDeepTime.setMax(Math.max(30, durationMinutes / 2));
        binding.sbDeepTime.setProgress(Math.min(deepMinutes, Math.max(30, durationMinutes / 2)));
        updateDurationText();
        binding.tvDeepTime.setText(deepMinutes + " 分钟");
    }

    /**
     * 日期选择对话框。
     */
    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        try {
            java.util.Date date = DateUtils.parse(selectedDate, DateUtils.DATE_FORMAT);
            calendar.setTime(date);
        } catch (Exception ignored) {
        }
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.CHINA, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            binding.tvDate.setText(selectedDate + " " + DateUtils.weekday(selectedDate));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * 时间选择对话框。isStart 为 true 表示选择入睡时间。
     */
    private void showTimePicker(boolean isStart) {
        String current = isStart ? sleepStart : wakeTime;
        int hour = 23;
        int minute = 0;
        try {
            String[] parts = current.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }
        new TimePickerDialog(this, (view, h, m) -> {
            String time = String.format(Locale.CHINA, "%02d:%02d", h, m);
            if (isStart) {
                sleepStart = time;
                binding.tvSleepStart.setText(time);
            } else {
                wakeTime = time;
                binding.tvWakeTime.setText(time);
            }
            // 时长随时间变化自动重算
            durationMinutes = DateUtils.minutesBetween(sleepStart, wakeTime);
            updateDurationText();
            binding.sbDeepTime.setMax(Math.max(30, durationMinutes / 2));
            if (deepMinutes > durationMinutes / 2) {
                deepMinutes = durationMinutes / 2;
                binding.sbDeepTime.setProgress(deepMinutes);
            }
            binding.tvDeepTime.setText(deepMinutes + " 分钟");
        }, hour, minute, true).show();
    }

    /**
     * 更新睡眠时长显示。
     */
    private void updateDurationText() {
        float hours = durationMinutes / 60f;
        binding.tvDuration.setText(String.format(Locale.CHINA, "%.1f 小时", hours));
    }

    /**
     * 保存记录。
     */
    private void save() {
        if (durationMinutes <= 0) {
            CommonUtils.toast(this, "请检查入睡与起床时间");
            return;
        }
        SleepRecord record = editing != null ? editing : new SleepRecord();
        record.setDate(selectedDate);
        record.setSleepStart(sleepStart);
        record.setWakeTime(wakeTime);
        record.setDurationMinutes(durationMinutes);
        record.setDeepMinutes(deepMinutes);
        record.setQuality((int) binding.rbQuality.getRating());
        record.setNote(binding.etNote.getText().toString().trim());
        record.setCreateTime(System.currentTimeMillis());

        if (editing != null) {
            db.updateSleep(record);
        } else {
            db.insertSleep(record);
        }
        CommonUtils.toast(this, getString(R.string.common_success));
        finish();
    }

    /**
     * 删除记录。
     */
    private void deleteRecord() {
        if (editing == null) {
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("删除睡眠记录")
                .setMessage("确定要删除这条睡眠记录吗？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    db.deleteSleep(editing.getId());
                    CommonUtils.toast(this, "已删除");
                    finish();
                })
                .show();
    }
}
