package com.heartsync.app.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日期时间工具类。
 */
public class DateUtils {

    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm";

    /**
     * 今天的日期字符串。
     */
    public static String today() {
        return format(new Date(), DATE_FORMAT);
    }

    public static String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(date);
    }

    public static String format(long millis, String pattern) {
        return format(new Date(millis), pattern);
    }

    public static Date parse(String dateStr, String pattern) {
        try {
            return new SimpleDateFormat(pattern, Locale.CHINA).parse(dateStr);
        } catch (Exception e) {
            return new Date();
        }
    }

    /**
     * 计算两个 HH:mm 之间的分钟差，跨零点自动处理（如 23:30 → 07:00）。
     */
    public static int minutesBetween(String start, String end) {
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) {
            return 0;
        }
        try {
            String[] s = start.split(":");
            String[] e = end.split(":");
            int startMin = Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]);
            int endMin = Integer.parseInt(e[0]) * 60 + Integer.parseInt(e[1]);
            int diff = endMin - startMin;
            if (diff <= 0) {
                diff += 24 * 60;
            }
            return diff;
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * 相对时间描述，例如「今天」「昨天」「3 天前」。
     */
    public static String relativeDay(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "";
        }
        try {
            Date d = parse(dateStr, DATE_FORMAT);
            Calendar a = Calendar.getInstance();
            a.setTime(d);
            a.set(Calendar.HOUR_OF_DAY, 0);
            a.set(Calendar.MINUTE, 0);
            a.set(Calendar.SECOND, 0);
            a.set(Calendar.MILLISECOND, 0);

            Calendar b = Calendar.getInstance();
            b.set(Calendar.HOUR_OF_DAY, 0);
            b.set(Calendar.MINUTE, 0);
            b.set(Calendar.SECOND, 0);
            b.set(Calendar.MILLISECOND, 0);

            long diffDays = (b.getTimeInMillis() - a.getTimeInMillis()) / (24 * 3600 * 1000L);
            if (diffDays == 0) {
                return "今天";
            } else if (diffDays == 1) {
                return "昨天";
            } else if (diffDays == 2) {
                return "前天";
            } else if (diffDays > 0 && diffDays < 7) {
                return diffDays + " 天前";
            }
            return dateStr;
        } catch (Exception e) {
            return dateStr;
        }
    }

    /**
     * 星期中文描述。
     */
    public static String weekday(String dateStr) {
        try {
            Date d = parse(dateStr, DATE_FORMAT);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            String[] names = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
            return names[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 根据当前时间返回问候语。
     */
    public static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 6) {
            return "夜深了";
        } else if (hour < 11) {
            return "早上好";
        } else if (hour < 14) {
            return "中午好";
        } else if (hour < 18) {
            return "下午好";
        }
        return "晚上好";
    }

    /**
     * 生成 n 天前的日期字符串，用于「近 n 天」范围统计。
     *
     * @param days 往前推的天数，days=7 表示包含今天在内的最近 7 天
     * @return yyyy-MM-dd 格式的日期字符串
     */
    public static String dateDaysAgo(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -(Math.max(1, days) - 1));
        return format(c.getTime(), DATE_FORMAT);
    }

    /**
     * 生成本周（周一至周日）的日期字符串数组，用于折线图。
     */
    public static String[] currentWeekDates() {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        String[] result = new String[7];
        for (int i = 0; i < 7; i++) {
            result[i] = format(c.getTime(), DATE_FORMAT);
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
    }
}
