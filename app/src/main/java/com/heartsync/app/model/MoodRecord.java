package com.heartsync.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 情绪记录实体类，对应数据库表 t_mood_record。
 */
public class MoodRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 情绪标签候选池 */
    public static final String[] TAGS = {
            "开心", "平静", "充实", "疲惫", "焦虑", "低落",
            "烦躁", "紧张", "孤独", "被理解", "有动力", "迷茫"
    };

    private long id;
    private String date;
    /** 心情分值 1~5，1 最低 5 最高 */
    private int score;
    /** 逗号分隔的标签串 */
    private String tags;
    /** 情绪日记正文 */
    private String diary;
    /** AI 生成的分析结果 */
    private String aiAnalysis;
    private long createTime;

    public MoodRecord() {
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

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDiary() {
        return diary;
    }

    public void setDiary(String diary) {
        this.diary = diary;
    }

    public String getAiAnalysis() {
        return aiAnalysis;
    }

    public void setAiAnalysis(String aiAnalysis) {
        this.aiAnalysis = aiAnalysis;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    /** 将逗号分隔的标签串转成 List */
    public List<String> getTagList() {
        List<String> list = new ArrayList<>();
        if (tags == null || tags.isEmpty()) {
            return list;
        }
        for (String s : tags.split(",")) {
            if (!s.trim().isEmpty()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    /** 心情分值对应文案 */
    public String getScoreText() {
        String[] arr = {"", "很糟", "偏低", "一般", "不错", "很棒"};
        if (score >= 1 && score <= 5) {
            return arr[score];
        }
        return "一般";
    }

    /** 心情分值对应色值索引：0 差 → 1 中 → 2 好 */
    public int getScoreLevel() {
        if (score >= 4) {
            return 2;
        } else if (score == 3) {
            return 1;
        }
        return 0;
    }
}
