package com.heartsync.app.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.heartsync.app.R;

/**
 * 环形进度自定义 View。
 *
 * <p>用于首页展示 0~100 的健康指数。纯 Canvas 绘制，不依赖任何第三方图表库。</p>
 */
public class RingProgressView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private RectF arcRect;

    /** 当前进度 0~100 */
    private float progress = 0f;
    /** 圆环线宽（px） */
    private float strokeWidth;
    /** 起始角度，-90 度即从 12 点方向开始 */
    private final float startAngle = -90f;
    /** 圆环是否用渐变 */
    private boolean gradientEnabled = true;

    public RingProgressView(Context context) {
        this(context, null);
    }

    public RingProgressView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RingProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidth = getResources().getDisplayMetrics().density * 7f;

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(getResources().getColor(R.color.bg_subtle, getContext().getTheme()));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(getResources().getColor(R.color.brand_primary, getContext().getTheme()));

        arcRect = new RectF();
    }

    /**
     * 设置进度值（0~100），超出会自动裁剪。
     */
    public void setProgress(float value) {
        this.progress = Math.max(0f, Math.min(100f, value));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    /**
     * 设置圆环颜色。
     */
    public void setProgressColor(int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float padding = strokeWidth / 2f + 2f;
        float size = Math.min(width, height);

        float left = (width - size) / 2f + padding;
        float top = (height - size) / 2f + padding;
        float right = left + size - padding * 2;
        float bottom = top + size - padding * 2;
        arcRect.set(left, top, right, bottom);

        // 背景轨道
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint);

        // 进度弧
        float sweep = progress / 100f * 360f;
        if (sweep > 0) {
            canvas.drawArc(arcRect, startAngle, sweep, false, progressPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = (int) (getResources().getDisplayMetrics().density * 84);
        int w = resolveSize(defaultSize, widthMeasureSpec);
        int h = resolveSize(defaultSize, heightMeasureSpec);
        // 保持正方形
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }
}
