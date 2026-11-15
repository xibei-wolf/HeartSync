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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.heartsync.app.activity.MainActivity;
import com.heartsync.app.activity.SleepActivity;
import com.heartsync.app.activity.SleepDetailActivity;
import com.heartsync.app.adapter.SleepAdapter;
import com.heartsync.app.databinding.FragmentSleepBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.SleepRecord;

import java.util.List;
import java.util.Locale;

/**
 * 睡眠记录 Fragment。
 *
 * <p>展示睡眠记录列表与统计概览，支持下拉刷新、新增记录与查看详情。</p>
 */
public class SleepFragment extends Fragment implements MainActivity.Refreshable {

    /** 下拉刷新结束前的模拟等待时间（毫秒）。 */
    private static final long REFRESH_DELAY = 600L;

    /** 统计平均睡眠时长所参考的天数。 */
    private static final int STAT_DAYS = 7;

    private FragmentSleepBinding binding;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 睡眠记录列表适配器。 */
    private SleepAdapter adapter;

    /** 延迟结束下拉刷新的任务。 */
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            loadData();
            binding.swipeRefresh.setRefreshing(false);
        }
    };

    public static SleepFragment newInstance() {
        return new SleepFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSleepBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(refreshRunnable);
        binding = null;
        super.onDestroyView();
    }

    /**
     * 初始化列表。
     */
    private void setupRecyclerView() {
        binding.rvSleep.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSleep.setHasFixedSize(true);
        adapter = new SleepAdapter((record, position) -> openDetail(record));
        binding.rvSleep.setAdapter(adapter);
    }

    /**
     * 绑定点击事件。
     */
    private void setupListeners() {
        binding.swipeRefresh.setOnRefreshListener(() -> {
            // 通过 Handler 延迟结束刷新动画，模拟数据加载过程
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, REFRESH_DELAY);
        });

        binding.fabAddSleep.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            startActivity(new Intent(ctx, SleepActivity.class));
        });
    }

    /**
     * 重新查询数据库并刷新界面。
     */
    @Override
    public void onRefresh() {
        loadData();
    }

    /**
     * 加载睡眠记录与统计数据。
     */
    private void loadData() {
        if (binding == null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        HeartSyncDb db = HeartSyncDb.getInstance(ctx);
        List<SleepRecord> list = db.querySleepList();
        adapter.setData(list);

        // 统计概览
        int avgMinutes = db.averageSleepMinutes(STAT_DAYS);
        if (avgMinutes > 0) {
            binding.tvAvgDuration.setText(
                    String.format(Locale.CHINA, "%.1f 小时", avgMinutes / 60f));
        } else {
            binding.tvAvgDuration.setText("--");
        }

        float avgQuality = db.averageSleepQuality();
        if (avgQuality > 0) {
            binding.tvAvgQuality.setText(String.format(Locale.CHINA, "%.1f", avgQuality));
        } else {
            binding.tvAvgQuality.setText("--");
        }
        binding.tvRecordCount.setText(String.valueOf(list.size()));

        // 空状态与列表互斥显示
        boolean empty = list.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvSleep.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * 打开睡眠详情页，通过 Intent 传递记录对象。
     */
    private void openDetail(SleepRecord record) {
        Context ctx = getContext();
        if (ctx == null || record == null) {
            return;
        }
        Intent intent = new Intent(ctx, SleepDetailActivity.class);
        intent.putExtra("extra_sleep", record);
        startActivity(intent);
    }
}
