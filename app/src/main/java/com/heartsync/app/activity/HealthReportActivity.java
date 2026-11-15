package com.heartsync.app.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.heartsync.app.R;
import com.heartsync.app.ai.AiClient;
import com.heartsync.app.databinding.ActivityHealthReportBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.ExerciseRecord;
import com.heartsync.app.model.MoodRecord;
import com.heartsync.app.model.SleepRecord;
import com.heartsync.app.util.DateUtils;
import com.heartsync.app.util.HealthScore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 健康报告页。
 *
 * <p>技术点对应：自定义 View 图表绘制（折线图 / 柱状图）、数据聚合统计、
 * 大模型 AI 综合建议。</p>
 */
public class HealthReportActivity extends AppCompatActivity {

    /** 统计窗口天数：概览指标按近 7 天计算。 */
    private static final int STAT_DAYS = 7;

    private ActivityHealthReportBinding binding;
    private HeartSyncDb db;

    /** 是否已经发起过一次 AI 建议请求，避免 onCreate 与 onResume 重复调用大模型。 */
    private boolean aiRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHealthReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = HeartSyncDb.getInstance(this);

        loadReport();
        binding.btnAskAi.setOnClickListener(v -> openAiChat());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReport();
    }

    /**
     * 加载并渲染健康报告。
     */
    private void loadReport() {
        List<SleepRecord> sleeps = db.querySleepList();
        List<ExerciseRecord> exercises = db.queryExerciseList();
        List<MoodRecord> moods = db.queryMoodList();

        // ===== 概览指标（按近 7 天统计）=====
        int avgMinutes = db.averageSleepMinutes(STAT_DAYS);
        binding.tvSleepAvg.setText(avgMinutes > 0
                ? String.format(Locale.CHINA, "%.1f 小时", avgMinutes / 60f)
                : "--");
        int[] totals = db.exerciseTotalsAll();
        binding.tvExerciseTotal.setText(totals[0] > 0 ? totals[0] + " 分钟" : "--");
        float avgMood = db.averageMoodScore(STAT_DAYS);
        binding.tvMoodAvg.setText(avgMood > 0
                ? String.format(Locale.CHINA, "%.1f 分", avgMood)
                : "--");

        // ===== 本周趋势 =====
        String[] weekDates = DateUtils.currentWeekDates();
        List<String> labels = new ArrayList<>();
        for (String date : weekDates) {
            labels.add(date.substring(5));
        }

        // 睡眠折线（单位小时）。当日无记录时传 NaN，折线在该点断开而非补 0
        List<float[]> sleepValues = new ArrayList<>();
        List<String> sleepLabels = new ArrayList<>();
        for (String date : weekDates) {
            SleepRecord record = findSleepByDate(sleeps, date);
            if (record != null) {
                sleepValues.add(new float[]{record.getDurationMinutes() / 60f});
                sleepLabels.add(String.format(Locale.CHINA, "%.1f", record.getDurationMinutes() / 60f));
            } else {
                sleepValues.add(new float[]{Float.NaN});
                sleepLabels.add("");
            }
        }
        binding.chartSleep.setData(labels, sleepValues, 10f, 0f, "h");
        binding.chartSleep.setValueLabels(sleepLabels);

        // 心情折线。同理，当日无记录时折线断开
        List<float[]> moodValues = new ArrayList<>();
        List<String> moodLabels = new ArrayList<>();
        for (String date : weekDates) {
            MoodRecord record = findMoodByDate(moods, date);
            if (record != null) {
                moodValues.add(new float[]{record.getScore()});
                moodLabels.add(String.valueOf(record.getScore()));
            } else {
                moodValues.add(new float[]{Float.NaN});
                moodLabels.add("");
            }
        }
        binding.chartMood.setData(labels, moodValues, 5f, 0f, "分");
        binding.chartMood.setValueLabels(moodLabels);

        // 运动柱状图
        List<Float> exerciseValues = new ArrayList<>();
        List<String> exerciseLabels = new ArrayList<>();
        for (String date : weekDates) {
            int minutes = 0;
            for (ExerciseRecord record : exercises) {
                if (date.equals(record.getDate())) {
                    minutes += record.getDurationMinutes();
                }
            }
            exerciseValues.add((float) minutes);
            exerciseLabels.add(minutes > 0 ? String.valueOf(minutes) : "");
        }
        binding.chartExercise.setData(labels, exerciseValues, exerciseLabels, 90f, "分");

        // ===== AI 综合建议 =====
        // 仅在首次进入时请求一次大模型：onCreate 与 onResume 都会调用 loadReport，
        // 若每次都请求会重复消耗接口额度，甚至触发 429 限流。
        if (!aiRequested) {
            aiRequested = true;
            binding.pbAi.setVisibility(android.view.View.VISIBLE);
            binding.tvAiAdvice.setText("正在为你生成综合健康建议…");
            requestAiAdvice(sleeps, exercises, moods);
        }
    }

    /**
     * 在列表中按日期查找睡眠记录。
     */
    private SleepRecord findSleepByDate(List<SleepRecord> list, String date) {
        for (SleepRecord record : list) {
            if (date.equals(record.getDate())) {
                return record;
            }
        }
        return null;
    }

    /**
     * 在列表中按日期查找情绪记录。
     */
    private MoodRecord findMoodByDate(List<MoodRecord> list, String date) {
        for (MoodRecord record : list) {
            if (date.equals(record.getDate())) {
                return record;
            }
        }
        return null;
    }

    /**
     * 请求 AI 生成综合健康建议。
     */
    private void requestAiAdvice(List<SleepRecord> sleeps, List<ExerciseRecord> exercises,
                                 List<MoodRecord> moods) {
        int score = HealthScore.calculate(sleeps, exercises, moods);
        int avgMinutes = db.averageSleepMinutes(STAT_DAYS);
        float avgQuality = db.averageSleepQuality();
        int[] totals = db.exerciseTotalsAll();
        float avgMood = db.averageMoodScore(STAT_DAYS);

        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下健康数据，给大学生一份简洁的综合健康建议（不超过 200 字，包含 2-3 条可执行建议）：\n");
        prompt.append("健康指数：").append(score).append(" 分（").append(HealthScore.comment(score)).append("）\n");
        prompt.append("平均睡眠时长：").append(avgMinutes).append(" 分钟，平均睡眠质量：")
                .append(String.format(Locale.CHINA, "%.1f", avgQuality)).append(" 分\n");
        prompt.append("累计运动：").append(totals[0]).append(" 分钟，消耗 ").append(totals[1])
                .append(" 千卡，运动天数 ").append(totals[2]).append(" 天\n");
        prompt.append("平均心情分：").append(String.format(Locale.CHINA, "%.1f", avgMood)).append(" / 5\n");

        new AiClient(this).chat(AiClient.PROMPT_CHAT, prompt.toString(), new AiClient.AiCallback() {
            @Override
            public void onSuccess(String content) {
                binding.pbAi.setVisibility(android.view.View.GONE);
                binding.tvAiAdvice.setText(content);
            }

            @Override
            public void onError(String message) {
                binding.pbAi.setVisibility(android.view.View.GONE);
                if ("NO_API_KEY".equals(message)) {
                    binding.tvAiAdvice.setText("尚未配置大模型 API Key，可在「我的 - 设置」中填写后再生成综合建议。\n\n"
                            + "当前健康指数为 " + score + " 分：" + HealthScore.comment(score));
                } else {
                    binding.tvAiAdvice.setText("AI 建议暂不可用：" + message + "\n\n"
                            + "当前健康指数为 " + score + " 分：" + HealthScore.comment(score));
                }
            }
        });
    }

    /**
     * 打开 AI 助手页面。
     */
    private void openAiChat() {
        Intent intent = new Intent(this, AiChatActivity.class);
        startActivity(intent);
    }
}
