package com.heartsync.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivitySplashBinding;
import com.heartsync.app.util.PrefUtils;

import java.util.Random;

/**
 * 启动页。
 *
 * <p>技术点对应：Handler 消息机制。使用 Handler 发送延时消息控制
 * 启动页停留时长，并在停留期间轮播展示每日健康小贴士。</p>
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 2200L;
    /** 消息类型：切换小贴士 */
    private static final int MSG_TIP = 100;
    /** 消息类型：跳转主界面 */
    private static final int MSG_GO = 101;

    private ActivitySplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 本地小贴士池，用于启动页轮播 */
    private static final String[] TIPS = {
            "规律作息是大学生最划算的健康投资",
            "每天运动 30 分钟，胜过周末突击两小时",
            "情绪低落时，先动一动身体再想清楚",
            "睡前 1 小时放下手机，入睡会快很多",
            "喝够水，很多「头痛」其实是缺水",
            "久坐每小时起身活动 3 分钟",
            "向朋友倾诉本身就是一种心理治疗",
            "吃早餐不是必须，但别空着肚子熬夜",
            "连续学习 45 分钟，就该休息 10 分钟",
            "心情不好时，先呼吸 4-7-8 试试"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 随机起始小贴士
        binding.tvSplashTip.setText(TIPS[new Random().nextInt(TIPS.length)]);

        // Handler 消息机制：周期性切换小贴士
        handler.postDelayed(tipRunnable, 700);

        // Handler 消息机制：延时跳转
        handler.postDelayed(goRunnable, SPLASH_DELAY);
    }

    private final Runnable tipRunnable = new Runnable() {
        @Override
        public void run() {
            binding.tvSplashTip.setText(TIPS[new Random().nextInt(TIPS.length)]);
            handler.postDelayed(this, 700);
        }
    };

    private final Runnable goRunnable = new Runnable() {
        @Override
        public void run() {
            goNext();
        }
    };

    /**
     * 根据登录状态跳转到登录页或主界面。
     */
    private void goNext() {
        handler.removeCallbacksAndMessages(null);
        Intent intent;
        if (PrefUtils.isLoggedIn(this)) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 防止内存泄漏与页面销毁后回调
        handler.removeCallbacksAndMessages(null);
    }
}
