package com.heartsync.app.net;

import android.content.Context;

import com.heartsync.app.model.FirstAidItem;
import com.heartsync.app.util.PrefUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务服务器接口封装。
 *
 * <p>访问课程自建的 Node.js 服务器，获取每日健康提示与急救知识库。
 * 网络请求失败时自动降级为本地 assets 数据，保证离线可用。</p>
 */
public class ServerApi {

    /** 接口回调 */
    public interface ApiCallback<T> {
        void onSuccess(T data);

        void onFailure(String message);
    }

    /**
     * 拉取每日健康提示。
     */
    public static void fetchDailyTip(Context context, final ApiCallback<String> callback) {
        String url = PrefUtils.serverUrl(context, "/api/tip");
        HttpClient.get(url, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    // 服务器统一封装：{"code":0,"message":"success","data":{"tip":...}}
                    JSONObject data = obj.optJSONObject("data");
                    String tip = data != null ? data.optString("tip", "") : "";
                    if (tip.isEmpty()) {
                        // 兼容旧版直接返回 {"tip":"..."} 的格式
                        tip = obj.optString("tip", "");
                    }
                    callback.onSuccess(tip);
                } catch (JSONException e) {
                    callback.onFailure("数据解析失败");
                }
            }

            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }

    /**
     * 拉取急救知识库。成功后回调列表，失败时回调 null 表示应使用本地数据。
     */
    public static void fetchFirstAid(Context context, final ApiCallback<List<FirstAidItem>> callback) {
        String url = PrefUtils.serverUrl(context, "/api/firstaid");
        HttpClient.get(url, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    List<FirstAidItem> list = parseFirstAidResponse(response);
                    callback.onSuccess(list);
                } catch (JSONException e) {
                    callback.onFailure("急救数据解析失败");
                }
            }

            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }

    /**
     * 请求服务端做规则化情绪分析。
     *
     * <p>用于大模型 API Key 未配置时的降级方案：服务端内置规则引擎，
     * 返回与大模型完全一致的 JSON 结构，保证功能链路完整可演示。</p>
     *
     * @param score    心情分值 1~5
     * @param tags     情绪标签，逗号分隔
     * @param diary    情绪日记
     * @param callback 回调，成功时返回可直接解析的 JSON 字符串
     */
    public static void analyzeMood(Context context, int score, String tags, String diary,
                                   final ApiCallback<String> callback) {
        String url = PrefUtils.serverUrl(context, "/api/mood");
        try {
            JSONObject body = new JSONObject();
            body.put("score", score);
            body.put("tags", tags == null ? "" : tags);
            body.put("diary", diary == null ? "" : diary);
            HttpClient.post(url, body.toString(), new HttpClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject root = new JSONObject(response);
                        JSONObject data = root.optJSONObject("data");
                        if (data == null) {
                            callback.onFailure("服务端返回数据为空");
                            return;
                        }
                        // 重新组装成与 AI 返回一致的 JSON，便于复用同一套解析逻辑
                        callback.onSuccess(data.toString());
                    } catch (JSONException e) {
                        callback.onFailure("情绪分析数据解析失败");
                    }
                }

                @Override
                public void onFailure(String error) {
                    callback.onFailure(error);
                }
            });
        } catch (JSONException e) {
            callback.onFailure("请求参数构造失败");
        }
    }

    /**
     * 请求服务端做一次对话（服务端代理大模型；未配置 Key 时返回本地兜底回复）。
     *
     * @param prompt   用户输入
     * @param system   系统提示词
     * @param callback 回调
     */
    public static void chat(Context context, String prompt, String system,
                            final ApiCallback<String> callback) {
        String url = PrefUtils.serverUrl(context, "/api/ai/chat");
        try {
            JSONObject body = new JSONObject();
            body.put("prompt", prompt == null ? "" : prompt);
            body.put("system", system == null ? "" : system);
            HttpClient.post(url, body.toString(), new HttpClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject root = new JSONObject(response);
                        JSONObject data = root.optJSONObject("data");
                        if (data == null) {
                            callback.onFailure("服务端返回数据为空");
                            return;
                        }
                        callback.onSuccess(data.optString("reply", ""));
                    } catch (JSONException e) {
                        callback.onFailure("对话数据解析失败");
                    }
                }

                @Override
                public void onFailure(String error) {
                    callback.onFailure(error);
                }
            });
        } catch (JSONException e) {
            callback.onFailure("请求参数构造失败");
        }
    }

    /**
     * 解析急救知识接口的响应。
     *
     * <p>服务器统一封装为
     * {@code {"code":0,"message":"success","data":{"total":10,"items":[...]}}}，
     * 同时兼容本地 assets 的 {@code {"items":[...]}} 与旧版直接返回数组的格式。</p>
     */
    public static List<FirstAidItem> parseFirstAidResponse(String response) throws JSONException {
        JSONArray arr = null;
        try {
            JSONObject obj = new JSONObject(response);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                arr = data.optJSONArray("items");
            }
            if (arr == null) {
                // 兼容本地 assets 的 {"items":[...]} 格式
                arr = obj.optJSONArray("items");
            }
        } catch (JSONException e) {
            // 不是对象结构，尝试按数组解析
            arr = null;
        }
        if (arr == null) {
            // 兼容旧版直接返回 JSON 数组的格式
            arr = new JSONArray(response);
        }
        return parseFirstAidArray(arr);
    }

    /**
     * 解析急救知识 JSON 数组为对象列表。
     */
    public static List<FirstAidItem> parseFirstAidArray(JSONArray arr) {
        List<FirstAidItem> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            FirstAidItem item = new FirstAidItem();
            item.setId(obj.optString("id", String.valueOf(i)));
            item.setCategory(obj.optString("category", "通用"));
            item.setTitle(obj.optString("title", ""));
            item.setSymptom(obj.optString("symptom", ""));
            item.setCaution(obj.optString("caution", ""));
            item.setSeverity(obj.optInt("severity", 1));
            List<String> steps = new ArrayList<>();
            JSONArray stepArr = obj.optJSONArray("steps");
            if (stepArr != null) {
                for (int j = 0; j < stepArr.length(); j++) {
                    String s = stepArr.optString(j, "").trim();
                    if (!s.isEmpty()) {
                        steps.add(s);
                    }
                }
            }
            item.setSteps(steps);
            list.add(item);
        }
        return list;
    }
}
