package com.heartsync.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ItemFirstaidBinding;
import com.heartsync.app.model.FirstAidItem;
import com.heartsync.app.util.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 急救知识列表适配器。
 * 绑定布局：item_firstaid.xml
 */
public class FirstAidAdapter extends RecyclerView.Adapter<FirstAidAdapter.ViewHolder> {

    /** 急救条目点击回调。 */
    public interface OnItemClickListener {
        void onItemClick(FirstAidItem item, int position);
    }

    private final List<FirstAidItem> data = new ArrayList<>();
    private final OnItemClickListener listener;

    public FirstAidAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    /** 整体替换数据。 */
    public void setData(List<FirstAidItem> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    /** 获取指定位置的条目，越界返回 null。 */
    @Nullable
    public FirstAidItem getItem(int position) {
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
        ItemFirstaidBinding binding = ItemFirstaidBinding.inflate(
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
            FirstAidItem item = data.get(pos);
            if (item != null && listener != null) {
                listener.onItemClick(item, pos);
            }
        });
    }

    /** 紧急程度对应的文字颜色：3 危急 / 2 中度 / 1 轻度。 */
    private static int severityColorRes(int severity) {
        if (severity == 3) {
            return R.color.status_error;
        } else if (severity == 2) {
            return R.color.status_warning;
        }
        return R.color.status_success;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final ItemFirstaidBinding binding;

        ViewHolder(ItemFirstaidBinding b) {
            super(b.getRoot());
            this.binding = b;
        }

        void bind(FirstAidItem item) {
            Context context = binding.getRoot().getContext();

            binding.tvCategory.setText(CommonUtils.safe(item.getCategory(), "--"));
            binding.tvTitle.setText(CommonUtils.safe(item.getTitle(), "--"));
            binding.tvSymptom.setText(CommonUtils.safe(item.getSymptom(), ""));

            binding.tvSeverity.setText("紧急程度：" + item.getSeverityText());
            binding.tvSeverity.setTextColor(
                    ContextCompat.getColor(context, severityColorRes(item.getSeverity())));

            List<String> steps = item.getSteps();
            int stepCount = steps == null ? 0 : steps.size();
            binding.tvStepCount.setText(stepCount + " 个处理步骤");
        }
    }
}
