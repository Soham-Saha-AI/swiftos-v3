package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Small, static weather symbols drawn in a 24-by-24 viewport. */
public final class WeatherIconDrawable extends Drawable {
    private static final int SUN = 0xFFFFD685;
    private static final int MOON = 0xFFDFEAF8;
    private static final int CLOUD = 0xFFDEE7EF;
    private static final int ICE = 0xFFBCDFF5;
    private static final int LIGHTNING = 0xFFFFDB96;

    private static final float[] SUN_RAYS = {
            0f, -5.4f, 0f, -7.2f,
            3.82f, -3.82f, 5.09f, -5.09f,
            5.4f, 0f, 7.2f, 0f,
            3.82f, 3.82f, 5.09f, 5.09f,
            0f, 5.4f, 0f, 7.2f,
            -3.82f, 3.82f, -5.09f, 5.09f,
            -5.4f, 0f, -7.2f, 0f,
            -3.82f, -3.82f, -5.09f, -5.09f
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloud = new Path();
    private final Path moonDisk = new Path();
    private final Path moonCutout = new Path();
    private final Path bolt = new Path();
    private final Path star = new Path();

    private String kind = "cloud";
    private boolean isDay = true;
    private int alpha = 255;
    private ColorFilter colorFilter;

    public WeatherIconDrawable() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        cloud.moveTo(6.1f, 17.5f);
        cloud.cubicTo(3.7f, 17.5f, 1.8f, 15.9f, 1.8f, 13.7f);
        cloud.cubicTo(1.8f, 11.5f, 3.5f, 9.7f, 5.8f, 9.6f);
        cloud.cubicTo(6.8f, 6.2f, 10.8f, 5.2f, 13.6f, 7.1f);
        cloud.cubicTo(15.1f, 8.0f, 16.1f, 9.6f, 16.4f, 11.1f);
        cloud.cubicTo(19.5f, 10.6f, 22.2f, 12.1f, 22.2f, 14.6f);
        cloud.cubicTo(22.2f, 16.3f, 20.7f, 17.5f, 18.5f, 17.5f);
        cloud.close();

        moonDisk.addCircle(0f, 0f, 7.4f, Path.Direction.CW);
        moonCutout.setFillType(Path.FillType.EVEN_ODD);
        moonCutout.addCircle(0f, 0f, 7.4f, Path.Direction.CW);
        moonCutout.addCircle(3.1f, -3.1f, 7.2f, Path.Direction.CW);

        bolt.moveTo(12.7f, 14.6f);
        bolt.lineTo(9.1f, 19.1f);
        bolt.lineTo(11.9f, 19.1f);
        bolt.lineTo(10.8f, 23f);
        bolt.lineTo(16.2f, 17.2f);
        bolt.lineTo(13.4f, 17.2f);
        bolt.lineTo(14.8f, 14.6f);
        bolt.close();

        star.moveTo(0f, -1f);
        star.quadTo(0.2f, -0.2f, 1f, 0f);
        star.quadTo(0.2f, 0.2f, 0f, 1f);
        star.quadTo(-0.2f, 0.2f, -1f, 0f);
        star.quadTo(-0.2f, -0.2f, 0f, -1f);
        star.close();
    }

    public void setWeather(String kind, boolean isDay) {
        String nextKind = normalizeKind(kind);
        if (this.kind.equals(nextKind) && this.isDay == isDay) return;
        this.kind = nextKind;
        this.isDay = isDay;
        invalidateSelf();
    }

