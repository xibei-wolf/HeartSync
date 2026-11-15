package com.heartsync.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ItemExerciseBinding;
import com.heartsync.app.model.ExerciseRecord;
import com.heartsync.app.util.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 运动记录列表适配器。
 * 绑定布局：item_exercise.xml
 */
public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.ViewHolder> {

    /** 运动记录条目点击回调。 */
    public interface OnItemClickListener {
        void onItemClick(ExerciseRecord record, int position);
    }

    /** 删除按钮点击回调。 */
    public interface OnDeleteListener {
        void onDelete(ExerciseRecord record, int position);
    }

    private final List<ExerciseRecord> data = new ArrayList<>();
    private final OnItemClickListener clickListener;
    private final OnDeleteListener deleteListener;

    public ExerciseAdapter(OnItemClickListener clickListener, OnDeleteListener deleteListener) {
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    /** 整体替换数据。 */
    public void setData(List<ExerciseRecord> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
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
    public ExerciseRecord getItem(int position) {
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
        ItemExerciseBinding binding = ItemExerciseBinding.inflate(
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
            ExerciseRecord record = data.get(pos);
            if (record != null && clickListener != null) {
                clickListener.onItemClick(record, pos);
            }
        });

        holder.binding.ivDelete.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            ExerciseRecord record = data.get(pos);
            if (record != null && deleteListener != null) {
                deleteListener.onDelete(record, pos);
            }
        });
    }

    /** 依据运动类型挑选对应的图标资源。 */
    private static int iconResOf(String type) {
        String t = CommonUtils.safe(type, "");
        switch (t) {
            case "篮球":
            case "羽毛球":
            case "足球":
            case "健身训练":
            case "跳绳":
                return R.drawable.ic_flame;
            case "瑜伽":
            case "拉伸放松":
                return R.drawable.ic_mood;
            case "跑步":
            case "快走":
            case "骑行":
            case "游泳":
            case "其他":
            default:
                return R.drawable.ic_run;
        }
    }

    /** 强度对应的文字颜色：1 轻松 / 2 适中 / 3 高强度。 */
    private static int intensityColorRes(int intensity) {
        if (intensity == 1) {
            return R.color.status_success;
        } else if (intensity == 2) {
            return R.color.status_info;
        }
        return R.color.accent_coral;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final ItemExerciseBinding binding;

        ViewHolder(ItemExerciseBinding b) {
            super(b.getRoot());
            this.binding = b;
        }

        void bind(ExerciseRecord record) {
            Context context = binding.getRoot().getContext();

            binding.tvType.setText(CommonUtils.safe(record.getType(), "--"));
            binding.tvDuration.setText(record.getDurationMinutes() + " 分钟");
            binding.tvCalories.setText(record.getCalories() + " 千卡");

            binding.tvIntensity.setText(record.getIntensityText());
            binding.tvIntensity.setTextColor(
                    ContextCompat.getColor(context, intensityColorRes(record.getIntensity())));

            binding.tvDate.setText(CommonUtils.safe(record.getDate(), "--"));

            binding.ivType.setImageResource(iconResOf(record.getType()));
            ImageViewCompat.setImageTintList(binding.ivType, ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.accent_coral)));
        }
    }
}
