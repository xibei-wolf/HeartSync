package com.heartsync.app.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.heartsync.app.net.HttpClient;
import com.heartsync.app.util.PrefUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 大模型 AI 客户端。
 *
 * <p>统一调用 OpenAI 兼容格式的 Chat Completions 接口，
 * 因此通义千问 / DeepSeek / 智谱 / Kimi 均可使用，只需在设置页切换
 * Base URL 与模型名即可。</p>
 *
 * <p>技术点对应：网络开发、JSON 数据解析、大模型 API 调用流程。</p>
 */
public class AiClient {

    /** 请求完成回调 */
    public interface AiCallback {
        /** 成功，返回模型生成的内容 */
        void onSuccess(String content);

        /** 失败 */
        void onError(String message);
    }

    /** 用于把回调切回主线程 */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context context;

    public AiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 发送一轮对话。
     *
     * @param systemPrompt 系统角色提示词，定义 AI 身份与输出格式
     * @param userPrompt   用户输入
     * @param callback     回调
     */
    public void chat(String systemPrompt, String userPrompt, final AiCallback callback) {
        if (!PrefUtils.hasApiKey(context)) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError("NO_API_KEY");
                }
            });
            return;
        }

        String base = PrefUtils.getApiBase(context);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/chat/completions";
        String body = buildRequestBody(systemPrompt, userPrompt);

        // OpenAI 兼容接口统一使用 Authorization: Bearer <api_key> 鉴权
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + PrefUtils.getApiKey(context));

        HttpClient.post(url, body, headers, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                String content = parseResponse(response);
                if (content == null) {
                    callback.onError("返回数据格式异常，请检查接口地址与模型名是否正确");
                } else {
                    callback.onSuccess(content);
                }
            }

            @Override
            public void onFailure(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * 组装符合 OpenAI 兼容格式的请求体 JSON。
     */
    private String buildRequestBody(String systemPrompt, String userPrompt) {
        try {
            JSONObject root = new JSONObject();
            root.put("model", PrefUtils.getModel(context));
            root.put("temperature", 0.8);

            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.put(sys);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userPrompt);
            messages.put(user);
            root.put("messages", messages);

            return root.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    /**
     * 解析 Chat Completions 返回结构，提取 choices[0].message.content。
     *
     * @return 模型输出内容；解析失败返回 null
     */
    public static String parseResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(response);
            // 部分厂商会返回 code/errorMessage 而非 HTTP 错误码
            if (root.has("error")) {
                JSONObject err = root.getJSONObject("error");
                String msg = err.optString("message", "调用失败");
                return null;
            }
            if (!root.has("choices") || root.getJSONArray("choices").length() == 0) {
                return null;
            }
            JSONObject first = root.getJSONArray("choices").getJSONObject(0);
            JSONObject message = first.getJSONObject("message");
            String content = message.optString("content", "");
            if (content.trim().isEmpty()) {
                return null;
            }
            return content.trim();
        } catch (JSONException e) {
            return null;
        }
    }

    /* ==================== 业务提示词 ==================== */

    /**
     * 情绪分析提示词：让模型按固定 JSON 结构返回，便于客户端解析后结构化展示。
     */
    public static final String PROMPT_MOOD = "你是「心晴校园」App 里的心理咨询助手，面向在校大学生。" +
            "请依据用户提供的情绪记录进行分析，并**严格**输出如下 JSON（不要输出任何额外文字、不要用代码块包裹）：\n" +
            "{\"summary\":\"不超过60字的情绪总结\",\"cause\":\"可能的心理成因分析\"," +
            "\"suggestions\":[\"建议1\",\"建议2\",\"建议3\"],\"risk\":\"风险等级，取值 低/中/高\"," +
            "\"quote\":\"一句温和的鼓励话语\"}\n\n" +
            "注意：suggestions 必须是长度 3 的字符串数组；语气温和专业，不做医学诊断，不使用吓人的措辞。";

    /**
     * 作息分析提示词。
     */
    public static final String PROMPT_SLEEP = "你是「心晴校园」App 里的作息顾问，面向在校大学生。" +
            "请依据用户提供的睡眠记录数据进行分析，并**严格**输出如下 JSON（不要输出任何额外文字、不要用代码块包裹）：\n" +
            "{\"summary\":\"不超过60字的作息总结\",\"problem\":\"最主要的作息问题\"," +
            "\"advice\":[\"建议1\",\"建议2\",\"建议3\"],\"score\":\"建议的健康评分，0到100的整数\"}\n\n" +
            "注意：所有建议要具体可执行，贴合学生日常作息场景。";

    /**
     * 通用对话提示词。
     */
    public static final String PROMPT_CHAT = "你是「心晴」——一款面向在校大学生的身心健康助手 App。" +
            "你的专长是：睡眠与作息分析、运动建议、情绪疏导、校园急救常识解答。" +
            "请用简洁、温暖、专业的中文回答，控制在 300 字以内；" +
            "涉及心理危机或严重伤病时，明确建议用户立即联系学校心理中心或拨打 120；" +
            "不要做医学诊断，不使用 Markdown 表格。";
}
