package com.heartsync.app.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivityMainBinding;
import com.heartsync.app.fragment.ExerciseFragment;
import com.heartsync.app.fragment.HomeFragment;
import com.heartsync.app.fragment.MineFragment;
import com.heartsync.app.fragment.MoodFragment;
import com.heartsync.app.fragment.SleepFragment;

import java.util.HashMap;
import java.util.Map;

/**
 * 主界面：底部导航 + 5 个 Fragment。
 *
 * <p>技术点对应：常规界面布局、Fragment 页面切换。</p>
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    /** 缓存已创建的 Fragment，避免重复创建造成界面状态丢失 */
    private final Map<String, Fragment> fragmentMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showFragment(createFragment(item.getItemId()));
            return true;
        });

        // 默认显示首页
        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    /**
     * 显示指定 Fragment（替换容器内容）。
     */
    private void showFragment(Fragment fragment) {
        if (fragment == null) {
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    /**
     * 根据菜单项 ID 创建对应的 Fragment。
     */
    private Fragment createFragment(int itemId) {
        if (itemId == R.id.nav_sleep) {
            return getOrCreate(SleepFragment.newInstance());
        } else if (itemId == R.id.nav_exercise) {
            return getOrCreate(ExerciseFragment.newInstance());
        } else if (itemId == R.id.nav_mood) {
            return getOrCreate(MoodFragment.newInstance());
        } else if (itemId == R.id.nav_mine) {
            return getOrCreate(MineFragment.newInstance());
        }
        return getOrCreate(HomeFragment.newInstance());
    }

    /**
     * 从缓存中获取 Fragment，没有则创建。
     */
    private Fragment getOrCreate(Fragment fragment) {
        String tag = fragment.getClass().getSimpleName();
        Fragment cached = fragmentMap.get(tag);
        if (cached != null) {
            return cached;
        }
        fragmentMap.put(tag, fragment);
        return fragment;
    }

    /**
     * 切换到指定底部导航项，供 Fragment 调用。
     */
    public void selectTab(int menuId) {
        if (binding.bottomNav.getSelectedItemId() != menuId) {
            binding.bottomNav.setSelectedItemId(menuId);
        }
    }

    /**
     * 刷新当前页面数据（Fragment 返回时调用）。
     */
    public void refreshCurrent() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current != null && current instanceof Refreshable) {
            ((Refreshable) current).onRefresh();
        }
    }

    /**
     * 可刷新页面的接口。
     */
    public interface Refreshable {
        void onRefresh();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }
}
