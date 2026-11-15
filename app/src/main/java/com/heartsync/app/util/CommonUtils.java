package com.heartsync.app.util;

import android.content.Context;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 通用工具类：Toast 提示、密码哈希等。
 */
public class CommonUtils {

    /**
     * 统一 Toast 提示。
     */
    public static void toast(Context context, String msg) {
        if (context == null || msg == null) {
            return;
        }
        Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * SHA-256 哈希。用于密码存储，避免明文落盘。
     */
    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return text;
        }
    }

    /**
     * 判断字符串是否为空白。
     */
    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 安全地取字符串，避免空指针。
     */
    public static String safe(String s, String def) {
        return s == null ? def : s;
    }
}
