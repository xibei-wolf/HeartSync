package com.heartsync.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.heartsync.app.BuildConfig;
import com.heartsync.app.model.User;

/**
 * 全局配置工具：基于 SharedPreferences 存储用户信息与各项配置。
 *
 * <p>技术点对应：数据存储（SharedPreferences 轻量键值存储）。</p>
 */
public class PrefUtils {

    private static final String PREF_NAME = "heartsync_prefs";

    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_CLASS_NAME = "class_name";
    private static final String KEY_AVATAR = "avatar_color";
    private static final String KEY_LOGGED_IN = "logged_in";

    private static final String KEY_API_KEY = "ai_api_key";
    private static final String KEY_API_BASE = "ai_api_base";
    private static final String KEY_MODEL = "ai_model";
    private static final String KEY_SERVER_BASE = "server_base";
    private static final String KEY_AI_ENABLED = "ai_enabled";

    /** 公开大模型 API 预设 */
    public static final String PRESET_QWEN = "通义千问 Qwen";
    public static final String PRESET_DEEPSEEK = "DeepSeek";
    public static final String PRESET_ZHIPU = "智谱 GLM";
    public static final String PRESET_MOONSHOT = "月之暗面 Kimi";

    public static final String BASE_QWEN = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final String BASE_DEEPSEEK = "https://api.deepseek.com/v1";
    public static final String BASE_ZHIPU = "https://open.bigmodel.cn/api/paas/v4";
    public static final String BASE_MOONSHOT = "https://api.moonshot.cn/v1";

    private PrefUtils() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /* ==================== 用户 ==================== */

    public static void saveLogin(Context context, User user) {
        sp(context).edit()
                .putLong(KEY_USER_ID, user.getId())
                .putString(KEY_ACCOUNT, user.getAccount())
                .putString(KEY_NICKNAME, user.getNickname())
                .putString(KEY_CLASS_NAME, user.getClassName())
                .putInt(KEY_AVATAR, user.getAvatarColor())
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();
    }

    public static void updateUserInfo(Context context, User user) {
        sp(context).edit()
                .putString(KEY_NICKNAME, user.getNickname())
                .putString(KEY_CLASS_NAME, user.getClassName())
                .putInt(KEY_AVATAR, user.getAvatarColor())
                .apply();
    }

    public static boolean isLoggedIn(Context context) {
        return sp(context).getBoolean(KEY_LOGGED_IN, false);
    }

    public static String getAccount(Context context) {
        return sp(context).getString(KEY_ACCOUNT, "");
    }

    public static String getNickname(Context context) {
        return sp(context).getString(KEY_NICKNAME, "同学");
    }

    public static String getClassName(Context context) {
        return sp(context).getString(KEY_CLASS_NAME, "");
    }

    public static int getAvatarColor(Context context) {
        return sp(context).getInt(KEY_AVATAR, 0);
    }

    public static void logout(Context context) {
        sp(context).edit().putBoolean(KEY_LOGGED_IN, false).apply();
    }

    /* ==================== AI 配置 ==================== */

    public static String getApiKey(Context context) {
        return sp(context).getString(KEY_API_KEY, "");
    }

    public static void setApiKey(Context context, String key) {
        sp(context).edit().putString(KEY_API_KEY, key == null ? "" : key).apply();
    }

    public static String getApiBase(Context context) {
        return sp(context).getString(KEY_API_BASE, BASE_QWEN);
    }

    public static void setApiBase(Context context, String base) {
        sp(context).edit().putString(KEY_API_BASE, base).apply();
    }

    public static String getModel(Context context) {
        return sp(context).getString(KEY_MODEL, "qwen-plus");
    }

    public static void setModel(Context context, String model) {
        sp(context).edit().putString(KEY_MODEL, model).apply();
    }

    /**
     * 依据服务商预设写入接口地址与默认模型。
     */
    public static void applyPreset(Context context, String preset) {
        SharedPreferences.Editor editor = sp(context).edit();
        switch (preset) {
            case PRESET_DEEPSEEK:
                editor.putString(KEY_API_BASE, BASE_DEEPSEEK).putString(KEY_MODEL, "deepseek-chat");
                break;
            case PRESET_ZHIPU:
                editor.putString(KEY_API_BASE, BASE_ZHIPU).putString(KEY_MODEL, "glm-4-flash");
                break;
            case PRESET_MOONSHOT:
                editor.putString(KEY_API_BASE, BASE_MOONSHOT).putString(KEY_MODEL, "moonshot-v1-8k");
                break;
            case PRESET_QWEN:
            default:
                editor.putString(KEY_API_BASE, BASE_QWEN).putString(KEY_MODEL, "qwen-plus");
                break;
        }
        editor.apply();
    }

    public static boolean hasApiKey(Context context) {
        String key = getApiKey(context);
        return key != null && !key.trim().isEmpty();
    }

    /* ==================== 服务器配置 ==================== */

    public static String getServerBase(Context context) {
        return sp(context).getString(KEY_SERVER_BASE, BuildConfig.DEFAULT_SERVER_BASE);
    }

    public static void setServerBase(Context context, String base) {
        sp(context).edit().putString(KEY_SERVER_BASE, base).apply();
    }

    /**
     * 拼接服务器接口完整地址。
     */
    public static String serverUrl(Context context, String path) {
        String base = getServerBase(context);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }
}
