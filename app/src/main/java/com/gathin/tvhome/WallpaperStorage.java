package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Read-only access to user-visible weather photos. No startup requests, writes or background scans. */
final class WallpaperStorage {
    static final int PICKER_PERMISSION = 7401;
    static final int AUTO_PERMISSION = 7402;
    static final String CHOICE_PREFIX = "weather:";
    private WallpaperStorage() { }

    static File weatherDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SwiftOS/Weather");
    }

    static String[] permissionsForApi(int api) {
        if (api >= 34) return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"};
        if (api >= 33) return new String[]{"android.permission.READ_MEDIA_IMAGES"};
        return new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
    }

    static boolean hasReadAccess(Context context) {
        for (String permission : permissionsForApi(Build.VERSION.SDK_INT)) {
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true;
        }
        return false;
    }

    /** Called only from a photo picker click or an explicit Auto-enable click. */
    static boolean requestReadAccess(Activity activity, int request) {
        // Partial access may be reselected whenever the user reopens the photo picker.
        if (activity.checkSelfPermission(permissionsForApi(Build.VERSION.SDK_INT)[0])
                == PackageManager.PERMISSION_GRANTED) return false;
        activity.requestPermissions(permissionsForApi(Build.VERSION.SDK_INT), request);
        return true;
    }

    static boolean isPublicChoice(String choice) {
        return choice != null && choice.startsWith(CHOICE_PREFIX);
    }

    /** A single filename prevents arbitrary paths; canonical containment also rejects symlink escapes. */
    static File resolve(File directory, String choice) {
        if (directory == null || !isPublicChoice(choice)) return null;
        String name = choice.substring(CHOICE_PREFIX.length());
        if (name.isEmpty() || name.startsWith(".") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return null;
        try {
            File base = directory.getCanonicalFile();
            File file = new File(base, name).getCanonicalFile();
            return base.equals(file.getParentFile()) && readableImage(file) ? file : null;
        } catch (IOException | SecurityException unavailable) { return null; }
    }

    static boolean readableImage(File file) {
        if (file == null) return false;
        try {
            return file.canRead() && isImageName(file.getName()) && file.isFile() && file.length() > 0 && file.length() <= 10 * 1024 * 1024;
        } catch (SecurityException unavailable) { return false; }
    }

    private static boolean isImageName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    static List<File> images(File directory) {
        List<File> result = new ArrayList<>();
        try {
            for (File image : WallpaperRotation.images(directory)) {
                File safe = resolve(directory, CHOICE_PREFIX + image.getName());
                if (safe != null) result.add(safe);
            }
        } catch (SecurityException unavailable) { /* Permission revocation leaves the private picker available. */ }
        return result;
    }

    static String choice(File image) { return CHOICE_PREFIX + image.getName(); }
}
