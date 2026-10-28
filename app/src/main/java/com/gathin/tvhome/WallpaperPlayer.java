package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Debug;
import android.os.SystemClock;
import android.os.Trace;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final Context context;
    private final FrameLayout root;
    private final ImageView image;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    private final AutoWallpaperLoads autoLoads = new AutoWallpaperLoads();
    private long lifecycleGeneration;
    private AutoWallpaper autoPolicy;
    private WeatherReading autoReading;
    private boolean autoLookupRunning;
    private boolean autoLookupPending;
    private final Runnable autoBoundary = () -> applyAutoPolicy(autoPolicy, autoReading);
    private final InitialWallpaperLoad initialLoad = new InitialWallpaperLoad();
    private final boolean initialFade;
    private ViewTreeObserver.OnPreDrawListener firstDrawGate;
    private long initialStarted;
    private final Runnable firstDrawTimeout = this::onFirstDrawTimeout;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.context = context.getApplicationContext();
        this.root = root;
        initialFade = config.animations && !config.reducedMotion;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        boolean autoEnabled = AutoWallpaper.isEnabled(context);
        if ((config.wallpaperEnabled && !files.isEmpty()) || autoEnabled) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (config.wallpaperEnabled && !files.isEmpty()) showFirst();
        }
    }

    /** Overlap the first decode with Home setup, retaining the wallpaper on the ordinary first frame. */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        File first = WallpaperRotation.find(files, previous);
        if (first == null) first = WallpaperRotation.pick(files, previous, order, random);
        File selected = first;
        List<File> candidates = new ArrayList<>(files);
        Random choices = new Random(random.nextLong());
        initialStarted = SystemClock.uptimeMillis();
        long token = initialLoad.begin(initialStarted);
        long generation = lifecycleGeneration;
        firstDrawGate = () -> {
            if (initialLoad.waiting(SystemClock.uptimeMillis())) return false;
            releaseFirstDraw(initialLoad.pending() ? "timeout" : "ready");
            return true;
        };
        root.getViewTreeObserver().addOnPreDrawListener(firstDrawGate);
        root.postDelayed(firstDrawTimeout, InitialWallpaperLoad.WAIT_MS);
        decoder.execute(() -> {
            long began = SystemClock.uptimeMillis();
            long cpuBegan = Debug.threadCpuTimeNanos();
            Trace.beginSection("SwiftOS.firstWallpaperDecode");
            List<File> failed = new ArrayList<>();
            File next = selected;
            try {
                while (next != null && initialLoad.current(token)) {
                    File decodedFile = next;
                    Bitmap bitmap = decode(decodedFile);
                    if (bitmap != null) {
                        if (!initialLoad.current(token)) { bitmap.recycle(); return; }
                        long decodedMs = SystemClock.uptimeMillis() - began;
                        long cpuMs = (Debug.threadCpuTimeNanos() - cpuBegan) / 1_000_000;
                        root.post(() -> publishFirst(token, generation, decodedFile, bitmap, failed, previous, decodedMs, cpuMs));
                        return;
                    }
                    failed.add(next);
                    candidates.remove(next);
                    next = WallpaperRotation.pick(candidates, previous, order, choices);
                }
                long decodedMs = SystemClock.uptimeMillis() - began;
                long cpuMs = (Debug.threadCpuTimeNanos() - cpuBegan) / 1_000_000;
                root.post(() -> publishFirst(token, generation, null, null, failed, previous, decodedMs, cpuMs));
            } finally {
                Trace.endSection();
            }
        });
    }

    private void publishFirst(long token, long generation, File file, Bitmap bitmap,
                              List<File> failed, String previous, long decodedMs, long cpuMs) {
        boolean late = firstDrawGate == null;
        if (!initialLoad.complete(token) || closed || generation != lifecycleGeneration || current != null) {
            if (bitmap != null) bitmap.recycle();
            releaseFirstDraw("cancelled");
            return;
        }
        files.removeAll(failed);
        if (bitmap != null) {
            show(bitmap);
            if (late && initialFade) {
                image.setAlpha(0f);
                image.animate().alpha(1f).setDuration(220).withEndAction(() -> {
                    if (current == bitmap) root.setBackground(null);
                }).start();
            } else root.setBackground(null);
            displayed = file.getName();
            showCaption(false);
            if (!displayed.equals(previous)) preferences.edit().putString("image", displayed)
                    .putLong("changed", System.currentTimeMillis()).apply();
        }
        Log.i("SwiftOSWallpaper", "initial decode_ms=" + decodedMs + " decode_cpu_ms=" + cpuMs + " ready_ms="
                + (SystemClock.uptimeMillis() - initialStarted) + " late=" + late + " success=" + (bitmap != null));
        releaseFirstDraw(bitmap != null ? "ready" : "unreadable");
        root.invalidate();
        if (active && !AutoWallpaper.isEnabled(context)) update();
    }

    private void releaseFirstDraw(String outcome) {
        if (firstDrawGate == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(firstDrawGate);
        firstDrawGate = null;
        root.removeCallbacks(firstDrawTimeout);
        Log.i("SwiftOSWallpaper", "initial gate_ms=" + (SystemClock.uptimeMillis() - initialStarted)
                + " outcome=" + outcome);
    }

    private void onFirstDrawTimeout() {
        if (firstDrawGate != null && !initialLoad.waiting(SystemClock.uptimeMillis())) {
            releaseFirstDraw(initialLoad.pending() ? "timeout" : "ready");
            root.invalidate();
        }
    }

    void start() {
        if (closed) return;
        active = true;
        autoLoads.start();
        if (autoPolicy != null) { autoPolicy.invalidate(); applyAutoPolicy(autoPolicy, autoReading); }
        if (!AutoWallpaper.isEnabled(context)) update();
    }

    void stop() {
        active = false;
        initialLoad.cancel();
        releaseFirstDraw("stopped");
        image.animate().cancel();
        image.setAlpha(1f);
        if (current != null) root.setBackground(null);
        lifecycleGeneration++;
        autoLoads.stop();
        root.removeCallbacks(autoBoundary);
        autoLookupPending=false;
        if (AutoWallpaper.isEnabled(context) && current != null) {
            Bitmap previous=current;current=null;image.setImageDrawable(null);image.postDelayed(previous::recycle,500);
            autoLoads.forgetDisplayed();
        }
    }

    void close() {
        active = false;
        closed = true;
        initialLoad.cancel();
        releaseFirstDraw("closed");
        lifecycleGeneration++;
        autoLoads.close();
        root.removeCallbacks(autoBoundary);
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || initialLoad.pending() || loading || files.isEmpty() || AutoWallpaper.isEnabled(context)) return;
        String previous = preferences.getString("image", "");
        File selected = WallpaperRotation.find(files, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(files, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        long generation = lifecycleGeneration;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active || generation != lifecycleGeneration || AutoWallpaper.isEnabled(context)) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    String currentAutoCategory() {
        return autoLoads.displayedCategory();
    }

    /** Selection, directory indexing and file metadata all stay on the existing decoder thread. */
    void applyAutoPolicy(AutoWallpaper policy, WeatherReading reading) {
        autoPolicy=policy;autoReading=reading;
        if(policy==null || !active || closed || !AutoWallpaper.isEnabled(context)) return;
        root.removeCallbacks(autoBoundary);
        java.util.Calendar next=java.util.Calendar.getInstance();long now=next.getTimeInMillis();
        int hour=next.get(java.util.Calendar.HOUR_OF_DAY);int boundary=hour<6?6:hour<10?10:hour<17?17:hour<21?21:30;
        next.set(java.util.Calendar.HOUR_OF_DAY,boundary%24);next.set(java.util.Calendar.MINUTE,0);next.set(java.util.Calendar.SECOND,0);next.set(java.util.Calendar.MILLISECOND,0);
        if(boundary>=24)next.add(java.util.Calendar.DATE,1);
        root.postDelayed(autoBoundary,Math.max(1000,next.getTimeInMillis()-now));
        autoLookupPending=true;
        if(autoLookupRunning)return;
        runAutoLookup();
    }

    private void runAutoLookup() {
        if(!autoLookupPending || !active || closed)return;
        autoLookupPending=false;autoLookupRunning=true;
        AutoWallpaper policy=autoPolicy;WeatherReading reading=autoReading;long generation=lifecycleGeneration;
        decoder.execute(() -> {
            AutoWallpaper.Selection chosen;
            try { chosen=policy.select(reading,System.currentTimeMillis(),null); }
            catch (RuntimeException unavailable) { chosen=null; }
            final AutoWallpaper.Selection selection=chosen;
            root.post(() -> {
                autoLookupRunning=false;
                if(active && !closed && generation==lifecycleGeneration && !autoLookupPending)applyAuto(selection);
                runAutoLookup();
            });
        });
    }

    /** Decodes an externally installed category off the UI thread; the current image survives failures. */
    void applyAuto(AutoWallpaper.Selection selection) {
        if (closed || !active || !AutoWallpaper.isEnabled(context)) return;
        autoLoads.request(selection);
        decodeAuto();
    }

    private void decodeAuto() {
        AutoWallpaperLoads.Request request = autoLoads.begin();
        if (request == null) return;
        decoder.execute(() -> {
            long decodeBegan=SystemClock.uptimeMillis();long decodeCpu=Debug.threadCpuTimeNanos();
            Bitmap bitmap = decode(request.selection.file, true);
            Log.i("SwiftOSWallpaper", "auto decode_ms="+(SystemClock.uptimeMillis()-decodeBegan)
                    +" decode_cpu_ms="+((Debug.threadCpuTimeNanos()-decodeCpu)/1_000_000)
                    +" bitmap_bytes="+(bitmap==null?0:bitmap.getAllocationByteCount()));
            root.post(() -> {
                if (!AutoWallpaper.isEnabled(context)) autoLoads.stop();
                if (autoLoads.complete(request, bitmap != null)) {
                    show(bitmap);
                    root.setBackground(null);
                    displayed = request.selection.file.getName();
                    showCaption(false);
                } else if (bitmap != null) {
                    bitmap.recycle();
                } else {
                    Log.w("TvHome.Wallpaper", "Could not decode auto wallpaper: " + request.selection.file.getName());
                }
                decodeAuto();
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(context.getResources().getFont(R.font.outfit_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        FrameLayout foreground = root.findViewById(R.id.homeContent);
        (foreground != null ? foreground : root).addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        image.animate().cancel();
        image.setAlpha(1f);
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        return decode(file, false);
    }

    private Bitmap decode(File file, boolean automatic) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0 || automatic;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0 || automatic) {
                Canvas canvas = new Canvas(decoded);
                try {
                    if (dimColor != 0) canvas.drawColor(dimColor);
                    if (automatic) {
                        // Bright skies need a stable dark area behind the clock/date. Bake it into this
                        // decoded image once; manual themes and the installed JPEGs remain unchanged.
                        Paint shade = new Paint();
                        float end = decoded.getHeight() * 0.58f;
                        shade.setShader(new LinearGradient(0, 0, 0, end,
                                new int[] {0xB3000000, 0x8C000000, 0x00000000},
                                new float[] {0f, 0.20f / 0.58f, 1f}, Shader.TileMode.CLAMP));
                        canvas.drawRect(0, 0, decoded.getWidth(), end, shade);
                    }
                } finally {
                    // Release the native raster's pixel reference before copying/recycling the bitmap.
                    canvas.setBitmap(null);
                }
            }
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
