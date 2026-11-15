package com.heartsync.app.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.heartsync.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 折线图自定义 View。
 *
 * <p>用于健康报告中展示本周睡眠时长、心情分值等趋势数据。
 * 纯 Canvas 实现，报告「系统界面的设计与描述」一节有对应说明。</p>
 */
public class LineChartView extends View {

    /** 图表数据点 */
    private static class Point {
        String label;
        float value;

        Point(String label, float value) {
            this.label = label;
            this.value = value;
        }

        /** 该点是否有效（value 为 NaN 表示当日无记录） */
        boolean isValid() {
            return !Float.isNaN(value);
        }
    }

    private final List<Point> points = new ArrayList<>();
    private Paint linePaint;
    private Paint fillPaint;
    private Paint dotPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Path linePath = new Path();
    private Path fillPath = new Path();

    /** 数据上限，null 表示自动计算 */
    private Float maxValue = null;
    /** 数据下限 */
    private Float minValue = 0f;
    /** 纵轴单位 */
    private String unit = "";
    /** 是否显示数据点标签 */
    private boolean showValueLabels = true;

    public LineChartView(Context context) {
        this(context, null);
    }

    public LineChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(getResources().getColor(R.color.brand_primary, getContext().getTheme()));

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(getResources().getColor(R.color.brand_primary, getContext().getTheme()));

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
     * 设置图表数据。
     *
     * <p>若某日无记录，请传入 {@link Float#NaN} 作为该点数值：图表会
     * 在该位置断开折线且不绘制数据点，从而如实表达「当天没有数据」，
     * 而不是把它当作 0 值画进曲线。</p>
     *
     * @param labels     X 轴标签
     * @param values     Y 轴数值
     * @param maxValue   纵轴上限，传 null 表示自动
     * @param minValue   纵轴下限
     * @param unit       纵轴单位文字
     */
    public void setData(List<String> labels, List<float[]> values, Float maxValue,
                        Float minValue, String unit) {
        points.clear();
        if (labels != null && values != null) {
            for (int i = 0; i < labels.size() && i < values.size(); i++) {
                points.add(new Point(labels.get(i), values.get(i)[0]));
            }
        }
        this.maxValue = maxValue;
        this.minValue = minValue == null ? 0f : minValue;
        this.unit = unit == null ? "" : unit;
        invalidate();
    }

    /**
     * 设置数据点的展示文字（例如把 "23:30-07:00" 之类映射成小时数）。
     */
    public void setValueLabels(List<String> valueLabels) {
        this.valueLabels = valueLabels;
        invalidate();
    }

    private List<String> valueLabels;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) {
            drawEmpty(canvas);
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

        // 计算纵轴范围
        float max = maxValue != null ? maxValue : 0f;
        float min = minValue;
        if (maxValue == null) {
            for (Point p : points) {
                if (p.isValid()) {
                    max = Math.max(max, p.value);
                }
            }
            max = max <= 0 ? 10f : max * 1.15f;
        }
        if (max <= min) {
            max = min + 1f;
        }

        // 绘制横向网格线与刻度
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            float y = topPad + chartHeight * i / gridLines;
            canvas.drawLine(leftPad, y, leftPad + chartWidth, y, gridPaint);
            float value = max - (max - min) * i / gridLines;
            // 纵轴刻度右对齐到图表左边缘，避免文字被裁切
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(formatValue(value), leftPad - 6 * density, y + 3.5f * density, textPaint);
        }

        // 计算数据点坐标
        int n = points.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        boolean[] valid = new boolean[n];
        // X 轴与数值标签使用居中对齐
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float ratioX = n == 1 ? 0.5f : i / (float) (n - 1);
            xs[i] = leftPad + chartWidth * ratioX;
            valid[i] = points.get(i).isValid();
            if (valid[i]) {
                float ratioY = (points.get(i).value - min) / (max - min);
                ys[i] = topPad + chartHeight * (1 - Math.max(0f, Math.min(1f, ratioY)));
            }
        }

        // 绘制填充区域与折线：按「连续有效段」分段构建，遇到缺失点断开
        fillPath.reset();
        linePath.reset();
        int segStart = -1;
        for (int i = 0; i <= n; i++) {
            boolean ok = i < n && valid[i];
            if (ok && segStart < 0) {
                segStart = i;
            } else if (!ok && segStart >= 0) {
                // 当前有效段为 [segStart, i-1]，先描边再填充
                linePath.moveTo(xs[segStart], ys[segStart]);
                for (int j = segStart + 1; j <= i - 1; j++) {
                    // 使用二次贝塞尔让折线更平滑
                    float midX = (xs[j - 1] + xs[j]) / 2f;
                    linePath.cubicTo(midX, ys[j - 1], midX, ys[j], xs[j], ys[j]);
                }
                fillPath.moveTo(xs[segStart], topPad + chartHeight);
                for (int j = segStart; j <= i - 1; j++) {
                    fillPath.lineTo(xs[j], ys[j]);
                }
                fillPath.lineTo(xs[i - 1], topPad + chartHeight);
                fillPath.close();
                segStart = -1;
            }
        }

        int brandColor = getResources().getColor(R.color.brand_primary, getContext().getTheme());
        fillPaint.setShader(new LinearGradient(0, topPad, 0, topPad + chartHeight,
                brandColor & 0x00FFFFFF | 0x55000000, brandColor & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // 绘制数据点与标签（缺失点只保留 X 轴日期，不画点也不写数值）
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            if (valid[i]) {
                canvas.drawCircle(xs[i], ys[i], 4.5f * density, dotPaint);
                canvas.drawCircle(xs[i], ys[i], 2f * density, fillPaint);
            }
            // X 轴标签
            canvas.drawText(points.get(i).label, xs[i], getHeight() - 6 * density, textPaint);
            // 数值标签
            if (showValueLabels && valueLabels != null && i < valueLabels.size()) {
                String label = valueLabels.get(i);
                if (label != null && !label.isEmpty() && valid[i]) {
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(label, xs[i], ys[i] - 9 * density, textPaint);
                }
            }
        }
    }

    private void drawEmpty(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("暂无数据", getWidth() / 2f, getHeight() / 2f, textPaint);
    }

    private String formatValue(float value) {
        if (value == Math.round(value)) {
            return String.valueOf(Math.round(value)) + unit;
        }
        return String.format(java.util.Locale.CHINA, "%.1f%s", value, unit);
    }
}
