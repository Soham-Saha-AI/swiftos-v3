package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** One transparent focus rim. Only its narrow light/shadow edge changes during interaction. */
public class GlassView extends View {
    private static final float REST_ANGLE = 225f;
    private static final FloatPropertyCompat<GlassView> EDGE_ANGLE = new FloatPropertyCompat<GlassView>("edgeAngle") {
        @Override public float getValue(GlassView view) { return view.angle; }
        @Override public void setValue(GlassView view, float value) { view.setEdgeAngle(value); }
    };
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix reflectionMatrix = new Matrix();
    private final float density;
    private final SpringAnimation reflection;
    private float radius;
    private float angle = REST_ANGLE;
    private int accent;
    private SweepGradient reflectionShader;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        base.setStyle(Paint.Style.STROKE);
        base.setStrokeWidth(0.6f * density);
        base.setColor(0x32DDE8F6);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.05f * density);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(3f * density);
        halo.setAlpha(44);
        // Repaint only this small edge while it moves; the centre, artwork and wallpaper are untouched.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        reflection = new SpringAnimation(this, EDGE_ANGLE);
        reflection.setSpring(new SpringForce().setStiffness(800f).setDampingRatio(1f));
        reflection.setMinimumVisibleChange(0.4f);
        reflection.addEndListener((animation, cancelled, value, velocity) -> {
            if (!cancelled) setEdgeAngle(GlassDirection.normalise(value));
        });
    }

    /** Direction can be a pixel delta or a logical D-pad step. Retargeting retains current velocity. */
    void interact(float dx, float dy, boolean animate) {
        if (Math.abs(dx) + Math.abs(dy) < 0.01f) return;
        if (!animate || !isAttachedToWindow()) {
            stopInteraction();
            return;
        }
        Motion.retarget(reflection, GlassDirection.nearest(angle, GlassDirection.direction(dx, dy)));
    }

    /** No animation survives dismissal/backgrounding; reduced motion uses this resting edge. */
    void stopInteraction() {
        reflection.cancel();
        reflection.setStartVelocity(0f);
        setEdgeAngle(REST_ANGLE);
    }

    void setAccent(int argb) {
        if (accent == argb) return;
        accent = argb;
        updateShader();
        invalidate();
    }

    void setRadius(float px) {
        float next = Math.max(0f, px);
        if (radius == next) return;
        radius = next;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = 6f * density;
        rect.set(inset, inset, w - inset, h - inset);
        updateShader();
    }

    private void updateShader() {
        if (rect.isEmpty()) return;
        int rgb = 0xF1F6FF;
        if (accent != 0) {
            int r = (((accent >> 16) & 255) + 3 * 245) / 4;
            int g = (((accent >> 8) & 255) + 3 * 248) / 4;
            int b = ((accent & 255) + 3 * 255) / 4;
            rgb = r << 16 | g << 8 | b;
        }
        // Paired crescents, not an all-around glow. Shader allocation only on size/accent changes.
        reflectionShader = new SweepGradient(rect.centerX(), rect.centerY(),
                new int[]{0xB0000000 | rgb, 0x35000000 | rgb, 0x00000000,
                        0x9A000000, 0x30000000, 0x00000000, 0xB0000000 | rgb},
                new float[]{0f, 0.14f, 0.29f, 0.5f, 0.64f, 0.8f, 1f});
        edge.setShader(reflectionShader);
        halo.setShader(reflectionShader);
        updateMatrix();
    }

    private void setEdgeAngle(float value) {
        if (angle == value) return;
        angle = value;
        updateMatrix();
        invalidate();
    }

    private void updateMatrix() {
        if (reflectionShader == null) return;
        reflectionMatrix.setRotate(angle, rect.centerX(), rect.centerY());
        reflectionShader.setLocalMatrix(reflectionMatrix);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (rect.isEmpty()) return;
        canvas.drawRoundRect(rect, radius, radius, halo);
        canvas.drawRoundRect(rect, radius, radius, base);
        canvas.drawRoundRect(rect, radius, radius, edge);
    }

    @Override protected void onDetachedFromWindow() {
        stopInteraction();
        super.onDetachedFromWindow();
    }
}
