package com.heartsync.app.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 解析工具类。
 *
 * <p>技术点对应：JSON 数据解析。提供两类能力：</p>
 * <ol>
 *     <li>解析 AI 返回的「结构化 JSON 字符串」（模型常会包裹 ```json 代码块，需先清洗）；</li>
 *     <li>解析服务器下发的急救知识 JSON 数组。</li>
 * </ol>
 */
public class JsonUtils {

    /**
     * 从模型输出中提取 JSON 主体。
     * 兼容三种形式：纯 JSON、```json ... ``` 代码块、前后夹带说明文字。
     */
    public static String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        // 去除 Markdown 代码块围栏
        if (text.contains("```")) {
            int first = text.indexOf("```");
            int second = text.indexOf("```", first + 3);
            if (second > first) {
                text = text.substring(first + 3, second);
                if (text.startsWith("json")) {
                    text = text.substring(4);
                }
            }
        }
        text = text.trim();
        // 截取第一个 { 到最后一个 }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    /**
     * 解析 AI 情绪分析结果。
     */
    public static class MoodAnalysis {
        public String summary = "";
        public String cause = "";
        public List<String> suggestions = new ArrayList<>();
        public String risk = "低";
        public String quote = "";

        public boolean isValid() {
            return !summary.isEmpty() || !suggestions.isEmpty();
        }
    }

    /**
     * 解析 AI 情绪分析 JSON。
     */
    public static MoodAnalysis parseMoodAnalysis(String raw) {
        MoodAnalysis result = new MoodAnalysis();
        String json = extractJson(raw);
        if (json == null) {
            return result;
        }
        try {
            JSONObject obj = new JSONObject(json);
            result.summary = obj.optString("summary", "");
            result.cause = obj.optString("cause", "");
            result.risk = obj.optString("risk", "低");
            result.quote = obj.optString("quote", "");
            JSONArray arr = obj.optJSONArray("suggestions");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) {
                        result.suggestions.add(s);
                    }
                }
            }
        } catch (JSONException e) {
            // 解析失败时返回空结果，调用方会退化为展示原始文本
        }
        return result;
    }

    /**
     * AI 作息分析结果。
     */
    public static class SleepAnalysis {
        public String summary = "";
        public String problem = "";
        public List<String> advice = new ArrayList<>();
        public int score = 0;

        public boolean isValid() {
            return !summary.isEmpty() || !advice.isEmpty();
        }
    }

    /**
     * 解析 AI 作息分析 JSON。
     */
    public static SleepAnalysis parseSleepAnalysis(String raw) {
        SleepAnalysis result = new SleepAnalysis();
        String json = extractJson(raw);
        if (json == null) {
            return result;
        }
        try {
            JSONObject obj = new JSONObject(json);
            result.summary = obj.optString("summary", "");
            result.problem = obj.optString("problem", "");
            result.score = obj.optInt("score", 0);
            JSONArray arr = obj.optJSONArray("advice");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) {
                        result.advice.add(s);
                    }
                }
            }
        } catch (JSONException e) {
            // 同上
        }
        return result;
    }
}