    private static String normalizeKind(String kind) {
        if (kind == null) return "cloud";
        switch (kind) {
            case "clear":
            case "partly":
            case "fog":
            case "rain":
            case "snow":
            case "storm":
                return kind;
            default:
                return "cloud";
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || alpha == 0) return;
        float scale = Math.min(bounds.width(), bounds.height()) / 24f;
        int saved = canvas.save();
        canvas.translate(bounds.left + (bounds.width() - 24f * scale) / 2f,
                bounds.top + (bounds.height() - 24f * scale) / 2f);
        canvas.scale(scale, scale);

        switch (kind) {
            case "clear":
                if (isDay) {
                    drawSun(canvas, 12f, 12f, 1.14f);
                } else {
                    drawMoon(canvas, 11.2f, 12.3f, 1f);
                    drawStar(canvas, 18.5f, 5.8f, 1.3f);
                    drawStar(canvas, 20.6f, 11.2f, 0.75f);
                }
                break;
            case "partly":
                if (isDay) drawSun(canvas, 8.5f, 8.2f, 0.82f);
                else drawMoon(canvas, 8.1f, 8f, 0.77f);
                drawCloud(canvas, 1.2f);
                break;
            case "fog":
                drawCloud(canvas, -1f);
                stroke(CLOUD, 1.45f);
                canvas.drawLine(3.7f, 19f, 20.3f, 19f, paint);
                canvas.drawLine(6f, 22f, 18f, 22f, paint);
                break;
            case "rain":
                drawCloud(canvas, -2f);
                stroke(ICE, 1.65f);
                canvas.drawLine(7.4f, 18f, 6.2f, 21.4f, paint);
                canvas.drawLine(12.4f, 18f, 11.2f, 21.4f, paint);
                canvas.drawLine(17.4f, 18f, 16.2f, 21.4f, paint);
                break;
            case "snow":
                drawCloud(canvas, -2f);
                stroke(ICE, 1.15f);
                drawSnowflake(canvas, 7.5f, 19.6f);
                drawSnowflake(canvas, 16.5f, 19.6f);
                break;
            case "storm":
                drawCloud(canvas, -2f);
                fill(LIGHTNING);
                canvas.drawPath(bolt, paint);
                break;
            default:
                drawCloud(canvas, 0.5f);
                break;
        }
        canvas.restoreToCount(saved);
    }

    private void drawSun(Canvas canvas, float x, float y, float scale) {
        int saved = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        stroke(SUN, 1.55f);
        canvas.drawLines(SUN_RAYS, paint);
        fill(SUN);
        canvas.drawCircle(0f, 0f, 3.65f, paint);
        canvas.restoreToCount(saved);
    }

    private void drawMoon(Canvas canvas, float x, float y, float scale) {
        int saved = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        // Clip the even-odd cutout to the outer disk, so its second circle cannot add an outer lobe.
        // This leaves the existing background untouched and needs no CLEAR blend or offscreen layer.
        canvas.clipPath(moonDisk);
        fill(MOON);
        canvas.drawPath(moonCutout, paint);
        canvas.restoreToCount(saved);
    }

    private void drawCloud(Canvas canvas, float yOffset) {
        int saved = canvas.save();
        canvas.translate(0f, yOffset);
        fill(CLOUD);
        canvas.drawPath(cloud, paint);
        canvas.restoreToCount(saved);
    }

    private void drawStar(Canvas canvas, float x, float y, float scale) {
        int saved = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        fill(MOON);
        canvas.drawPath(star, paint);
        canvas.restoreToCount(saved);
    }

    private void drawSnowflake(Canvas canvas, float x, float y) {
        canvas.drawLine(x, y - 1.9f, x, y + 1.9f, paint);
        canvas.drawLine(x - 1.65f, y - 0.95f, x + 1.65f, y + 0.95f, paint);
        canvas.drawLine(x - 1.65f, y + 0.95f, x + 1.65f, y - 0.95f, paint);
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(alpha);
    }

    private void stroke(int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        paint.setAlpha(alpha);
    }

    @Override
    public void setAlpha(int alpha) {
        int next = Math.max(0, Math.min(255, alpha));
        if (this.alpha == next) return;
        this.alpha = next;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (this.colorFilter == colorFilter) return;
        this.colorFilter = colorFilter;
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
