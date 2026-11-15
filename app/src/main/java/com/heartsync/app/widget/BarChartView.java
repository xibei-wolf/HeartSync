package com.heartsync.app.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.heartsync.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 柱状图自定义 View。
 *
 * <p>用于健康报告中展示本周运动时长分布。纯 Canvas 实现。</p>
 */
public class BarChartView extends View {

    /** 柱状数据 */
    private static class Bar {
        String label;
        float value;
        String valueLabel;

        Bar(String label, float value, String valueLabel) {
            this.label = label;
            this.value = value;
            this.valueLabel = valueLabel;
        }
    }

    private final List<Bar> bars = new ArrayList<>();
    private Paint barPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private RectF barRect = new RectF();

    /** 纵轴上限，null 表示自动 */
    private Float maxValue = null;
    private String unit = "";
    /** 柱条颜色 */
    private int barColor;

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);
        barColor = getResources().getColor(R.color.accent_coral, getContext().getTheme());
        barPaint.setColor(barColor);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setColor(getResources().getColor(R.color.divider, getContext().getTheme()));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(10f * density);
        textPaint.setColor(getResources().getColor(R.color.text_hint, getContext().getTheme()));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 设置柱状图数据。
     *
     * @param labels   底部标签
     * @param values   数值
     * @param maxValue 纵轴上限，null 表示自动
     * @param unit     单位
     */
    public void setData(List<String> labels, List<Float> values, List<String> valueLabels,
                        Float maxValue, String unit) {
        bars.clear();
        if (labels != null && values != null) {
            for (int i = 0; i < labels.size() && i < values.size(); i++) {
                String vl = (valueLabels != null && i < valueLabels.size()) ? valueLabels.get(i) : null;
                bars.add(new Bar(labels.get(i), values.get(i), vl));
            }
        }
        this.maxValue = maxValue;
        this.unit = unit == null ? "" : unit;
        invalidate();
    }

    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bars.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float leftPad = 42 * density;
        float rightPad = 12 * density;
        float topPad = 18 * density;
        float bottomPad = 22 * density;

        float chartWidth = getWidth() - leftPad - rightPad;
        float chartHeight = getHeight() - topPad - bottomPad;
        if (chartWidth <= 0 || chartHeight <= 0) {
            return;
        }

        // 计算最大值
        float max = maxValue != null ? maxValue : 0f;
        if (maxValue == null) {
            for (Bar b : bars) {
                max = Math.max(max, b.value);
            }
            max = max <= 0 ? 10f : max * 1.2f;
        }
        if (max <= 0) {
            max = 1f;
        }

        // 网格线
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            float y = topPad + chartHeight * i / gridLines;
            canvas.drawLine(leftPad, y, leftPad + chartWidth, y, gridPaint);
            float value = max * (1 - i / (float) gridLines);
            // 纵轴刻度右对齐到图表左边缘，避免文字被裁切
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(formatValue(value), leftPad - 6 * density, y + 3.5f * density, textPaint);
        }

        // 柱条
        int n = bars.size();
        float slot = chartWidth / n;
        float barWidth = Math.min(slot * 0.55f, 26 * density);
        float radius = 5 * density;
        // 柱状标签恢复为居中对齐
        textPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < n; i++) {
            Bar b = bars.get(i);
            float centerX = leftPad + slot * i + slot / 2f;
            float ratio = Math.max(0f, Math.min(1f, b.value / max));
            float barHeight = chartHeight * ratio;
            float top = topPad + chartHeight - barHeight;

            barRect.set(centerX - barWidth / 2f, top, centerX + barWidth / 2f,
                    topPad + chartHeight);
            canvas.drawRoundRect(barRect, radius, radius, barPaint);

            // 数值
            if (b.valueLabel != null && !b.valueLabel.isEmpty()) {
                canvas.drawText(b.valueLabel, centerX, top - 6 * density, textPaint);
            }
            // 底部标签
            canvas.drawText(b.label, centerX, getHeight() - 6 * density, textPaint);
        }
    }

    private String formatValue(float value) {
        if (value == Math.round(value)) {
            return String.valueOf(Math.round(value)) + unit;
        }
        return String.format(java.util.Locale.CHINA, "%.1f%s", value, unit);
    }
}
