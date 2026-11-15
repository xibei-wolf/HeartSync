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
import com.heartsync.app.activity.MoodActivity;
import com.heartsync.app.activity.MoodDetailActivity;
import com.heartsync.app.adapter.MoodAdapter;
import com.heartsync.app.databinding.FragmentMoodBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.MoodRecord;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 情绪记录 Fragment。
 *
 * <p>展示情绪记录列表与平均心情、标签种类统计，支持下拉刷新、新增与查看详情。</p>
 */
public class MoodFragment extends Fragment implements MainActivity.Refreshable {

    /** 下拉刷新结束前的模拟等待时间（毫秒）。 */
    private static final long REFRESH_DELAY = 600L;

    /** 统计窗口天数：平均心情按近 7 天计算。 */
    private static final int STAT_DAYS = 7;

    private FragmentMoodBinding binding;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 情绪记录列表适配器。 */
    private MoodAdapter adapter;

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

    public static MoodFragment newInstance() {
        return new MoodFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMoodBinding.inflate(inflater, container, false);
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
        binding.rvMood.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMood.setHasFixedSize(true);
        adapter = new MoodAdapter((record, position) -> openDetail(record));
        binding.rvMood.setAdapter(adapter);
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

        binding.fabAddMood.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            startActivity(new Intent(ctx, MoodActivity.class));
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
     * 加载情绪记录与统计数据。
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
        List<MoodRecord> list = db.queryMoodList();
        adapter.setData(list);

        // 平均心情（近 7 天）
        float avgScore = db.averageMoodScore(STAT_DAYS);
        if (avgScore > 0) {
            binding.tvAvgScore.setText(String.format(Locale.CHINA, "%.1f", avgScore));
        } else {
            binding.tvAvgScore.setText("--");
        }
        binding.tvRecordCount.setText(String.valueOf(list.size()));

        // 标签种类数：统计所有记录中不重复的标签
        Set<String> tags = new HashSet<>();
        for (MoodRecord record : list) {
            if (record != null) {
                tags.addAll(record.getTagList());
            }
        }
        binding.tvTagCount.setText(String.valueOf(tags.size()));

        // 空状态与列表互斥显示
        boolean empty = list.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvMood.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * 打开情绪详情页，通过 Intent 传递记录对象。
     */
    private void openDetail(MoodRecord record) {
        Context ctx = getContext();
        if (ctx == null || record == null) {
            return;
        }
        Intent intent = new Intent(ctx, MoodDetailActivity.class);
        intent.putExtra("extra_mood", record);
        startActivity(intent);
    }
}
