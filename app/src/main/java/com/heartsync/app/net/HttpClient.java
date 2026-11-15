package com.heartsync.app.net;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网络请求封装：基于 HttpURLConnection。
 *
 * <p>技术点对应：网络开发。所有请求均在子线程执行，
 * 通过 Handler 回到主线程回调，避免阻塞 UI。</p>
 */
public class HttpClient {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /** 网络请求成功回调 */
    public interface Callback {
        void onSuccess(String response);

        void onFailure(String error);
    }

    /**
     * 发起 GET 请求。
     */
    public static void get(final String url, final Callback callback) {
        request(url, "GET", null, null, callback);
    }

    /**
     * 发起 POST 请求，body 为 JSON 字符串。
     */
    public static void post(String url, String body, Callback callback) {
        request(url, "POST", body, null, callback);
    }

    /**
     * 发起带自定义请求头的 POST 请求。
     *
     * <p>大模型 API 需要携带 {@code Authorization: Bearer <key>} 鉴权头，
     * 因此在原有 POST 基础上扩展出带 header 的重载。</p>
     *
     * @param url    请求地址
     * @param body   JSON 请求体
     * @param header 额外请求头，key 为头名称，value 为头值；可为 null
     * @param callback 回调
     */
    public static void post(String url, String body, java.util.Map<String, String> header,
                            Callback callback) {
        request(url, "POST", body, header, callback);
    }

    private static void request(final String url, final String method, final String body,
                                final java.util.Map<String, String> header,
                                final Callback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String result = execute(url, method, body, header);
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onSuccess(result);
                            }
                        }
                    });
                } catch (final Exception e) {
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onFailure(describe(e));
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 同步执行一次 HTTP 请求（在工作线程中调用）。
     */
    private static String execute(String urlStr, String method, String body,
                                  java.util.Map<String, String> header) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Charset", "UTF-8");

            // 自定义请求头（如大模型接口的 Authorization 鉴权头）
            if (header != null && !header.isEmpty()) {
                for (java.util.Map.Entry<String, String> entry : header.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }

            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] data = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(data.length);
                OutputStream os = conn.getOutputStream();
                os.write(data);
                os.flush();
                os.close();
            }

            int code = conn.getResponseCode();
            InputStream is;
            if (code >= 200 && code < 300) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }
            String text = readStream(is);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " : " + shorten(text));
            }
            return text;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 把异常转换成用户可读的错误信息。
     */
    private static String describe(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) {
            return "请求超时，请检查网络或稍后重试";
        }
        if (e instanceof java.net.UnknownHostException) {
            return "无法解析服务器地址，请检查网络设置";
        }
        if (e instanceof java.net.ConnectException) {
            return "无法连接到服务器，请确认服务已启动";
        }
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        // 大模型接口鉴权失败：给出可操作的提示，而不是裸露的 HTTP 状态码
        if (msg.startsWith("HTTP 401") || msg.startsWith("HTTP 403")) {
            return "大模型接口鉴权失败，请检查设置中的 API Key 是否正确";
        }
        if (msg.startsWith("HTTP 429")) {
            return "大模型接口调用过于频繁，请稍后重试";
        }
        return msg;
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
