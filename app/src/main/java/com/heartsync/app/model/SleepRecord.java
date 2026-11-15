package com.heartsync.app.model;

import java.io.Serializable;

/**
 * 睡眠记录实体类。
 * 对应数据库表 t_sleep_record，用于跨 Activity 通过 Intent 传递。
 */
public class SleepRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    /** 记录日期，格式 yyyy-MM-dd */
    private String date;
    /** 入睡时间，格式 HH:mm */
    private String sleepStart;
    /** 起床时间，格式 HH:mm */
    private String wakeTime;
    /** 睡眠总时长（分钟） */
    private int durationMinutes;
    /** 深睡时长（分钟） */
    private int deepMinutes;
    /** 睡眠质量评分 1~5 */
    private int quality;
    /** 睡眠备注 */
    private String note;
    /** 创建时间戳 */
    private long createTime;

    public SleepRecord() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getSleepStart() {
        return sleepStart;
    }

    public void setSleepStart(String sleepStart) {
        this.sleepStart = sleepStart;
    }

    public String getWakeTime() {
        return wakeTime;
    }

    public void setWakeTime(String wakeTime) {
        this.wakeTime = wakeTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getDeepMinutes() {
        return deepMinutes;
    }

    public void setDeepMinutes(int deepMinutes) {
        this.deepMinutes = deepMinutes;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    /** 睡眠时长格式化，例如 7.5 小时 */
    public String getDurationText() {
        float hours = durationMinutes / 60f;
        return String.format(java.util.Locale.CHINA, "%.1f 小时", hours);
    }

    /** 质量对应文案 */
    public String getQualityText() {
        String[] arr = {"", "很差", "较差", "一般", "良好", "极佳"};
        if (quality >= 1 && quality <= 5) {
            return arr[quality];
        }
        return "一般";
    }

    /** 质量对应色值资源名索引：0 差 → 1 中 → 2 好 */
    public int getQualityLevel() {
        if (quality >= 4) {
            return 2;
        } else if (quality == 3) {
            return 1;
        }
        return 0;
    }
}
