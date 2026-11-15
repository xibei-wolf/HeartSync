package com.heartsync.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.heartsync.app.R;
import com.heartsync.app.databinding.ItemMoodBinding;
import com.heartsync.app.model.MoodRecord;
import com.heartsync.app.util.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 心情记录列表适配器。
 * 绑定布局：item_mood.xml
 */
public class MoodAdapter extends RecyclerView.Adapter<MoodAdapter.ViewHolder> {

    /** 心情记录条目点击回调。 */
    public interface OnItemClickListener {
        void onItemClick(MoodRecord record, int position);
    }

    /** 日记正文的最大显示长度，超出后截断。 */
    private static final int DIARY_MAX_LENGTH = 60;

    private final List<MoodRecord> data = new ArrayList<>();
    private final OnItemClickListener listener;

    public MoodAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    /** 整体替换数据。 */
    public void setData(List<MoodRecord> list) {
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
    public MoodRecord getItem(int position) {
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
        ItemMoodBinding binding = ItemMoodBinding.inflate(
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
            MoodRecord record = data.get(pos);
            if (record != null && listener != null) {
                listener.onItemClick(record, pos);
            }
        });
    }

    /** 依据心情分值返回对应的表情。 */
    private static String emojiOf(int score) {
        switch (score) {
            case 5:
                return "😄";
            case 4:
                return "🙂";
            case 3:
                return "😐";
            case 2:
                return "😕";
            case 1:
                return "😣";
            default:
                return "😐";
        }
    }

    /** 心情等级对应的文字颜色：2 好 / 1 中 / 0 差。 */
    private static int scoreColorRes(int level) {
        if (level == 2) {
            return R.color.mood_great;
        } else if (level == 1) {
            return R.color.mood_normal;
        }
        return R.color.mood_bad;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final ItemMoodBinding binding;

        ViewHolder(ItemMoodBinding b) {
            super(b.getRoot());
            this.binding = b;
        }

        void bind(MoodRecord record) {
            Context context = binding.getRoot().getContext();

            binding.tvMoodEmoji.setText(emojiOf(record.getScore()));

            binding.tvScoreText.setText(record.getScoreText());
            binding.tvScoreText.setTextColor(
                    ContextCompat.getColor(context, scoreColorRes(record.getScoreLevel())));

            binding.tvDate.setText(CommonUtils.safe(record.getDate(), "--"));

            bindTags(context, record.getTagList());

            String diary = record.getDiary();
            if (CommonUtils.isEmpty(diary)) {
                binding.tvDiary.setVisibility(View.GONE);
            } else {
                binding.tvDiary.setVisibility(View.VISIBLE);
                if (diary.length() > DIARY_MAX_LENGTH) {
                    binding.tvDiary.setText(diary.substring(0, DIARY_MAX_LENGTH) + "…");
                } else {
                    binding.tvDiary.setText(diary);
                }
            }
        }

        /** 用代码动态生成标签 Chip，避免在布局里写死。 */
        private void bindTags(Context context, List<String> tags) {
            binding.cgTags.removeAllViews();
            if (tags == null || tags.isEmpty()) {
                binding.cgTags.setVisibility(View.GONE);
                return;
            }
            binding.cgTags.setVisibility(View.VISIBLE);
            for (String tag : tags) {
                Chip chip = new Chip(context);
                chip.setText(tag);
                chip.setTextSize(10f);
                chip.setClickable(false);
                chip.setCheckable(false);
                chip.setChipBackgroundColor(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.bg_subtle)));
                chip.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                chip.setChipStrokeWidth(0f);
                binding.cgTags.addView(chip);
            }
        }
    }
}
