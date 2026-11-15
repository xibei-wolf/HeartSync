package com.heartsync.app.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ItemChatAiBinding;
import com.heartsync.app.databinding.ItemChatUserBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 聊天记录适配器。
 * 用户气泡布局：item_chat_user.xml
 * AI 气泡布局：item_chat_ai.xml
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** 用户消息。 */
    public static final int TYPE_USER = 0;
    /** AI 消息。 */
    public static final int TYPE_AI = 1;

    /** 一条聊天消息。 */
    public static class ChatMessage {
        /** 角色："user" 或 "ai"。 */
        public final String role;
        /** 消息正文。 */
        public final String content;
        /** 发送时间，形如 "HH:mm"。 */
        public final String time;

        public ChatMessage(String role, String content, String time) {
            this.role = role;
            this.content = content;
            this.time = time;
        }
    }

    private final List<ChatMessage> messages = new ArrayList<>();

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        return "ai".equals(message.role) ? TYPE_AI : TYPE_USER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_AI) {
            ItemChatAiBinding binding = ItemChatAiBinding.inflate(inflater, parent, false);
            return new ViewHolder(binding.getRoot(), binding.tvText, binding.tvTime, binding.flAvatar);
        }
        ItemChatUserBinding binding = ItemChatUserBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding.getRoot(), binding.tvText, binding.tvTime, null);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ViewHolder vh = (ViewHolder) holder;
        ChatMessage message = messages.get(position);

        vh.tvText.setText(message.content == null ? "" : message.content);
        vh.tvTime.setText(message.time == null ? "" : message.time);

        if (vh.flAvatar != null) {
            int tint = ContextCompat.getColor(vh.flAvatar.getContext(), R.color.brand_primary);
            ImageView icon = findAvatarIcon(vh.flAvatar);
            if (icon != null) {
                ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tint));
            }
        }
    }

    /** 在头像容器中查找用于显示 AI 图标的 ImageView。 */
    @Nullable
    private static ImageView findAvatarIcon(View flAvatar) {
        if (flAvatar instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) flAvatar;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ImageView) {
                    return (ImageView) child;
                }
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    /** 追加一条消息。 */
    public void addMessage(ChatMessage m) {
        if (m == null) {
            return;
        }
        messages.add(m);
        notifyItemInserted(getItemCount() - 1);
    }

    /** 批量追加消息。 */
    public void addMessages(List<ChatMessage> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int start = messages.size();
        messages.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    /** 清空所有消息。 */
    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    /**
     * 删除最后一条消息。
     * 用于「正在思考」占位气泡的移除。
     */
    public void removeLast() {
        if (messages.isEmpty()) {
            return;
        }
        messages.remove(messages.size() - 1);
        notifyItemRemoved(getItemCount());
    }

    /**
     * 更新最后一条 AI 消息的文字内容。
     * 用于打字机动画的逐字刷新。
     */
    public void updateLastAiText(String text) {
        if (messages.isEmpty() || text == null) {
            return;
        }
        int last = messages.size() - 1;
        ChatMessage old = messages.get(last);
        if (!"ai".equals(old.role)) {
            return;
        }
        messages.set(last, new ChatMessage(old.role, text, old.time));
        notifyItemChanged(last);
    }

    /** 返回当前全部消息的副本。 */
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView tvText;
        final TextView tvTime;
        /** 仅 AI 消息有头像，用户消息为 null。 */
        @Nullable
        final View flAvatar;

        ViewHolder(View itemView, TextView tvText, TextView tvTime, @Nullable View flAvatar) {
            super(itemView);
            this.tvText = tvText;
            this.tvTime = tvTime;
            this.flAvatar = flAvatar;
        }
    }
}
