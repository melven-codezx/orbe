package com.orbe.browser.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;

import com.orbe.browser.Ui;

/**
 * Botão flutuante do Orbe: esfera com halo, anel orbital e satélite girando.
 * Arrastável, com feedback de toque e morfose para "X" quando a janela está aberta.
 */
public class OrbeFab extends View {

    public interface Listener {
        void onTap();

        void onLongPressAction();

        void onMoved();
    }

    private static final int COLOR_A = 0xFF8B7BFF;
    private static final int COLOR_B = 0xFF5B4BEE;
    private static final int COLOR_C = 0xFF2E1FB8;
    private static final int COLOR_GLOW = 0x8C8B5CF6;
    private static final int COLOR_SAT = 0xFF56F4FF;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Listener listener;

    private float pad;
    private float radius;
    private float spin;
    private float press = 1f;
    private float openAmount;
    private boolean open;

    private float downRawX, downRawY, startX, startY;
    private boolean dragging;
    private boolean longPressConsumed;
    private final float slop;

    private ValueAnimator spinAnimator;

    public OrbeFab(Context context) {
        this(context, null);
    }

    public OrbeFab(Context context, AttributeSet attrs) {
        super(context, attrs);
        pad = Ui.dp(context, 7);
        slop = Ui.dp(context, 6);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);
        setClickable(true);
        setFocusable(true);
        if (Build.VERSION.SDK_INT >= 21) {
            setElevation(Ui.dp(context, 10));
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float p = pad;
                    float r = Math.max(1f, Math.min(view.getWidth(), view.getHeight()) / 2f - p);
                    outline.setOval((int) (p), (int) (p), (int) (p + 2 * r), (int) (p + 2 * r));
                }
            });
        }
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean value) {
        if (open == value) return;
        open = value;
        ValueAnimator a = ValueAnimator.ofFloat(openAmount, value ? 1f : 0f);
        a.setDuration(260);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> {
            openAmount = (float) an.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    public void restorePosition(float x, float y) {
        if (x < 0 || y < 0) return;
        post(() -> {
            View parent = (View) getParent();
            if (parent == null) return;
            float maxX = parent.getWidth() - getWidth();
            float maxY = parent.getHeight() - getHeight();
            setX(Math.max(0f, Math.min(x, maxX)));
            setY(Math.max(0f, Math.min(y, maxY)));
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (spinAnimator == null) {
            spinAnimator = ValueAnimator.ofFloat(0f, 360f);
            spinAnimator.setDuration(5200);
            spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
            spinAnimator.setInterpolator(null);
            spinAnimator.addUpdateListener(a -> {
                spin = (float) a.getAnimatedValue();
                invalidate();
            });
            spinAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        radius = Math.min(w, h) / 2f - pad;
        float cx = w / 2f, cy = h / 2f;

        bodyPaint.setShader(new RadialGradient(cx - radius * 0.34f, cy - radius * 0.42f, radius * 1.9f,
                new int[]{COLOR_A, COLOR_B, COLOR_C}, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));

        highlightPaint.setShader(new RadialGradient(cx - radius * 0.30f, cy - radius * 0.44f, radius * 0.95f,
                new int[]{0x4DFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));

        glowPaint.setShader(new RadialGradient(cx, cy, radius * 1.16f,
                new int[]{COLOR_GLOW, 0x008B5CF6}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (radius <= 0) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;

        canvas.save();
        canvas.scale(press, press, cx, cy);

        // halo suave
        glowPaint.setAlpha((int) (110 + 60 * openAmount));
        canvas.drawCircle(cx, cy, radius * 1.16f, glowPaint);

        // esfera
        canvas.drawCircle(cx, cy, radius, bodyPaint);
        canvas.drawCircle(cx, cy, radius, highlightPaint);

        float ringR = radius * 0.40f;
        float dotR = radius * 0.135f;

        // anel + satélite (estado fechado)
        int ringAlpha = (int) (255 * (1f - openAmount));
        if (ringAlpha > 4) {
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(Ui.dp(getContext(), 2.4f));
            linePaint.setColor(Color.WHITE);
            linePaint.setAlpha(ringAlpha);
            canvas.drawCircle(cx, cy, ringR, linePaint);

            double ang = Math.toRadians(spin - 48);
            float sx = (float) (cx + Math.cos(ang) * ringR);
            float sy = (float) (cy + Math.sin(ang) * ringR);
            linePaint.setStyle(Paint.Style.FILL);
            linePaint.setColor(COLOR_SAT);
            linePaint.setAlpha(ringAlpha);
            canvas.drawCircle(sx, sy, dotR, linePaint);

            linePaint.setColor(Color.WHITE);
            linePaint.setAlpha(ringAlpha);
            canvas.drawCircle(cx, cy, dotR * 0.62f, linePaint);
        }

        // X (estado aberto)
        int xAlpha = (int) (255 * openAmount);
        if (xAlpha > 4) {
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(Ui.dp(getContext(), 2.9f));
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setColor(Color.WHITE);
            linePaint.setAlpha(xAlpha);
            float d = radius * 0.30f * (0.6f + 0.4f * openAmount);
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, linePaint);
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, linePaint);
        }

        canvas.restore();
        linePaint.setAlpha(255);
    }

    private void animatePress(final float target) {
        ValueAnimator a = ValueAnimator.ofFloat(press, target);
        a.setDuration(160);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> {
            press = (float) an.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            longPressConsumed = true;
            animatePress(1f);
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) listener.onLongPressAction();
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                longPressConsumed = false;
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = getX();
                startY = getY();
                animatePress(0.93f);
                postDelayed(longPressRunnable, 520);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (!dragging && (float) Math.hypot(dx, dy) > slop) {
                    dragging = true;
                    removeCallbacks(longPressRunnable);
                }
                if (dragging) {
                    View parent = (View) getParent();
                    if (parent != null) {
                        float maxX = parent.getWidth() - getWidth();
                        float maxY = parent.getHeight() - getHeight();
                        setX(Math.max(0f, Math.min(startX + dx, maxX)));
                        setY(Math.max(0f, Math.min(startY + dy, maxY)));
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRunnable);
                animatePress(1f);
                if (dragging) {
                    if (listener != null) listener.onMoved();
                } else if (!longPressConsumed) {
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                animatePress(1f);
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (listener != null) listener.onTap();
        return true;
    }
}
