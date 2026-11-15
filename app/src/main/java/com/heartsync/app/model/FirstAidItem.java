package com.heartsync.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 急救知识条目实体类。
 * 数据来源：本地 assets JSON（离线可用）+ 服务器接口（在线更新）。
 */
public class FirstAidItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    /** 分类，例如「意外伤害」「急症处置」 */
    private String category;
    /** 标题 */
    private String title;
    /** 症状描述 */
    private String symptom;
    /** 处理步骤，逐条 */
    private List<String> steps = new ArrayList<>();
    /** 注意事项 */
    private String caution;
    /** 紧急程度：1 轻度 2 中度 3 危急 */
    private int severity;

    public FirstAidItem() {
    }

    public FirstAidItem(String id, String category, String title, String symptom,
                        List<String> steps, String caution, int severity) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.symptom = symptom;
        this.steps = steps;
        this.caution = caution;
        this.severity = severity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSymptom() {
        return symptom;
    }

    public void setSymptom(String symptom) {
        this.symptom = symptom;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public String getCaution() {
        return caution;
    }

    public void setCaution(String caution) {
        this.caution = caution;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public String getSeverityText() {
        switch (severity) {
            case 1:
                return "轻度";
            case 3:
                return "危急";
            default:
                return "中度";
        }
    }
}
