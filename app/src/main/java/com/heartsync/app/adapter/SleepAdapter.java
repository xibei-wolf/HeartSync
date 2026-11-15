package com.heartsync.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ItemSleepBinding;
import com.heartsync.app.model.SleepRecord;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 睡眠记录列表适配器。
 * 绑定布局：item_sleep.xml
 */
public class SleepAdapter extends RecyclerView.Adapter<SleepAdapter.ViewHolder> {

    /** 睡眠记录条目点击回调。 */
    public interface OnItemClickListener {
        void onItemClick(SleepRecord record, int position);
    }

    private final List<SleepRecord> data = new ArrayList<>();
    private final OnItemClickListener listener;

    public SleepAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    /** 整体替换数据。 */
    public void setData(List<SleepRecord> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    /** 在列表头部插入一条记录。 */
    public void addData(SleepRecord r) {
        if (r == null) {
            return;
        }
        data.add(0, r);
        notifyItemInserted(0);
    }

    /** 删除指定位置的一条记录。 */
    public void removeData(int position) {
        if (position < 0 || position >= data.size()) {
            return;
        }
        data.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    /** 获取指定位置的记录，越界返回 null。 */
    @Nullable
    public SleepRecord getItem(int position) {
        if (position < 0 || position >= data.size()) {
            return null;
        }
        return data.get(position);
    }

    /** 清空所有数据。 */
    public void clear() {
        data.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSleepBinding binding = ItemSleepBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(data.get(position));

        holder.binding.cardItem.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            SleepRecord record = data.get(pos);
            if (record != null && listener != null) {
                listener.onItemClick(record, pos);
            }
        });
    }

    /** 质量等级对应的颜色资源：2 好 / 1 中 / 0 差。 */
    private static int qualityColorRes(int level) {
        if (level == 2) {
            return R.color.sleep_excellent;
        } else if (level == 1) {
            return R.color.sleep_fair;
        }
        return R.color.sleep_poor;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final ItemSleepBinding binding;

        ViewHolder(ItemSleepBinding b) {
            super(b.getRoot());
            this.binding = b;
        }

        void bind(SleepRecord record) {
            String date = CommonUtils.safe(record.getDate(), "");
            // 日期形如 2026-01-01，取从第 5 位开始的 MM-dd
            binding.tvDate.setText(date.length() > 5 ? date.substring(5) : "--");
            binding.tvWeekday.setText(DateUtils.weekday(date));

            binding.tvDuration.setText(record.getDurationText());

            int level = record.getQualityLevel();
            int colorRes = qualityColorRes(level);
            int color = ContextCompat.getColor(binding.getRoot().getContext(), colorRes);
            binding.tvQuality.setText(record.getQualityText());
            binding.tvQuality.setTextColor(color);
            binding.vBar.setBackgroundColor(color);

            binding.tvTimeRange.setText(
                    CommonUtils.safe(record.getSleepStart(), "--") + " - "
                            + CommonUtils.safe(record.getWakeTime(), "--"));

            int deep = record.getDeepMinutes();
            binding.tvDeepTime.setText(deep > 0 ? deep + " 分钟" : "--");

            String note = record.getNote();
            if (CommonUtils.isEmpty(note)) {
                binding.tvNote.setVisibility(View.GONE);
            } else {
                binding.tvNote.setVisibility(View.VISIBLE);
                binding.tvNote.setText(note);
            }
        }
    }
}
