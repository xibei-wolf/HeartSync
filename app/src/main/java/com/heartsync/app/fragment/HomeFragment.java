package com.heartsync.app.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.heartsync.app.R;
import com.heartsync.app.activity.AiChatActivity;
import com.heartsync.app.activity.ExerciseActivity;
import com.heartsync.app.activity.FirstAidActivity;
import com.heartsync.app.activity.HealthReportActivity;
import com.heartsync.app.activity.MainActivity;
import com.heartsync.app.activity.MoodActivity;
import com.heartsync.app.activity.SettingsActivity;
import com.heartsync.app.activity.SleepActivity;
import com.heartsync.app.databinding.FragmentHomeBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.ExerciseRecord;
import com.heartsync.app.model.MoodRecord;
import com.heartsync.app.model.SleepRecord;
import com.heartsync.app.net.ServerApi;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;
import com.heartsync.app.util.HealthScore;
import com.heartsync.app.util.PrefUtils;

import java.util.List;
import java.util.Locale;

/**
 * 首页 Fragment。
 *
 * <p>展示问候语、今日睡眠/运动/心情概览、健康指数环形图与每日健康贴士，
 * 并提供 AI 助手、快捷记录、健康报告等入口。</p>
 */
public class HomeFragment extends Fragment implements MainActivity.Refreshable {

    /** 健康指数动画每帧间隔（毫秒），约 60 FPS。 */
    private static final long SCORE_FRAME_DELAY = 16L;

    /** 网络较慢时，兜底展示本地贴士的延迟时间。 */
    private static final long TIP_FALLBACK_DELAY = 1500L;

    /** 本地兜底的健康贴士，保证离线可用。 */
    private static final String[] LOCAL_TIPS = {
            "今天也要好好照顾自己，规律作息是健康的第一步。",
            "课间起来走动 5 分钟，让大脑和眼睛都休息一下。",
            "每天喝够 1500 毫升水，身体会感谢你的。",
            "睡前半小时放下手机，入睡质量会明显提升。",
            "情绪低落时试着写下来，写完心里会轻松很多。",
            "每天运动 30 分钟，焦虑和低落都会减少。",
            "和信任的朋友聊聊天，连接是最好的治愈。"
    };

    private FragmentHomeBinding binding;

