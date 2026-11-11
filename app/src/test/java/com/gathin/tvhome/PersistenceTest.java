package com.gathin.tvhome;

import android.content.SharedPreferences;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class PersistenceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void presetLookupMatchesKnownThemesAndIgnoresUnknown() {
        ThemePicker.Preset aurora = ThemePicker.findPreset("Aurora Glass");
        assertNotNull(aurora);
        assertEquals("aurora.jpg", aurora.asset);
        assertEquals("#070A14", aurora.background);
        assertSame(aurora, ThemePicker.findPreset("  aurora glass  "));

        ThemePicker.Preset midnight = ThemePicker.findPreset("Midnight Glass");
        assertNotNull(midnight);
        assertNull(midnight.asset);

        assertNotNull(ThemePicker.findPreset("Liquid Glass"));
        assertNull(ThemePicker.findPreset(null));
        assertNull(ThemePicker.findPreset(""));
        assertNull(ThemePicker.findPreset("Nonexistent Glass"));
    }

    @Test public void coldBootWithoutThemeRevisionPreservesOpacityAndThemeChoices() throws Exception {
        File dir = temporary.newFolder("external-files");
        File config = new File(dir, Config.FILE_NAME);
        try (FileOutputStream out = new FileOutputStream(config)) {
            out.write("{\"background_color\":\"#070A14\"}".getBytes(StandardCharsets.UTF_8));
        }

        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit()
                .putString("theme_signature", "999@12345:absent")
                .putString(ThemePicker.THEME_PRESET_KEY, "Aurora Glass")
                .putInt(Config.ICON_BACKGROUND_OPACITY_KEY, 50)
                .putInt("columns", 6)
                .putInt(Config.ROWS_BELOW_DOCK_KEY, 2)
                .putBoolean("animations", false)
                .putString(Config.WALLPAPER_CHOICE_KEY, "aurora.jpg")
                .commit();

        // Simulate cold boot where external config.json stamp differs and then vanishes temporarily.
        Config.syncThemeChoices(prefs, dir, config);
        assertTrue(config.delete());
        Config.syncThemeChoices(prefs, dir, config);

        assertEquals(50, prefs.getInt(Config.ICON_BACKGROUND_OPACITY_KEY, 100));
        assertEquals("Aurora Glass", prefs.getString(ThemePicker.THEME_PRESET_KEY, null));
        assertEquals(6, prefs.getInt("columns", 4));
        assertEquals(2, prefs.getInt(Config.ROWS_BELOW_DOCK_KEY, 0));
        assertFalse(prefs.getBoolean("animations", true));
        assertEquals("aurora.jpg", prefs.getString(Config.WALLPAPER_CHOICE_KEY, null));
    }

    @Test public void externalThemeRevisionPushPreservesOpacityWhileClearingPushedThemeOverrides() throws Exception {
        File dir = temporary.newFolder("external-push");
        File revision = new File(dir, "theme_revision");
        try (FileOutputStream out = new FileOutputStream(revision)) {
            out.write("push-1".getBytes(StandardCharsets.US_ASCII));
        }
        File config = new File(dir, Config.FILE_NAME);
        try (FileOutputStream out = new FileOutputStream(config)) {
            out.write("{\"background_color\":\"#080D19\"}".getBytes(StandardCharsets.UTF_8));
        }

        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit()
                .putString("theme_signature", "old:absent")
                .putString(ThemePicker.THEME_PRESET_KEY, "Aurora Glass")
                .putInt(Config.ICON_BACKGROUND_OPACITY_KEY, 50)
                .putInt("columns", 6)
                .putInt(Config.ROWS_BELOW_DOCK_KEY, 1)
                .putString(Config.WALLPAPER_CHOICE_KEY, "custom.jpg")
                .commit();

        Config.syncThemeChoices(prefs, dir, config);

        assertEquals(50, prefs.getInt(Config.ICON_BACKGROUND_OPACITY_KEY, 100));
        assertNull(prefs.getString(ThemePicker.THEME_PRESET_KEY, null));
        assertFalse(prefs.contains("columns"));
        assertFalse(prefs.contains(Config.ROWS_BELOW_DOCK_KEY));
        assertFalse(prefs.contains(Config.WALLPAPER_CHOICE_KEY));

        // Subsequent launch with unchanged push signature must keep any newly chosen settings.
        prefs.edit().putInt("columns", 5).commit();
        Config.syncThemeChoices(prefs, dir, config);
        assertEquals(5, prefs.getInt("columns", 4));
        assertEquals(50, prefs.getInt(Config.ICON_BACKGROUND_OPACITY_KEY, 100));
    }

    private static final class MemoryPreferences implements SharedPreferences {
        private final Map<String, Object> entries = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(entries); }
        @Override public String getString(String key, String defValue) {
            Object v = entries.get(key); return v instanceof String ? (String) v : defValue;
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
        @Override public int getInt(String key, int defValue) {
            Object v = entries.get(key); return v instanceof Integer ? (Integer) v : defValue;
        }
        @Override public long getLong(String key, long defValue) {
            Object v = entries.get(key); return v instanceof Long ? (Long) v : defValue;
        }
        @Override public float getFloat(String key, float defValue) {
            Object v = entries.get(key); return v instanceof Float ? (Float) v : defValue;
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = entries.get(key); return v instanceof Boolean ? (Boolean) v : defValue;
        }
        @Override public boolean contains(String key) { return entries.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> staged = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override public Editor putString(String key, String value) {
                if (value == null) return remove(key);
                removals.remove(key); staged.put(key, value); return this;
            }
            @Override public Editor putStringSet(String key, Set<String> values) { return this; }
            @Override public Editor putInt(String key, int value) { removals.remove(key); staged.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { removals.remove(key); staged.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { removals.remove(key); staged.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { removals.remove(key); staged.put(key, value); return this; }
            @Override public Editor remove(String key) { staged.remove(key); removals.add(key); return this; }
            @Override public Editor clear() { staged.clear(); removals.clear(); clear = true; return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                if (clear) entries.clear();
                for (String k : removals) entries.remove(k);
                entries.putAll(staged);
            }
        }
    }
}
