package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Static glass material shared by Home and Library, with reflections confined to its edge. */
final class GlassTileDrawable extends Drawable {
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF edgeRect = new RectF();
    private final RectF innerRect = new RectF();
    private final float density;
    private final float radius;
    private final boolean fill;

    /** fill=false is a foreground rim: no pixels are painted across the artwork's centre. */
    GlassTileDrawable(float density, float radiusDp, boolean fill) {
        this.density = Math.max(0.5f, density);
        this.radius = Math.max(0f, radiusDp) * this.density;
        this.fill = fill;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.75f * this.density);
        innerEdge.setStyle(Paint.Style.STROKE);
        innerEdge.setStrokeWidth(0.5f * this.density);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        float strokeInset = edge.getStrokeWidth() / 2f;
        edgeRect.set(bounds.left + strokeInset, bounds.top + strokeInset,
                bounds.right - strokeInset, bounds.bottom - strokeInset);
        innerRect.set(edgeRect);
        innerRect.inset(1.5f * density, 1.5f * density);
        if (edgeRect.isEmpty()) return;
        // Shader creation only follows a size change. Drawing reuses these paints and rectangles.
        if (fill) surface.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{0xE3252932, 0xE5171B23, 0xEB12151D},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{0x82F2F9FF, 0x22D6E4EE, 0x4096B7DA, 0x54DDD9F8},
                new float[]{0f, 0.38f, 0.74f, 1f}, Shader.TileMode.CLAMP));
        innerEdge.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                new int[]{0x25FFFFFF, 0x00FFFFFF, 0x0EC6DBF4},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        if (edgeRect.isEmpty()) return;
        if (fill) canvas.drawRoundRect(edgeRect, radius, radius, surface);
        canvas.drawRoundRect(edgeRect, radius, radius, edge);
        if (!innerRect.isEmpty()) {
            float innerRadius = Math.max(0f, radius - 1.5f * density);
            canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerEdge);
        }
    }

    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override public void setAlpha(int alpha) {
        surface.setAlpha(alpha);
        edge.setAlpha(alpha);
        innerEdge.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        surface.setColorFilter(filter);
        edge.setColorFilter(filter);
        innerEdge.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
