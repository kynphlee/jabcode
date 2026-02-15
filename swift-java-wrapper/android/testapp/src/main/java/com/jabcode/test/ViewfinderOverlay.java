package com.jabcode.test;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class ViewfinderOverlay extends View {
    
    public enum State {
        IDLE,
        SCANNING,
        DETECTED,
        SUCCESS,
        ERROR
    }
    
    private static final float SCAN_AREA_RATIO = 0.7f;
    private static final float MAX_SCAN_SIZE_DP = 300f;
    private static final float CORNER_RADIUS_DP = 16f;
    private static final float BORDER_WIDTH_DP = 3f;
    private static final int OVERLAY_ALPHA = 153; // 60% opacity
    
    private State currentState = State.IDLE;
    private RectF scanArea = new RectF();
    private RectF detectedBounds = null;
    
    private Paint overlayPaint;
    private Paint borderPaint;
    private Paint detectedBoundsPaint;
    
    private ValueAnimator pulseAnimator;
    private float animationProgress = 0f;
    
    public ViewfinderOverlay(Context context) {
        this(context, null);
    }
    
    public ViewfinderOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.BLACK);
        overlayPaint.setAlpha(OVERLAY_ALPHA);
        
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(BORDER_WIDTH_DP));
        
        detectedBoundsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        detectedBoundsPaint.setStyle(Paint.Style.STROKE);
        detectedBoundsPaint.setStrokeWidth(dpToPx(2f));
        detectedBoundsPaint.setColor(Color.CYAN);
        detectedBoundsPaint.setAlpha(200);
        
        setupAnimator();
    }
    
    private void setupAnimator() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateScanArea(w, h);
    }
    
    private void calculateScanArea(int width, int height) {
        float scanSize = Math.min(width * SCAN_AREA_RATIO, dpToPx(MAX_SCAN_SIZE_DP));
        
        float left = (width - scanSize) / 2f;
        float top = (height - scanSize) / 2f - (height * 0.1f); // Offset upward slightly
        
        scanArea.set(left, top, left + scanSize, top + scanSize);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (getWidth() == 0 || getHeight() == 0) return;
        
        canvas.save();
        
        // Draw semi-transparent overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        
        // Clear scan area (punch-out effect)
        Paint clearPaint = new Paint();
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setStyle(Paint.Style.FILL);
        clearPaint.setAntiAlias(true);
        canvas.drawRoundRect(scanArea, dpToPx(CORNER_RADIUS_DP), dpToPx(CORNER_RADIUS_DP), clearPaint);
        
        // Draw border
        updateBorderColor();
        canvas.drawRoundRect(scanArea, dpToPx(CORNER_RADIUS_DP), dpToPx(CORNER_RADIUS_DP), borderPaint);
        
        // Draw detected bounds if available
        if (detectedBounds != null) {
            canvas.drawRect(detectedBounds, detectedBoundsPaint);
        }
        
        canvas.restore();
    }
    
    private void updateBorderColor() {
        int baseColor = getBorderColorForState();
        
        if (isAnimating()) {
            int alpha = (int) (127 + 128 * animationProgress);
            borderPaint.setAlpha(alpha);
        } else {
            borderPaint.setAlpha(255);
        }
        
        borderPaint.setColor(baseColor);
    }
    
    private int getBorderColorForState() {
        switch (currentState) {
            case IDLE:
                return Color.WHITE;
            case SCANNING:
                return Color.parseColor("#BBDEFB"); // Light blue
            case DETECTED:
                return Color.YELLOW;
            case SUCCESS:
                return Color.parseColor("#4CAF50"); // Green
            case ERROR:
                return Color.parseColor("#F44336"); // Red
            default:
                return Color.WHITE;
        }
    }
    
    public void setState(State state) {
        if (this.currentState == state) return;
        
        this.currentState = state;
        
        // Start/stop animation based on state
        if (shouldAnimate(state)) {
            if (!pulseAnimator.isRunning()) {
                pulseAnimator.start();
            }
        } else {
            pulseAnimator.cancel();
            animationProgress = 0f;
        }
        
        invalidate();
    }
    
    private boolean shouldAnimate(State state) {
        return state == State.SCANNING || state == State.SUCCESS || state == State.ERROR;
    }
    
    public State getState() {
        return currentState;
    }
    
    public int getBorderColor() {
        return getBorderColorForState();
    }
    
    public boolean isAnimating() {
        return pulseAnimator.isRunning();
    }
    
    public RectF getScanArea() {
        return new RectF(scanArea);
    }
    
    public void showDetectedBounds(RectF bounds) {
        this.detectedBounds = new RectF(bounds);
        invalidate();
    }
    
    public RectF getDetectedBounds() {
        return detectedBounds != null ? new RectF(detectedBounds) : null;
    }
    
    public void clearDetectedBounds() {
        this.detectedBounds = null;
        invalidate();
    }
    
    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }
}
