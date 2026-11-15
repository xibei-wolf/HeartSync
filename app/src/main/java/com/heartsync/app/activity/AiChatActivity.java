package com.heartsync.app.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.heartsync.app.R;
import com.heartsync.app.adapter.ChatAdapter;
import com.heartsync.app.ai.AiClient;
import com.heartsync.app.databinding.ActivityAiChatBinding;
import com.heartsync.app.net.ServerApi;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 心晴助手对话页。
 *
 * <p>技术点对应：集成大模型 AI 功能（网络请求 + JSON 解析 + 结果展示）、
 * 列表组件（RecyclerView 聊天记录）、Handler 消息机制（打字机效果）。</p>
 */
public class AiChatActivity extends AppCompatActivity {

    private ActivityAiChatBinding binding;
    private ChatAdapter adapter;
    private AiClient aiClient;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 是否正在等待模型回复 */
    private boolean waiting = false;
    /** 当前待展示的完整回复 */
    private String pendingReply = "";
    /** 打字机动画的当前字符位置 */
    private int typingIndex = 0;

    private static final String ROLE_USER = "user";
    private static final String ROLE_AI = "ai";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        aiClient = new AiClient(this);

        setupList();
        setupInput();

        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivClear.setOnClickListener(v -> clearChat());

        // 首次进入展示欢迎语
        if (adapter.getItemCount() == 0) {
            addAiMessage(getString(R.string.ai_placeholder));
        }
    }

    /**
     * 初始化聊天列表。
     */
    private void setupList() {
        adapter = new ChatAdapter();
        binding.rvChat.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChat.setAdapter(adapter);
    }

    /**
     * 初始化输入区。
     */
    private void setupInput() {
        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.etInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isEnter) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    /**
     * 发送用户消息并请求 AI 回复。
     */
    private void sendMessage() {
        if (waiting) {
            CommonUtils.toast(this, "上一条消息还在回复中，请稍候");
            return;
        }
        final String text = binding.etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        binding.etInput.setText("");
        addUserMessage(text);

        // 构造发送给模型的完整上下文（带上最近几轮对话）
        StringBuilder prompt = new StringBuilder();
        List<ChatAdapter.ChatMessage> history = adapter.getMessages();
        int start = Math.max(0, history.size() - 8);
        for (int i = start; i < history.size(); i++) {
            ChatAdapter.ChatMessage m = history.get(i);
            if (ROLE_USER.equals(m.role)) {
                prompt.append("学生：").append(m.content).append("\n");
            } else {
                prompt.append("心晴：").append(m.content).append("\n");
            }
        }
        prompt.append("学生：").append(text).append("\n请直接回答学生最后的问题。");

        waiting = true;
        showTypingIndicator();

        aiClient.chat(AiClient.PROMPT_CHAT, prompt.toString(), new AiClient.AiCallback() {
            @Override
            public void onSuccess(String content) {
                waiting = false;
                removeTypingIndicator();
                pendingReply = content;
                typingIndex = 0;
                // Handler 消息机制：打字机式逐字展示
                handler.postDelayed(typingRunnable, 120);
            }

            @Override
            public void onError(String message) {
                waiting = false;
                removeTypingIndicator();
                if ("NO_API_KEY".equals(message)) {
                    // 未配置 API Key：降级到服务端代理（服务端未配 Key 时返回本地兜底回复）
                    requestServerChat(text);
                } else {
                    addAiMessage(getString(R.string.ai_error_request_failed, message));
                }
            }
        });
    }

    /**
     * 降级方案：调用业务服务器的 AI 代理接口。
     */
    private void requestServerChat(String userText) {
        waiting = true;
        showTypingIndicator();
        ServerApi.chat(this, userText, AiClient.PROMPT_CHAT, new ServerApi.ApiCallback<String>() {
            @Override
            public void onSuccess(String reply) {
                waiting = false;
                removeTypingIndicator();
                if (reply == null || reply.trim().isEmpty()) {
                    addAiMessage(getString(R.string.ai_error_no_key));
                    return;
                }
                pendingReply = reply;
                typingIndex = 0;
                handler.postDelayed(typingRunnable, 120);
            }

            @Override
            public void onFailure(String error) {
                waiting = false;
                removeTypingIndicator();
                addAiMessage(getString(R.string.ai_error_no_key));
            }
        });
    }

    /**
     * 打字机动画任务：每次展示一部分文字。
     */
    private final Runnable typingRunnable = new Runnable() {
        @Override
        public void run() {
            if (typingIndex >= pendingReply.length()) {
                return;
            }
            // 每帧展示 3 个字符，模拟逐字输出
            int end = Math.min(typingIndex + 3, pendingReply.length());
            String visible = pendingReply.substring(0, end);
            typingIndex = end;

            if (typingIndex >= pendingReply.length()) {
                // 展示完成，替换掉临时气泡
                removeLastAiPlaceholder();
                addAiMessage(pendingReply);
            } else {
                updateLastAiText(visible);
                handler.postDelayed(this, 40);
            }
        }
    };

    /**
     * 添加用户消息气泡。
     */
    private void addUserMessage(String text) {
        adapter.addMessage(new ChatAdapter.ChatMessage(ROLE_USER, text, now()));
        scrollToBottom();
    }

    /**
     * 添加 AI 消息气泡。
     */
    private void addAiMessage(String text) {
        adapter.addMessage(new ChatAdapter.ChatMessage(ROLE_AI, text, now()));
        scrollToBottom();
    }

    /**
     * 显示「正在思考」占位气泡。
     */
    private void showTypingIndicator() {
        adapter.addMessage(new ChatAdapter.ChatMessage(ROLE_AI, getString(R.string.ai_thinking), now()));
        scrollToBottom();
    }

    /**
     * 移除「正在思考」占位气泡。
     */
    private void removeTypingIndicator() {
        List<ChatAdapter.ChatMessage> messages = adapter.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        ChatAdapter.ChatMessage last = messages.get(messages.size() - 1);
        if (ROLE_AI.equals(last.role) && getString(R.string.ai_thinking).equals(last.content)) {
            adapter.removeLast();
        }
    }

    /**
     * 移除最后一个 AI 气泡（打字机临时气泡）。
     */
    private void removeLastAiPlaceholder() {
        List<ChatAdapter.ChatMessage> messages = adapter.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        ChatAdapter.ChatMessage last = messages.get(messages.size() - 1);
        if (ROLE_AI.equals(last.role)) {
            adapter.removeLast();
        }
    }

    /**
     * 更新最后一个 AI 气泡的文字。
     */
    private void updateLastAiText(String text) {
        adapter.updateLastAiText(text);
        scrollToBottom();
    }

    /**
     * 滚动到列表底部。
     */
    private void scrollToBottom() {
        binding.rvChat.post(new Runnable() {
            @Override
            public void run() {
                int count = adapter.getItemCount();
                if (count > 0) {
                    binding.rvChat.scrollToPosition(count - 1);
                }
            }
        });
    }

    /**
     * 清空对话。
     */
    private void clearChat() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("清空对话")
                .setMessage("确定要清空当前的所有对话记录吗？")
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_confirm, (dialog, which) -> {
                    adapter.clear();
                    addAiMessage(getString(R.string.ai_placeholder));
                })
                .show();
    }

    private String now() {
        return DateUtils.format(System.currentTimeMillis(), DateUtils.TIME_FORMAT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
