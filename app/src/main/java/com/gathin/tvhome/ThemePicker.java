package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

/** Small, offline theme chooser. Only appearance changes; layout, apps and remote mappings survive. */
final class ThemePicker {
    private static final String TAG = "TvHome.ThemePicker";
    // Config's signature includes the config file stamp. A local appearance change acknowledges
    // that stamp itself so Config.load does not mistake it for a full tools/theme.sh reset.
    private static final String SIGNATURE_KEY = "theme_signature";
    private static final int CONFIG_LIMIT = 64 * 1024;
    private static final int IMAGE_LIMIT = 16 * 1024 * 1024;
    private static final Object APPLY_LOCK = new Object();
    private static final Preset[] PRESETS = {
            new Preset("Aurora Glass", "aurora.jpg", "#070A14", 0, 0xFF5267C8, 0xFFB67AB5),
            new Preset("Midnight Glass", null, "#080D19", 0, 0xFF263249, 0xFF080D19),
            new Preset("Ocean Glass", "ocean.jpg", "#07121D", 12, 0xFF3995AF, 0xFFDFA565),
            new Preset("Sunset Glass", "sunset.jpg", "#160E22", 8, 0xFFB77FBD, 0xFF3C315B),
            new Preset("Valley Glass", "valley.jpg", "#0D1614", 12, 0xFFB38B52, 0xFF45675A),
            new Preset("Liquid Glass", "liquid.png", "#040917", 8, 0xFF182C54, 0xFF6A5EA9)
    };

    private ThemePicker() {}

