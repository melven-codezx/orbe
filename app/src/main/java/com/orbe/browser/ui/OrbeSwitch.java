package com.orbe.browser.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.orbe.browser.Ui;

/** Interruptor desenhado à mão: trilho com gradiente e botão que desliza. */
public class OrbeSwitch extends View {

    public interface OnChangeListener {
        void onChanged(boolean checked);
    }

    private static final int OFF = 0xFFDCE0EE;
    private static final int GRAD_A = 0xFF5B4BEE;
    private static final int GRAD_B = 0xFF22D3EE;

    private final Paint offPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint onPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean checked;
    private float progress;
    private float press = 1f;
    private ValueAnimator animator;
    private OnChangeListener listener;

    public OrbeSwitch(Context context) {
        this(context, null);
    }

    public OrbeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        offPaint.setColor(OFF);
        knobPaint.setColor(0xFFFFFFFF);
        knobShadow.setColor(0x22000000);
        dotPaint.setColor(0xFF22D3EE);
        setClickable(true);
        setFocusable(true);
    }

    public void setOnChangeListener(OnChangeListener l) {
        this.listener = l;
    }

    public boolean isChecked() {
        return checked;
    }

    /** Define o estado; use notify=false para sincronizar sem disparar callback. */
    public void setChecked(boolean value, boolean notify) {
        setChecked(value, notify, true);
    }

    public void setChecked(boolean value, boolean notify, boolean animate) {
        if (checked == value && animate) {
            return;
        }
        checked = value;
        if (!animate) {
            progress = value ? 1f : 0f;
            invalidate();
        } else {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(progress, value ? 1f : 0f);
            animator.setDuration(240);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
        if (notify && listener != null) listener.onChanged(checked);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        onPaint.setShader(new LinearGradient(0, 0, w, h, GRAD_A, GRAD_B, Shader.TileMode.CLAMP));
        rect.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        canvas.scale(press, press, w / 2f, h / 2f);

        float r = h / 2f;
        canvas.drawRoundRect(rect, r, r, offPaint);

        if (progress > 0.01f) {
            onPaint.setAlpha((int) (255 * Math.min(1f, progress)));
            canvas.drawRoundRect(rect, r, r, onPaint);
            onPaint.setAlpha(255);
        }

        float knobR = r - Ui.dp(getContext(), 3f);
        float cx = knobR + Ui.dp(getContext(), 3f) + (w - 2 * (knobR + Ui.dp(getContext(), 3f))) * progress;
        canvas.drawCircle(cx, r + Ui.dp(getContext(), 1.2f), knobR, knobShadow);
        canvas.drawCircle(cx, r, knobR, knobPaint);

        if (progress > 0.02f) {
            dotPaint.setAlpha((int) (255 * progress * 0.9f));
            canvas.drawCircle(cx, r, knobR * 0.34f, dotPaint);
            dotPaint.setAlpha(255);
        }
        canvas.restore();
    }

    private void animatePress(final float target) {
        ValueAnimator a = ValueAnimator.ofFloat(press, target);
        a.setDuration(120);
        a.addUpdateListener(an -> {
            press = (float) an.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animatePress(0.94f);
                return true;
            case MotionEvent.ACTION_UP:
                animatePress(1f);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                animatePress(1f);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        setChecked(!checked, true, true);
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        return true;
    }
}
