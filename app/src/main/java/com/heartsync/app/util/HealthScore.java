package com.heartsync.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.heartsync.app.model.ExerciseRecord;
import com.heartsync.app.model.MoodRecord;
import com.heartsync.app.model.SleepRecord;

import java.util.List;

/**
 * 健康指数计算器。
 *
 * <p>依据近期的睡眠、运动、情绪三类记录计算 0~100 的综合健康指数，
 * 作为首页数据看板的核心指标。评分规则在报告中「关键技术介绍」一节有对应说明。</p>
 */
public class HealthScore {

    /**
     * 计算健康指数。
     *
     * @return 0~100 的整数
     */
    public static int calculate(List<SleepRecord> sleeps,
                                List<ExerciseRecord> exercises,
                                List<MoodRecord> moods) {
        // 1. 睡眠维度（满分 40）：时长 7~8 小时最佳，质量 5 分最佳
        int sleepScore = 0;
        if (sleeps != null && !sleeps.isEmpty()) {
            int count = Math.min(sleeps.size(), 7);
            float totalMinutes = 0f;
            float totalQuality = 0f;
            for (int i = 0; i < count; i++) {
                totalMinutes += sleeps.get(i).getDurationMinutes();
                totalQuality += sleeps.get(i).getQuality();
            }
            float avgMinutes = totalMinutes / count;
            float avgQuality = totalQuality / count;

            // 时长得分：7~8 小时得满分，偏离越多扣分越多
            float durationScore;
            if (avgMinutes >= 420 && avgMinutes <= 480) {
                durationScore = 20f;
            } else if (avgMinutes < 420) {
                durationScore = 20f * Math.max(0, avgMinutes / 420f);
            } else {
                durationScore = 20f * Math.max(0, 1 - (avgMinutes - 480) / 240f);
            }
            // 质量得分：5 分 20 分，线性映射
            float qualityScore = (avgQuality / 5f) * 20f;
            sleepScore = (int) Math.round(Math.max(0, Math.min(40, durationScore + qualityScore)));
        }

        // 2. 运动维度（满分 30）：近 7 天累计 150 分钟为满分
        int exerciseScore = 0;
        if (exercises != null && !exercises.isEmpty()) {
            int count = Math.min(exercises.size(), 7);
            int totalMinutes = 0;
            for (int i = 0; i < count; i++) {
                totalMinutes += exercises.get(i).getDurationMinutes();
            }
            exerciseScore = (int) Math.round(Math.min(30f, totalMinutes / 150f * 30f));
        }

        // 3. 情绪维度（满分 30）：平均心情分 5 分制映射到 30 分
        int moodScore = 0;
        if (moods != null && !moods.isEmpty()) {
            int count = Math.min(moods.size(), 7);
            float total = 0f;
            for (int i = 0; i < count; i++) {
                total += moods.get(i).getScore();
            }
            float avg = total / count;
            moodScore = (int) Math.round(Math.min(30f, avg / 5f * 30f));
        }

        return Math.max(0, Math.min(100, sleepScore + exerciseScore + moodScore));
    }

    /**
     * 依据健康指数生成一句评价。
     */
    public static String comment(int score) {
        if (score >= 85) {
            return "状态很棒，继续保持！";
        } else if (score >= 70) {
            return "状态良好，注意补充睡眠。";
        } else if (score >= 50) {
            return "状态一般，建议增加运动或调整作息。";
        } else if (score > 0) {
            return "状态偏低，建议先从规律作息开始调整。";
        }
        return "还没有记录，先完成一次健康记录吧。";
    }
}
