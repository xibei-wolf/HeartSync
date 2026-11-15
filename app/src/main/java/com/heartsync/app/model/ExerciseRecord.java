package com.heartsync.app.model;

import java.io.Serializable;

/**
 * 运动记录实体类，对应数据库表 t_exercise_record。
 */
public class ExerciseRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 运动类型枚举 */
    public static final String[] TYPES = {
            "跑步", "快走", "骑行", "游泳", "篮球", "羽毛球",
            "足球", "瑜伽", "健身训练", "跳绳", "拉伸放松", "其他"
    };

    private long id;
    private String date;
    /** 运动类型 */
    private String type;
    /** 时长（分钟） */
    private int durationMinutes;
    /** 强度：1 轻松 2 适中 3 高强度 */
    private int intensity;
    /** 消耗热量（千卡） */
    private int calories;
    private String note;
    private long createTime;

    public ExerciseRecord() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getIntensity() {
        return intensity;
    }

    public void setIntensity(int intensity) {
        this.intensity = intensity;
    }

    public int getCalories() {
        return calories;
    }

    public void setCalories(int calories) {
        this.calories = calories;
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

    public String getIntensityText() {
        switch (intensity) {
            case 1:
                return "轻松";
            case 3:
                return "高强度";
            default:
                return "适中";
        }
    }

    /**
     * 依据运动类型、时长、强度估算热量消耗。
     * 采用 MET 公式：热量 = MET × 体重(kg) × 时长(h)。
     */
    public static int estimateCalories(String type, int minutes, int intensity) {
        double met;
        switch (type == null ? "" : type) {
            case "跑步":
                met = intensity == 3 ? 9.8 : (intensity == 1 ? 6.0 : 8.3);
                break;
            case "快走":
                met = intensity == 3 ? 4.8 : 3.5;
                break;
            case "骑行":
                met = intensity == 3 ? 10.0 : (intensity == 1 ? 4.0 : 6.8);
                break;
            case "游泳":
                met = intensity == 3 ? 10.0 : (intensity == 1 ? 5.8 : 8.3);
                break;
            case "篮球":
                met = intensity == 3 ? 9.3 : 6.5;
                break;
            case "羽毛球":
                met = intensity == 3 ? 8.0 : 5.5;
                break;
            case "足球":
                met = intensity == 3 ? 10.3 : 7.0;
                break;
            case "瑜伽":
                met = intensity == 3 ? 4.0 : 2.5;
                break;
            case "健身训练":
                met = intensity == 3 ? 8.0 : 5.0;
                break;
            case "跳绳":
                met = intensity == 3 ? 12.3 : 8.8;
                break;
            case "拉伸放松":
                met = 2.3;
                break;
            default:
                met = 4.0;
                break;
        }
        // 默认体重按 60kg 计算
        return (int) Math.round(met * 60 * (minutes / 60f));
    }
}
