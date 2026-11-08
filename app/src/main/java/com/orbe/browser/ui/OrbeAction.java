package com.orbe.browser.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.orbe.browser.Ui;

/**
 * Botão de ação com três estados (normal, processando e concluído).
 * O ícone vira um spinner girando e depois um "check" desenhado na tela.
 */
public class OrbeAction extends View {

    public static final int STYLE_PRIMARY = 0;
    public static final int STYLE_GHOST = 1;

    private static final int STATE_IDLE = 0;
    private static final int STATE_BUSY = 1;
    private static final int STATE_DONE = 2;

    private static final int C_A = 0xFF6D5BFF;
    private static final int C_B = 0xFF4322D8;
    private static final int C_OK = 0xFF12B981;
    private static final int C_GHOST = 0xFFF0F1F8;
    private static final int C_INK = 0xFF0C1024;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String label = "";
    private String idleLabel = "";
    private Drawable icon;
    private int tint = 0xFFFFFFFF;
    private int style = STYLE_PRIMARY;
    private int state = STATE_IDLE;
    private float spin;
    private float checkProgress = 1f;
    private float press = 1f;
    private float successBlend;
    private ValueAnimator spinner;

    public OrbeAction(Context context) {
        this(context, null);
    }

    public OrbeAction(Context context, AttributeSet attrs) {
        super(context, attrs);
        textPaint.setTypeface(Ui.font(context, true));
        textPaint.setTextSize(Ui.sp(context, 15.5f));
        textPaint.setAntiAlias(true);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        setClickable(true);
        setFocusable(true);
    }

    public void setup(String label, int iconRes, int style) {
        this.label = label;
        this.idleLabel = label;
        this.style = style;
        this.tint = style == STYLE_PRIMARY ? 0xFFFFFFFF : C_INK;
        this.icon = getResources().getDrawable(iconRes, getContext().getTheme());
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(tint);
        }
        invalidate();
    }

    public void setLabelText(String value) {
        this.label = value;
        invalidate();
    }

    public boolean isBusy() {
        return state == STATE_BUSY;
    }

    public void setBusy(boolean busy) {
        if (busy) {
            state = STATE_BUSY;
            checkProgress = 1f;
            if (spinner == null) {
                spinner = ValueAnimator.ofFloat(0f, 360f);
                spinner.setDuration(760);
                spinner.setRepeatCount(ValueAnimator.INFINITE);
                spinner.addUpdateListener(a -> {
                    spin = (float) a.getAnimatedValue();
                    invalidate();
                });
            }
            spinner.start();
        } else {
            state = STATE_IDLE;
            if (spinner != null) spinner.cancel();
        }
        invalidate();
    }

    public void setDone() {
        state = STATE_DONE;
        checkProgress = 0f;
        if (spinner != null) spinner.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(320);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> {
            checkProgress = (float) an.getAnimatedValue();
            successBlend = Math.min(1f, checkProgress * 1.6f);
            invalidate();
        });
        a.start();
        performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
    }

    public void reset(String originalLabel) {
        state = STATE_IDLE;
        label = originalLabel;
        idleLabel = originalLabel;
        successBlend = 0f;
        checkProgress = 1f;
        if (spinner != null) spinner.cancel();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rect.set(0, 0, w, h);
        updateBg();
    }

    private void updateBg() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (style == STYLE_GHOST) {
            bgPaint.setShader(null);
            bgPaint.setColor(blend(C_GHOST, C_OK, successBlend * 0.18f));
        } else {
            bgPaint.setShader(new LinearGradient(0, 0, w, h,
                    blend(C_A, C_OK, successBlend), blend(C_B, C_OK, successBlend * 0.85f),
                    Shader.TileMode.CLAMP));
        }
        textPaint.setColor(style == STYLE_GHOST
                ? blend(C_INK, C_OK, successBlend)
                : 0xFFFFFFFF);
    }

    private static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >> 24) & 0xFF) + (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        updateBg();

        canvas.save();
        canvas.scale(press, press, w / 2f, h / 2f);

        float radius = Ui.dp(getContext(), 19f);
        canvas.drawRoundRect(rect, radius, radius, bgPaint);

        float iconSize = Ui.dp(getContext(), 21f);
        float gap = Ui.dp(getContext(), 11f);
        float textW = textPaint.measureText(label);
        float total = iconSize + gap + textW;
        float startX = (w - total) / 2f;
        float cy = h / 2f;
        float iconLeft = startX;
        float iconTop = cy - iconSize / 2f;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, startX + iconSize + gap, textY, textPaint);

        RectF iconRect = new RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);

        if (state == STATE_BUSY) {
            iconPaint.setStrokeWidth(Ui.dp(getContext(), 2.4f));
            iconPaint.setColor(tint);
            iconPaint.setAlpha(230);
            iconRect.inset(Ui.dp(getContext(), 1.5f), Ui.dp(getContext(), 1.5f));
            canvas.drawArc(iconRect, spin, 280f, false, iconPaint);
            iconPaint.setAlpha(255);
        } else if (state == STATE_DONE) {
            drawCheck(canvas, iconRect);
        } else if (icon != null) {
            icon.setAlpha(240);
            icon.setBounds((int) iconLeft, (int) iconTop,
                    (int) (iconLeft + iconSize), (int) (iconTop + iconSize));
            icon.draw(canvas);
        }

        canvas.restore();
    }

    private void drawCheck(Canvas canvas, RectF r) {
        float p = Math.max(0.001f, Math.min(1f, checkProgress));
        Path path = new Path();
        path.moveTo(r.left + r.width() * 0.06f, r.top + r.height() * 0.54f);
        path.lineTo(r.left + r.width() * 0.40f, r.top + r.height() * 0.86f);
        path.lineTo(r.left + r.width() * 0.98f, r.top + r.height() * 0.16f);

        PathMeasure pm = new PathMeasure(path, false);
        Path partial = new Path();
        pm.getSegment(0f, pm.getLength() * p, partial, true);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(Ui.dp(getContext(), 2.6f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(tint);
        canvas.drawPath(partial, iconPaint);
    }

    private void animatePress(final float target) {
        ValueAnimator a = ValueAnimator.ofFloat(press, target);
        a.setDuration(110);
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
                animatePress(0.975f);
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
}