    /** 主线程 Handler，用于健康指数动画与贴士兜底。 */
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 健康指数动画任务。 */
    private final Runnable scoreRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            // 逐帧 +1 递增到目标分数
            if (currentScore < targetScore) {
                currentScore++;
                binding.ringHealth.setProgress(currentScore);
                binding.tvHealthScore.setText(String.valueOf(currentScore));
                handler.postDelayed(this, SCORE_FRAME_DELAY);
            } else {
                binding.ringHealth.setProgress(targetScore);
                binding.tvHealthScore.setText(String.valueOf(targetScore));
            }
        }
    };

    /** 贴士兜底任务，网络过慢时先展示本地内容。 */
    private final Runnable tipFallbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding != null && !tipLoaded) {
                binding.tvHealthTip.setText(randomLocalTip());
            }
        }
    };

    /** 目标健康指数。 */
    private int targetScore;

    /** 当前动画显示的分数。 */
    private int currentScore;

    /** 是否已经拿到服务器返回的贴士。 */
    private boolean tipLoaded;

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onDestroyView() {
        // 释放动画与延迟任务，避免内存泄漏和空指针
        handler.removeCallbacks(scoreRunnable);
        handler.removeCallbacks(tipFallbackRunnable);
        binding = null;
        super.onDestroyView();
    }

    /**
     * 绑定所有点击事件。
     */
    private void setupListeners() {
        binding.flSettings.setOnClickListener(v -> openActivity(SettingsActivity.class));
        binding.cardAi.setOnClickListener(v -> openActivity(AiChatActivity.class));
        binding.cardReport.setOnClickListener(v -> openActivity(HealthReportActivity.class));

        binding.btnQuickSleep.setOnClickListener(v -> openActivity(SleepActivity.class));
        binding.btnQuickExercise.setOnClickListener(v -> openActivity(ExerciseActivity.class));
        binding.btnQuickMood.setOnClickListener(v -> openActivity(MoodActivity.class));
        binding.btnQuickAid.setOnClickListener(v -> openActivity(FirstAidActivity.class));

        // 三个数据卡片切换到对应的底部导航页
        binding.cardSleep.setOnClickListener(v -> selectTab(R.id.nav_sleep));
        binding.cardExercise.setOnClickListener(v -> selectTab(R.id.nav_exercise));
        binding.cardMood.setOnClickListener(v -> selectTab(R.id.nav_mood));
    }

    /**
     * 重新查询数据库并刷新首页数据。
     */
    @Override
    public void onRefresh() {
        refreshData();
    }

    /**
     * 加载并刷新首页数据。
     */
    private void refreshData() {
        if (binding == null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        // 问候语与用户信息
        binding.tvGreeting.setText(DateUtils.greeting());
        String nickname = PrefUtils.getNickname(ctx);
        String className = PrefUtils.getClassName(ctx);
        if (CommonUtils.isEmpty(className)) {
            binding.tvUserName.setText(nickname);
        } else {
            binding.tvUserName.setText(nickname + " · " + className);
        }

        HeartSyncDb db = HeartSyncDb.getInstance(ctx);
        List<SleepRecord> sleeps = db.querySleepList();
        List<ExerciseRecord> exercises = db.queryExerciseList();
        List<MoodRecord> moods = db.queryMoodList();

        // 今日数据概览
        binding.tvSleepValue.setText(todaySleepText(sleeps));
        binding.tvExerciseValue.setText(todayExerciseText(exercises));
        binding.tvMoodValue.setText(todayMoodText(moods));

        // 健康指数
        int score = HealthScore.calculate(sleeps, exercises, moods);
        binding.tvHealthComment.setText(HealthScore.comment(score));
        animateScore(score);

        // 每日贴士
        loadDailyTip();
    }

    /**
     * 今日睡眠时长文案，例如「7.5h」。无记录返回「--」。
     */
    private String todaySleepText(List<SleepRecord> sleeps) {
        String today = DateUtils.today();
        int minutes = 0;
        boolean found = false;
        for (SleepRecord record : sleeps) {
            if (record != null && today.equals(CommonUtils.safe(record.getDate(), ""))) {
                minutes += record.getDurationMinutes();
                found = true;
            }
        }
        if (!found) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.1fh", minutes / 60f);
    }

    /**
     * 今日运动时长文案，例如「45 分」。无记录返回「--」。
     */
    private String todayExerciseText(List<ExerciseRecord> exercises) {
        String today = DateUtils.today();
        int minutes = 0;
        boolean found = false;
        for (ExerciseRecord record : exercises) {
            if (record != null && today.equals(CommonUtils.safe(record.getDate(), ""))) {
                minutes += record.getDurationMinutes();
                found = true;
            }
        }
        if (!found) {
            return "--";
        }
        return minutes + " 分";
    }

    /**
     * 今日心情分值文案，例如「4/5」。无记录返回「--」。
     */
    private String todayMoodText(List<MoodRecord> moods) {
        String today = DateUtils.today();
        for (MoodRecord record : moods) {
            if (record != null && today.equals(CommonUtils.safe(record.getDate(), ""))) {
                return record.getScore() + "/5";
            }
        }
        return "--";
    }

    /**
     * 以逐帧递增的方式把健康指数动画展示出来。
     *
     * <p>通过 Handler 消息机制每隔 16 毫秒发送一次任务，
     * 直至当前分数达到目标分数，视觉上形成环形进度增长动画。</p>
     */
    private void animateScore(int score) {
        handler.removeCallbacks(scoreRunnable);
        targetScore = Math.max(0, Math.min(100, score));
        currentScore = 0;
        if (binding == null) {
            return;
        }
        binding.ringHealth.setProgress(0);
        binding.tvHealthScore.setText("0");
        handler.post(scoreRunnable);
    }

    /**
     * 请求服务器返回每日健康贴士，失败时降级为本地内容。
     */
    private void loadDailyTip() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        tipLoaded = false;
        handler.removeCallbacks(tipFallbackRunnable);
        // 网络较慢时先展示本地贴士，保证界面不空着
        handler.postDelayed(tipFallbackRunnable, TIP_FALLBACK_DELAY);

        ServerApi.fetchDailyTip(ctx, new ServerApi.ApiCallback<String>() {
            @Override
            public void onSuccess(String data) {
                if (binding == null) {
                    return;
                }
                if (CommonUtils.isEmpty(data)) {
                    binding.tvHealthTip.setText(randomLocalTip());
                    return;
                }
                tipLoaded = true;
                handler.removeCallbacks(tipFallbackRunnable);
                binding.tvHealthTip.setText(data);
            }

            @Override
            public void onFailure(String message) {
                if (binding == null) {
                    return;
                }
                binding.tvHealthTip.setText(randomLocalTip());
            }
        });
    }

    /**
     * 随机取一条本地健康贴士。
     */
    private String randomLocalTip() {
        int index = (int) (System.currentTimeMillis() % LOCAL_TIPS.length);
        return LOCAL_TIPS[Math.abs(index)];
    }

    /**
     * 跳转到指定 Activity。
     */
    private void openActivity(Class<?> target) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        startActivity(new Intent(ctx, target));
    }

    /**
     * 切换底部导航页签。
     */
    private void selectTab(int menuId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).selectTab(menuId);
        }
    }
}
