package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Precomputed card artwork. Call only from the existing Home/Library artwork loader threads. */
final class GlassArtwork {
    private static final int WIDTH = 384;
    private static final int HEIGHT = 216;
    private static final int CACHE_BYTES = 8 * 1024 * 1024;
    private static final Object CACHE_WRITES = new Object();
    private static final AtomicInteger CACHE_EPOCH = new AtomicInteger();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_BYTES) {
        // Count logical ARGB pixels equally for SW/HW entries under one shared 8 MiB budget.
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getWidth() * bitmap.getHeight() * 4; }
    };
    // Assets change only when the app updates (and its process restarts). Bound failed lookups too.
    private static final Map<String, Boolean> MISSING = new LinkedHashMap<String, Boolean>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) { return size() > 128; }
    };
    private static Bitmap material;
    private static boolean materialRead;

    private GlassArtwork() { }

    /** Cache-only: binding a visible card never opens an asset or another app's resources. */
    static Drawable peek(Context context, String pkg, String label, int opacity) {
        return peek(context, pkg, label, opacity, false);
    }

    /** The mode is explicit so a software consumer can never accidentally receive a hardware bitmap. */
    static Drawable peek(Context context, String pkg, String label, int opacity, boolean hardware) {
        if (!validPackage(pkg)) return null;
        Bitmap bitmap = CACHE.get(IconBackgroundOpacity.bundledKey(pkg, opacity, hardware));
        if (bitmap == null) bitmap = CACHE.get(IconBackgroundOpacity.fallbackKey(pkg, label, opacity, hardware));
        return bitmap == null ? null : new BitmapDrawable(context.getResources(), bitmap);
    }

    /** Worker-only complete card; the original icon is requested only on a generated-card cache miss. */
    static int cacheToken() { return CACHE_EPOCH.get(); }

    static Drawable loadApp(Context context, String pkg, String label, Supplier<Drawable> originalIcon, int requestEpoch, int opacity) {
        return loadApp(context, pkg, label, originalIcon, requestEpoch, opacity, false);
    }

    static Drawable loadApp(Context context, String pkg, String label, Supplier<Drawable> originalIcon,
                            int requestEpoch, int opacity, boolean hardware) {
        Drawable cached = peek(context, pkg, label, opacity, hardware);
        if (cached != null) return cached;
        // The token belongs to the queued request, before its ResolveInfo can become outdated.
        if (requestEpoch != CACHE_EPOCH.get()) return null;
        Drawable prepared = load(context, pkg, opacity, hardware);
        if (prepared != null) return prepared;
        Drawable icon = null;
        try { if (originalIcon != null) icon = originalIcon.get(); }
        catch (RuntimeException unavailable) { /* The app name still identifies an unavailable icon. */ }
        Drawable rendered = fallback(context, icon, label, opacity);
        if (validPackage(pkg) && rendered instanceof BitmapDrawable) {
            Bitmap source = ((BitmapDrawable) rendered).getBitmap();
            Bitmap stored = toStorage(source, hardware);
            if (stored != source) rendered = new BitmapDrawable(context.getResources(), stored);
            synchronized (CACHE_WRITES) {
                if (requestEpoch == CACHE_EPOCH.get()) {
                    CACHE.put(IconBackgroundOpacity.fallbackKey(pkg, label, opacity, hardware),
                            ((BitmapDrawable) rendered).getBitmap());
                }
            }
        }
        return rendered;
    }

    /** Bundled cards are immutable; only generated icon/name cards change with another app's update. */
    static void invalidatePackage(String pkg) {
        synchronized (CACHE_WRITES) {
            CACHE_EPOCH.incrementAndGet();
            if (pkg == null) {
                CACHE.evictAll();
            } else {
                String prefix = "fallback:" + pkg + '\n';
                for (String key : CACHE.snapshot().keySet()) if (key.startsWith(prefix)) CACHE.remove(key);
            }
        }
    }

    /** Drop cached pixels under actual background memory pressure, without recycling view-owned bitmaps. */
    static void trimMemory() { invalidatePackage(null); }

    private static boolean validPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9') && c != '_' && c != '.') return false;
        }
        return true;
    }

    /** Each view gets its own Drawable bounds; decoded pixels alone are shared by the bounded cache. */
    static Drawable load(Context context, String pkg, int opacity) {
        return load(context, pkg, opacity, false);
    }

    static Drawable load(Context context, String pkg, int opacity, boolean hardware) {
        if (!validPackage(pkg)) return null;
        Bitmap bitmap = CACHE.get(IconBackgroundOpacity.bundledKey(pkg, opacity, hardware));
        if (bitmap == null) {
            synchronized (MISSING) { if (MISSING.get(pkg) != null) return null; }
            int epoch = CACHE_EPOCH.get();
            bitmap = loadBundled(context, pkg, opacity);
            if (bitmap == null) {
                synchronized (MISSING) { MISSING.put(pkg, true); }
                return null;
            }
            bitmap = toStorage(bitmap, hardware);
            synchronized (CACHE_WRITES) {
                if (epoch == CACHE_EPOCH.get()) CACHE.put(IconBackgroundOpacity.bundledKey(pkg, opacity, hardware), bitmap);
            }
        }
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    /** Worker-only composition; an unknown baked card stays intact if it has no separate layers. */
    private static Bitmap loadBundled(Context context, String pkg, int opacity) {
        if (IconBackgroundOpacity.clamp(opacity) == 100)
            return decode(context, "glass-apps/" + pkg + ".png");
        // At zero the final pixels are exactly the separate foreground. Avoid decoding
        // an invisible plate and allocating a second full-size composition bitmap.
        if (IconBackgroundOpacity.clamp(opacity) == 0) {
            Bitmap foreground = decode(context, "glass-icon-layers/foreground/" + pkg + ".png");
            return foreground != null ? foreground : decode(context, "glass-apps/" + pkg + ".png");
        }
        // Only the three native-proven intermediate levels reuse their private decoded plate.
        boolean inPlace = opacity == 25 || opacity == 50 || opacity == 75;
        Bitmap background = decode(context, "glass-icon-layers/background/" + pkg + ".png", 1, inPlace);
        Bitmap foreground = background == null ? null
                : decode(context, "glass-icon-layers/foreground/" + pkg + ".png");
        if (background == null || foreground == null) {
            if (background != null) background.recycle();
            return decode(context, "glass-apps/" + pkg + ".png");
        }
        Bitmap card = inPlace ? background : Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        card.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(card);
        try {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            if (inPlace) {
                canvas.drawColor(IconBackgroundOpacity.alpha(opacity) << 24 | 0x00FFFFFF, PorterDuff.Mode.DST_IN);
            } else {
                paint.setAlpha(IconBackgroundOpacity.alpha(opacity));
                canvas.drawBitmap(background, 0f, 0f, paint);
            }
            paint.setAlpha(255);
            canvas.drawBitmap(foreground, 0f, 0f, paint);
        } finally {
            canvas.setBitmap(null);
        }
        if (!inPlace) background.recycle();
        foreground.recycle();
        return card;
    }

    /** A newly installed app receives the same material, its original icon foreground and a clear name. */
    static Drawable fallback(Context context, Drawable originalIcon, String label, int opacity) {
        // This path runs only on the existing artwork workers, once per cache miss. Share no
        // Drawable state or sampled pixels with a view, and never tint the original logo itself.
        Bitmap logo = prepareIcon(originalIcon);
        int accent = iconAccent(logo);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        RectF card = new RectF(1f, 1f, WIDTH - 1f, HEIGHT - 1f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new LinearGradient(0, 0, WIDTH, HEIGHT,
                new int[]{0xFF414C61, 0xFF1D2A40, 0xFF1C2945}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(card, 34f, 34f, paint);
        paint.setShader(null);

        Bitmap texture = material(context);
        if (texture != null) {
            Rect crop = texture.getWidth() > WIDTH || texture.getHeight() > HEIGHT
                    ? new Rect(Math.round(texture.getWidth() * 0.014f), Math.round(texture.getHeight() * 0.087f),
                            Math.round(texture.getWidth() * 0.986f), Math.round(texture.getHeight() * 0.902f))
                    : new Rect(0, 0, texture.getWidth(), texture.getHeight());
            Path clip = new Path();
            clip.addRoundRect(card, 34f, 34f, Path.Direction.CW);
            int saved = canvas.save();
            canvas.clipPath(clip);
            canvas.drawBitmap(texture, crop, card, paint);
            canvas.restoreToCount(saved);
        }
        // Faint app identity remains in the plate, below the crisp untouched icon and name.
        paint.setColor(0x06000000 | accent);
        canvas.drawRoundRect(card, 34f, 34f, paint);
        paint.setColor(Color.WHITE);
        paint.setShader(new RadialGradient(0, 0, WIDTH * 0.85f,
                0x0B000000 | accent, accent, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(card, 34f, 34f, paint);
        paint.setShader(new RadialGradient(0, HEIGHT, WIDTH * 0.72f,
                0x06000000 | accent, accent, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(card, 34f, 34f, paint);
        paint.setShader(null);
        if (IconBackgroundOpacity.clamp(opacity) < 100) {
            canvas.drawColor(IconBackgroundOpacity.alpha(opacity) << 24 | 0x00FFFFFF, PorterDuff.Mode.DST_IN);
        }
        paint.setColor(Color.WHITE);
        if (logo != null) {
            canvas.drawBitmap(logo, (WIDTH - logo.getWidth()) / 2f, 28f, paint);
        }

        TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(26f);
        try { text.setTypeface(context.getResources().getFont(R.font.outfit_medium)); }
        catch (RuntimeException unavailable) { text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
        text.setShadowLayer(1.5f, 0, 1f, 0xA0000000);
        String name = TextUtils.ellipsize(label == null ? "" : label, text, WIDTH - 48f,
                TextUtils.TruncateAt.END).toString();
        canvas.drawText(name, (WIDTH - text.measureText(name)) / 2f, 166f, text);
        canvas.setBitmap(null);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    /** Convert only a fresh, private worker result. A failed hardware allocation leaves software intact. */
    private static Bitmap toStorage(Bitmap source, boolean hardware) {
        if (!hardware || source == null || source.getConfig() == Bitmap.Config.HARDWARE) return source;
        try {
            Bitmap copy = source.copy(Bitmap.Config.HARDWARE, false);
            if (copy == null) return source;
            source.recycle();
            return copy;
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            return source;
        }
    }

    /** Render once at the final icon size; bounds are restored even for an unavailable drawable. */
    private static Bitmap prepareIcon(Drawable originalIcon) {
        Drawable icon = originalIcon;
        if (icon instanceof AdaptiveIconDrawable) icon = ((AdaptiveIconDrawable) icon).getForeground();
        if (icon == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(84, 84, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Rect previous = new Rect(icon.getBounds());
        int iconWidth = icon.getIntrinsicWidth(), iconHeight = icon.getIntrinsicHeight();
        float ratio = iconWidth > 0 && iconHeight > 0 ? Math.min(84f / iconWidth, 84f / iconHeight) : 1f;
        int width = iconWidth > 0 && iconHeight > 0 ? Math.max(1, Math.round(iconWidth * ratio)) : 84;
        int height = iconWidth > 0 && iconHeight > 0 ? Math.max(1, Math.round(iconHeight * ratio)) : 84;
        int left = (84 - width) / 2, top = (84 - height) / 2;
        try {
            icon.setBounds(left, top, left + width, top + height);
            icon.draw(new Canvas(bitmap));
            return bitmap;
        } catch (RuntimeException unavailable) {
            return null;
        } finally { icon.setBounds(previous); }
    }

    /** Sparse alpha/chroma-weighted samples ignore transparent padding and white lettering. */
    private static int iconAccent(Bitmap icon) {
        if (icon == null) return 0x7386A7;
        long red = 0, green = 0, blue = 0, weights = 0;
        for (int y = 1; y < icon.getHeight(); y += 3) for (int x = 1; x < icon.getWidth(); x += 3) {
            int c = icon.getPixel(x, y), alpha = c >>> 24;
            int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (alpha < 32 || max < 40 || max - min < 28) continue;
            int weight = alpha * (max - min);
            red += (long)r * weight; green += (long)g * weight; blue += (long)b * weight;
            weights += weight;
        }
        return weights == 0 ? 0x7386A7
                : (int)(red / weights) << 16 | (int)(green / weights) << 8 | (int)(blue / weights);
    }

    /** Shared prepared material for static control surfaces; callers never mutate these pixels. */
    static synchronized Bitmap material(Context context) {
        if (!materialRead) {
            int sample = 1;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getAssets().open("glass-material.png")) {
                BitmapFactory.decodeStream(input, null, bounds);
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2;
            } catch (IOException | RuntimeException unavailable) { /* A simple painted material remains available. */ }
            material = decode(context, "glass-material.png", sample);
            materialRead = true;
        }
        return material;
    }

    private static Bitmap decode(Context context, String asset) {
        return decode(context, asset, 1);
    }

    private static Bitmap decode(Context context, String asset, int sampleSize) {
        return decode(context, asset, sampleSize, false);
    }

    private static Bitmap decode(Context context, String asset, int sampleSize, boolean mutable) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = mutable;
        options.inSampleSize = sampleSize;
        options.inScaled = false;
        options.inDensity = Bitmap.DENSITY_NONE;
        options.inTargetDensity = Bitmap.DENSITY_NONE;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = context.getAssets().open(asset)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap != null) bitmap.setDensity(Bitmap.DENSITY_NONE);
            return bitmap;
        } catch (IOException | RuntimeException unavailable) { return null; }
    }
}
