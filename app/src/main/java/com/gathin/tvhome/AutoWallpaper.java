package com.gathin.tvhome;

import android.content.Context;

import java.io.File;

/** Selects a locally installed weather wallpaper. It never fetches or copies images. */
final class AutoWallpaper {
    private static final String PREFS = "auto_wallpaper_v1";
    private static final String ENABLED = "enabled";

    private final File directory;
    private final File legacyDirectory;
    private final LocalWallpaperPolicy local;

    AutoWallpaper(File directory) {
        this(directory, null);
    }

    AutoWallpaper(File directory, File legacyDirectory) {
        this(directory, legacyDirectory, null);
    }

    AutoWallpaper(File directory, File legacyDirectory, File localDirectory) {
        this.directory = directory;
        this.legacyDirectory = legacyDirectory;
        this.local = new LocalWallpaperPolicy(localDirectory);
    }

    void invalidate() { local.invalidate(); }

    static boolean isEnabled(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED, enabled).apply();
    }

    /** Returns the supported photo family for a fresh reading, or null to keep the current image. */
    static String categoryFor(WeatherReading reading, long now) {
        if (reading == null || !reading.isFresh(now)) return null;
        switch (reading.weatherCode) {
            case 0:
            case 1:
                return reading.isDay ? "clear" : "night";
            case 2:  // Partly cloudy
            case 3:  // Overcast
            case 45:
            case 48: // Fog
                return "cloud";
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82:
            case 95: case 96: case 97: case 99:
                return "rain";
            default:
                // Snow and unrecognized conditions preserve the current wallpaper.
                return null;
        }
    }

    /** Null means no fresh supported condition, no category change, or no usable local image. */
    Selection select(WeatherReading reading, long now, String currentlyDisplayedCategory) {
        Selection chosen=local.select(reading,now);
        if(chosen!=null)return chosen;
        String category = categoryFor(reading, now);
        if (category == null || category.equals(currentlyDisplayedCategory)) return null;
        File image = localImage(directory, category);
        if (image == null) image = localImage(legacyDirectory, category);
        if (image == null) return null;
        return new Selection(category, image);
    }

    private static File localImage(File directory, String category) {
        if (directory == null) return null;
        try {
            File image = new File(directory, category + ".jpg");
            return image.isFile() && image.canRead() && image.length() > 0 ? image : null;
        } catch (SecurityException unavailable) { return null; }
    }

    private static String imageIdentity(String category, File file) {
        String path = file.getAbsolutePath();
        try { return category + '\n' + path + '\n' + file.length() + '\n' + file.lastModified(); }
        catch (SecurityException revoked) { return category + '\n' + path + "\nunavailable"; }
    }

    static final class Selection {
        final String category;
        final File file;
        final String identity;

        Selection(String category, File file) {
            this.category = category;
            this.file = file;
            this.identity = imageIdentity(category, file);
        }
    }
}
