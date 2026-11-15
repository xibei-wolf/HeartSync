package com.heartsync.app.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.heartsync.app.R;
import com.heartsync.app.activity.AiChatActivity;
import com.heartsync.app.activity.FirstAidActivity;
import com.heartsync.app.activity.HealthReportActivity;
import com.heartsync.app.activity.LoginActivity;
import com.heartsync.app.activity.MainActivity;
import com.heartsync.app.activity.SettingsActivity;
import com.heartsync.app.databinding.FragmentMineBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.PrefUtils;

/**
 * 我的 Fragment。
 *
 * <p>展示当前登录用户的资料与常用功能入口，并提供清除本地缓存、退出登录等操作。</p>
 */
public class MineFragment extends Fragment implements MainActivity.Refreshable {

    /** 头像底色，按用户注册时保存的颜色下标依次选取。 */
    private static final int[] AVATAR_BACKGROUNDS = {
            R.drawable.bg_circle_soft_primary,
            R.drawable.bg_circle_coral,
            R.drawable.bg_circle_indigo,
            R.drawable.bg_circle_primary
    };

    private FragmentMineBinding binding;

    public static MineFragment newInstance() {
        return new MineFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMineBinding.inflate(inflater, container, false);
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
        refreshUserInfo();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /**
     * 绑定点击事件。
     */
    private void setupListeners() {
        binding.cardFirstAid.setOnClickListener(v -> openActivity(FirstAidActivity.class));
        binding.cardAiChat.setOnClickListener(v -> openActivity(AiChatActivity.class));
        binding.cardReport.setOnClickListener(v -> openActivity(HealthReportActivity.class));
        binding.cardSettings.setOnClickListener(v -> openActivity(SettingsActivity.class));

        binding.btnClearCache.setOnClickListener(v -> confirmClearCache());
        binding.btnLogout.setOnClickListener(v -> confirmLogout());
    }

    /**
     * 刷新用户资料。
     */
    @Override
    public void onRefresh() {
        refreshUserInfo();
    }

    /**
     * 读取本地保存的用户信息并刷新界面。
     */
    private void refreshUserInfo() {
        if (binding == null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        String nickname = PrefUtils.getNickname(ctx);
        String account = PrefUtils.getAccount(ctx);
        String className = PrefUtils.getClassName(ctx);

        binding.tvNickname.setText(nickname);
        binding.tvAccount.setText("学号 " + account);
        binding.tvClass.setText(className);

        // 头像文字取昵称首字
        if (!CommonUtils.isEmpty(nickname)) {
            binding.tvAvatarText.setText(nickname.substring(0, 1));
        } else {
            binding.tvAvatarText.setText("心");
        }

        // 头像底色随用户注册时保存的颜色下标变化
        int colorIndex = Math.abs(PrefUtils.getAvatarColor(ctx)) % AVATAR_BACKGROUNDS.length;
        binding.flAvatar.setBackgroundResource(AVATAR_BACKGROUNDS[colorIndex]);
    }

    /**
     * 清除本地缓存前二次确认。
     */
    private void confirmClearCache() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("清除本地数据")
                .setMessage("将删除全部睡眠、运动与心情记录，账号信息会保留。确定继续吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> clearCache(ctx))
                .show();
    }

    /**
     * 清空本地业务数据。
     */
    private void clearCache(Context ctx) {
        HeartSyncDb.getInstance(ctx).clearAllRecords();
        CommonUtils.toast(ctx, "已清除本地数据");
    }

    /**
     * 退出登录前二次确认。
     */
    private void confirmLogout() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (dialog, which) -> logout(ctx))
                .show();
    }

    /**
     * 退出登录并回到登录页。
     */
    private void logout(Context ctx) {
        PrefUtils.logout(ctx);
        Intent intent = new Intent(ctx, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(intent);
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
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
}
