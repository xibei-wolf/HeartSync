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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.heartsync.app.activity.ExerciseActivity;
import com.heartsync.app.activity.MainActivity;
import com.heartsync.app.adapter.ExerciseAdapter;
import com.heartsync.app.databinding.FragmentExerciseBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.ExerciseRecord;
import com.heartsync.app.util.CommonUtils;

import java.util.List;

/**
 * 运动记录 Fragment。
 *
 * <p>展示运动记录列表与累计统计，支持下拉刷新、新增、删除运动记录。</p>
 */
public class ExerciseFragment extends Fragment implements MainActivity.Refreshable {

    /** 下拉刷新结束前的模拟等待时间（毫秒）。 */
    private static final long REFRESH_DELAY = 600L;

    private FragmentExerciseBinding binding;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 运动记录列表适配器。 */
    private ExerciseAdapter adapter;

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

    public static ExerciseFragment newInstance() {
        return new ExerciseFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExerciseBinding.inflate(inflater, container, false);
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
        binding.rvExercise.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExercise.setHasFixedSize(true);
        adapter = new ExerciseAdapter(
                (record, position) -> openDetail(record),
                (record, position) -> confirmDelete(record, position));
        binding.rvExercise.setAdapter(adapter);
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

        binding.fabAddExercise.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            startActivity(new Intent(ctx, ExerciseActivity.class));
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
     * 加载运动记录与统计数据。
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
        List<ExerciseRecord> list = db.queryExerciseList();
        adapter.setData(list);

        // 统计概览：总时长、总消耗、运动天数（累计全部记录）
        int[] totals = db.exerciseTotalsAll();
        int totalMinutes = totals.length > 0 ? totals[0] : 0;
        int totalCalories = totals.length > 1 ? totals[1] : 0;
        int distinctDays = totals.length > 2 ? totals[2] : 0;
        binding.tvTotalMinutes.setText(totalMinutes + " 分钟");
        binding.tvTotalCalories.setText(totalCalories + " 千卡");
        binding.tvRecordCount.setText(distinctDays + " 天");

        // 空状态与列表互斥显示
        boolean empty = list.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvExercise.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * 打开运动记录编辑页，通过 Intent 传递记录对象。
     */
    private void openDetail(ExerciseRecord record) {
        Context ctx = getContext();
        if (ctx == null || record == null) {
            return;
        }
        CommonUtils.toast(ctx, CommonUtils.safe(record.getType(), "运动")
                + " · " + record.getDurationMinutes() + " 分钟");
        Intent intent = new Intent(ctx, ExerciseActivity.class);
        intent.putExtra("extra_exercise", record);
        startActivity(intent);
    }

    /**
     * 删除前二次确认，避免误触丢失数据。
     */
    private void confirmDelete(ExerciseRecord record, int position) {
        Context ctx = getContext();
        if (ctx == null || record == null) {
            return;
        }
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("删除运动记录")
                .setMessage("确定删除「" + CommonUtils.safe(record.getType(), "运动")
                        + "」这条记录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteRecord(record, position))
                .show();
    }

    /**
     * 删除一条运动记录并刷新界面。
     */
    private void deleteRecord(ExerciseRecord record, int position) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        HeartSyncDb.getInstance(ctx).deleteExercise(record.getId());
        if (adapter != null && position >= 0 && position < adapter.getItemCount()) {
            adapter.removeData(position);
        }
        CommonUtils.toast(ctx, "已删除该运动记录");
        loadData();
    }
}
