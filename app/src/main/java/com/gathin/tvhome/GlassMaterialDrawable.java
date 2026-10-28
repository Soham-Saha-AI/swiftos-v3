package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Reuses the app cards' baked material behind control text; no backdrop sampling or live blur. */
final class GlassMaterialDrawable extends Drawable {
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint material = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix textureMatrix = new Matrix();
    private final float radius;
    private final float density;
    private final Bitmap texture;
    private final BitmapShader textureShader;
    private int alpha = 255;

    GlassMaterialDrawable(Context context, float radiusDp) {
        density = context.getResources().getDisplayMetrics().density;
        radius = radiusDp * density;
        base.setColor(0x48101827);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(0.65f * density);
        texture = GlassArtwork.material(context);
        textureShader = texture == null ? null : new BitmapShader(texture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        material.setShader(textureShader);
        material.setAlpha(48);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        float inset = 0.5f * density;
        rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        if (rect.isEmpty()) return;
        if (textureShader != null) {
            boolean prepared = texture.getWidth() <= 512;
            float l = prepared ? 0f : texture.getWidth() * 0.014f;
            float t = prepared ? 0f : texture.getHeight() * 0.087f;
            float w = prepared ? texture.getWidth() : texture.getWidth() * 0.972f;
            float h = prepared ? texture.getHeight() : texture.getHeight() * 0.815f;
            float sx = rect.width() / w, sy = rect.height() / h;
            textureMatrix.setScale(sx, sy);
            textureMatrix.postTranslate(rect.left - l * sx, rect.top - t * sy);
            textureShader.setLocalMatrix(textureMatrix);
        }
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0xB8F1F7FF, 0x48B6C8E4, 0x819CADD4, 0xA9DFDFF2},
                new float[]{0f, 0.35f, 0.70f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        if (rect.isEmpty()) return;
        canvas.drawRoundRect(rect, radius, radius, base);
        if (textureShader != null) canvas.drawRoundRect(rect, radius, radius, material);
        canvas.drawRoundRect(rect, radius, radius, rim);
    }

    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }
    @Override public void setAlpha(int value) {
        alpha = Math.max(0, Math.min(255, value));
        base.setAlpha(Math.round(72f * alpha / 255f));
        material.setAlpha(Math.round(48f * alpha / 255f));
        rim.setAlpha(alpha);
        invalidateSelf();
    }
    @Override public void setColorFilter(ColorFilter filter) {
        base.setColorFilter(filter); material.setColorFilter(filter); rim.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