    /** The callback runs on the main thread after all files and preferences have been saved. */
    static Dialog show(Activity activity, Runnable onApplied) {
        if (activity.isFinishing() || activity.isDestroyed()) return null;
        Config motionConfig = Config.load(activity);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View homeContent = activity.findViewById(R.id.homeContent);
        ModalContentState backdrop = homeContent == null ? null : new ModalContentState(new ModalContentState.Target() {
            @Override public int visibility() { return homeContent.getVisibility(); }
            @Override public float alpha() { return homeContent.getAlpha(); }
            @Override public void visibility(int value) { homeContent.setVisibility(value); }
            @Override public void alpha(float value) { homeContent.setAlpha(value); }
        });
        dialog.setOnDismissListener(ignored -> {
            if (backdrop != null) backdrop.restore();
        });
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 18));
        panel.setBackgroundResource(R.drawable.glass_panel);

        TextView title = text(activity, "Change theme", 23, Color.WHITE);
        title.setTypeface(activity.getResources().getFont(R.font.inter_semibold));
        panel.addView(title);
        TextView hint = text(activity, "Built-in glass looks. Select a theme and press OK.", 13, 0xBFFFFFFF);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(0, dp(activity, 6), 0, dp(activity, 12));
        panel.addView(hint, hintParams);

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFocusable(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        // Leave room for the shared focus scale and border on every edge.
        scroll.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
        scroll.addView(rows);
        int availableRowsHeight = Math.max(dp(activity, 104),
                activity.getResources().getDisplayMetrics().heightPixels - dp(activity, 180));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(dp(activity, 49 * (PRESETS.length + 1) + 8), availableRowsHeight)));
        TextView footer = text(activity, "Saved on this TV  ·  Back to cancel", 12, 0x8FFFFFFF);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(activity, 10);
        panel.addView(footer, footerParams);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);

        int current = currentPreset(activity);
        boolean autoEnabled = AutoWallpaper.isEnabled(activity);
        hint.setText("Current: " + (autoEnabled ? "Auto wallpaper" : current >= 0 ? PRESETS[current].label : "Custom appearance")
                + "\nChoose a look and press OK to apply.");
        final boolean[] applying = {false};
        View autoRow = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((TextView) autoRow.findViewById(R.id.title)).setText("Auto wallpaper");
        ((TextView) autoRow.findViewById(R.id.status)).setText(autoEnabled ? "On" : "Off");
        autoRow.findViewById(R.id.dot).setVisibility(View.GONE);
        ImageView autoIcon = autoRow.findViewById(R.id.icon);
        GradientDrawable autoSwatch = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF7995D6, 0xFFB981BA, 0xFFE9AD76});
        autoSwatch.setCornerRadius(dp(activity, 6));
        autoSwatch.setStroke(dp(activity, 1), 0x77FFFFFF);
        autoIcon.setImageTintList(null);
        autoIcon.setImageDrawable(autoSwatch);
        autoRow.setContentDescription("Auto wallpaper, " + (autoEnabled ? "on, turn off" : "off, follow current weather"));
        autoRow.setOnClickListener(v -> {
            if (applying[0]) return;
            if (!autoEnabled && !WallpaperStorage.hasReadAccess(activity)) {
                AutoWallpaper.setEnabled(activity, true);
                dialog.dismiss();
                WallpaperStorage.requestReadAccess(activity, WallpaperStorage.AUTO_PERMISSION);
                return;
            }
            AutoWallpaper.setEnabled(activity, !autoEnabled);
            dialog.dismiss();
            Toast.makeText(activity, autoEnabled ? "Auto wallpaper off" : "Auto wallpaper on", Toast.LENGTH_SHORT).show();
            if (onApplied != null) onApplied.run();
        });
        bindRowControls(autoRow, motionConfig);
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(-1, dp(activity, 46));
        autoParams.bottomMargin = dp(activity, 3);
        rows.addView(autoRow, autoParams);
        for (int i = 0; i < PRESETS.length; i++) {
            final Preset preset = PRESETS[i];
            View row = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            TextView label = row.findViewById(R.id.title);
            label.setText(preset.label);
            TextView status = row.findViewById(R.id.status);
            status.setText(!autoEnabled && i == current ? "Current" : "");
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            ImageView icon = row.findViewById(R.id.icon);
            GradientDrawable swatch = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {preset.swatchStart, preset.swatchEnd});
            swatch.setCornerRadius(dp(activity, 6));
            swatch.setStroke(dp(activity, 1), 0x77FFFFFF);
            icon.setImageTintList(null);
            icon.setImageDrawable(swatch);
            row.setContentDescription(preset.label + (!autoEnabled && i == current ? ", current theme" : ", apply theme"));
            row.setOnClickListener(v -> {
                if (applying[0]) return;
                applying[0] = true;
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                footer.setText("Applying " + preset.label + "…");
                for (int n = 0; n < rows.getChildCount(); n++) rows.getChildAt(n).setEnabled(false);
                Context app = activity.getApplicationContext();
                new Thread(() -> {
                    Exception failure = null;
                    try {
                        apply(app, preset);
                        // A failed manual apply leaves the previous automatic choice intact.
                        AutoWallpaper.setEnabled(app, false);
                    } catch (IOException | JSONException | RuntimeException e) {
                        failure = e;
                        Log.w(TAG, "Could not apply " + preset.label, e);
                    }
                    final Exception result = failure;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (result == null) {
                            dialog.dismiss();
                            Toast.makeText(activity, preset.label + " applied", Toast.LENGTH_SHORT).show();
                            if (onApplied != null) onApplied.run();
                        } else {
                            applying[0] = false;
                            dialog.setCancelable(true);
                            dialog.setCanceledOnTouchOutside(true);
                            footer.setText("Could not save theme. Select a theme to retry.");
                            for (int n = 0; n < rows.getChildCount(); n++) rows.getChildAt(n).setEnabled(true);
                            v.requestFocus();
                            Toast.makeText(activity, "Could not save theme. Please try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                }, "ThemePicker-apply").start();
            });
            bindRowControls(row, motionConfig);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(activity, 46));
            rowParams.bottomMargin = dp(activity, 3);
            rows.addView(row, rowParams);
        }

        // Keep only the wallpaper/ambient behind this clear glass panel, as in App Library.
        if (backdrop != null) backdrop.suppress(View.INVISIBLE);
        try {
            dialog.show();
        } catch (RuntimeException failure) {
            if (backdrop != null) backdrop.restore();
            throw failure;
        }
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(dp(activity, 470), activity.getResources().getDisplayMetrics().widthPixels
                    - dp(activity, 48)), ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.48f;
            attributes.windowAnimations = 0;
            window.setAttributes(attributes);
        }
        rows.getChildAt(autoEnabled ? 0 : current >= 0 ? current + 1 : 1).requestFocus();
        return dialog;
    }

    private static void bindRowControls(View row, Config config) {
        // Opening from OK-down must not activate a row on that key's orphan OK-up.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            ControlMotion.key(v, code, event, config.animations, config.reducedMotion, config.advancedEffects);
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                    && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean activate = pressed[0] && !event.isCanceled();
                pressed[0] = false;
                if (activate) v.performClick();
            }
            return true;
        });
        row.setOnFocusChangeListener((v, focused) -> {
            pressed[0] = false;
            ControlMotion.focus(v, focused, config.animations, config.reducedMotion, config.advancedEffects);
        });
    }

    private static TextView text(Context context, String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(context.getResources().getFont(R.font.outfit_regular));
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int currentPreset(Context context) {
        File dir = Config.dir(context);
        if (dir == null) return -1;
        try {
            JSONObject config = readConfig(new File(dir, Config.FILE_NAME));
            SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            String choice = prefs.getString(Config.WALLPAPER_CHOICE_KEY, null);
            if (choice != null && !"none".equals(choice) && !Config.WALLPAPER_NAME.equals(choice)) return -1;
            if ("none".equals(choice) || !config.optBoolean("wallpaper", true)) {
                return "#080D19".equalsIgnoreCase(config.optString("background_color")) ? 1 : -1;
            }
            if (choice == null && config.optInt("wallpaper_rotation_minutes", 0) > 0) return -1;
            File wallpaper = new File(dir, Config.WALLPAPER_NAME);
            if (!wallpaper.isFile() || wallpaper.length() > IMAGE_LIMIT) return -1;
            byte[] picture = read(wallpaper, IMAGE_LIMIT);
            // Existing installs keep their PNG wallpaper unchanged; recognize its exact legacy bytes.
            if (picture.length == 1454588 && android.util.Base64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(picture), android.util.Base64.NO_WRAP)
                    .equals("VPcbIC7JUIpj4yndm24cLOKtKRMZsUtqOnHBeybjSG0=")) return PRESETS.length - 1;
            for (int i = 0; i < PRESETS.length; i++) {
                if (PRESETS[i].asset == null) continue;
                try (InputStream in = context.getAssets().open("themes/" + PRESETS[i].asset)) {
                    if (Arrays.equals(picture, read(in, IMAGE_LIMIT))) return i;
                }
            }
        } catch (IOException | JSONException | NoSuchAlgorithmException | RuntimeException e) {
            Log.w(TAG, "Could not identify current local theme", e);
        }
        return -1;
    }

    private static void apply(Context context, Preset preset) throws IOException, JSONException {
        synchronized (APPLY_LOCK) {
            File dir = Config.dir(context);
            if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
                throw new IOException("Theme storage unavailable");
            }
            File configFile = new File(dir, Config.FILE_NAME);
            File wallpaperFile = new File(dir, Config.WALLPAPER_NAME);
            byte[] oldConfig = configFile.exists() ? read(configFile, CONFIG_LIMIT) : null;
            byte[] oldWallpaper = wallpaperFile.exists() ? read(wallpaperFile, IMAGE_LIMIT) : null;
            JSONObject config = oldConfig == null ? new JSONObject() : parse(oldConfig);
            // Deliberate visual allowlist. Never replace the overlay with a preset's whole config.
            config.put("background_color", preset.background);
            config.put("wallpaper", preset.asset != null);
            config.put("wallpaper_rotation_minutes", 0);
            config.put("wallpaper_dim", preset.dim);
            config.put("wallpaper_true_color", true);
            config.put("wallpaper_captions", false);
            config.put("sky_intensity", 0);
            config.put("dock_glass", true);
            config.put("dock_opacity", 78);
            config.put("animations", true);
            config.put("advanced_effects", false);
            config.put("reduced_motion", false);
            config.put("focus_overshoot", false);
            config.put("cinematic_home", false);
            config.put("ambient_glow", false);
            config.put("app_glow", false);
            byte[] newConfig = config.toString(2).getBytes(StandardCharsets.UTF_8);
            if (newConfig.length > CONFIG_LIMIT) throw new IOException("Theme config is too large");
            byte[] newWallpaper = null;
            if (preset.asset != null) {
                try (InputStream in = context.getAssets().open("themes/" + preset.asset)) {
                    newWallpaper = read(in, IMAGE_LIMIT);
                }
                if (newWallpaper.length == 0) throw new IOException("Empty bundled wallpaper");
            }

            SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            Map<String, ?> before = prefs.getAll();
            boolean wallpaperChanged = false, configChanged = false, preferencesAttempted = false;
            try {
                // Midnight leaves the old picture in place, disabled by its visual config.
                if (newWallpaper != null) {
                    atomicWrite(wallpaperFile, newWallpaper);
                    wallpaperChanged = true;
                }
                atomicWrite(configFile, newConfig);
                configChanged = true;
                String signature = configFile.length() + "@" + configFile.lastModified()
                        + ":" + revisionToken(new File(dir, "theme_revision"));
                preferencesAttempted = true;
                if (!prefs.edit().remove(Config.WALLPAPER_CHOICE_KEY)
                        .putString(SIGNATURE_KEY, signature).commit()) {
                    throw new IOException("Could not save theme choices");
                }
            } catch (IOException | RuntimeException failure) {
                // Each rename is atomic. If a later step fails, restore the previous pair before
                // returning to Home, including a failed SharedPreferences commit's in-memory edit.
                if (wallpaperChanged) restore(wallpaperFile, oldWallpaper, failure);
                if (configChanged) restore(configFile, oldConfig, failure);
                if (preferencesAttempted) {
                    SharedPreferences.Editor rollback = prefs.edit();
                    restoreString(rollback, Config.WALLPAPER_CHOICE_KEY, before);
                    restoreString(rollback, SIGNATURE_KEY, before);
                    if (configFile.isFile()) {
                        rollback.putString(SIGNATURE_KEY, configFile.length() + "@" + configFile.lastModified()
                                + ":" + revisionToken(new File(dir, "theme_revision")));
                    }
                    if (!rollback.commit()) failure.addSuppressed(new IOException("Choice rollback failed"));
                } else if (configChanged && configFile.isFile()) {
                    // Restoring bytes gives the config a new mtime; retain every on-TV choice.
                    if (!prefs.edit().putString(SIGNATURE_KEY, configFile.length() + "@" + configFile.lastModified()
                            + ":" + revisionToken(new File(dir, "theme_revision"))).commit()) {
                        failure.addSuppressed(new IOException("Signature rollback failed"));
                    }
                }
                throw failure;
            }
        }
    }

    private static void restoreString(SharedPreferences.Editor editor, String key, Map<String, ?> before) {
        Object value = before.get(key);
        if (value instanceof String) editor.putString(key, (String) value);
        else editor.remove(key);
    }

    private static void restore(File target, byte[] bytes, Throwable failure) {
        try {
            if (bytes != null) atomicWrite(target, bytes);
            else if (target.exists() && !target.delete()) throw new IOException("Could not remove " + target.getName());
        } catch (IOException | RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    /** A flushed sibling followed by checked POSIX rename never exposes a half-written file. */
    private static void atomicWrite(File target, byte[] bytes) throws IOException {
        File temporary = File.createTempFile(".theme-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }
            try {
                Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            } catch (ErrnoException e) {
                throw new IOException("Could not replace " + target.getName(), e);
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) Log.w(TAG, "Could not remove theme temporary file");
        }
    }

    private static JSONObject readConfig(File file) throws IOException, JSONException {
        return file.exists() ? parse(read(file, CONFIG_LIMIT)) : new JSONObject();
    }

    private static JSONObject parse(byte[] bytes) throws JSONException {
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.startsWith("\uFEFF")) value = value.substring(1);
        return new JSONObject(value);
    }

    private static byte[] read(File file, int limit) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return read(in, limit);
        }
    }

    private static byte[] read(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > limit) throw new IOException("Theme file is too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Keep this byte-for-byte compatible with Config.revisionToken; do not advance the token. */
    private static String revisionToken(File file) {
        if (!file.isFile() || file.length() > 64) return "absent";
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return n > 0 ? new String(bytes, 0, n, StandardCharsets.US_ASCII) : "absent";
        } catch (IOException | RuntimeException e) {
            return "absent";
        }
    }

    private static final class Preset {
        final String label, asset, background;
        final int dim, swatchStart, swatchEnd;

        Preset(String label, String asset, String background, int dim, int swatchStart, int swatchEnd) {
            this.label = label;
            this.asset = asset;
            this.background = background;
            this.dim = dim;
            this.swatchStart = swatchStart;
            this.swatchEnd = swatchEnd;
        }
    }
}
