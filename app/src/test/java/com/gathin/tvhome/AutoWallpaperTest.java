package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

public class AutoWallpaperTest {
    private static final long NOW = 1_800_000_000_000L;

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void mapsOnlySupportedWeatherFamilies() {
        assertEquals("clear", category(0, true));
        assertEquals("night", category(0, false));
        assertEquals("night", category(1, false));
        assertEquals("cloud", category(2, true));
        assertEquals("cloud", category(48, true));
        assertEquals("rain", category(55, true));
        assertEquals("rain", category(97, false));
        assertNull(category(71, true));
        assertNull(category(777, true));
    }

    @Test public void staleOrMissingReadingKeepsCurrentWallpaper() {
        WeatherReading stale = WeatherReading.create("Test", 10, 0, true,
                NOW - 31 * 60_000L, NOW - 31 * 60_000L, NOW);
        assertNotNull(stale);
        assertTrue(stale.isUsable(NOW));
        assertFalse(stale.isFresh(NOW));
        assertNull(AutoWallpaper.categoryFor(null, NOW));
        assertNull(AutoWallpaper.categoryFor(stale, NOW));
    }

    @Test public void freshBoundaryDoesNotUseSixHourWidgetFallback() {
        WeatherReading reading = WeatherReading.create("Test", 10, 0, true,
                NOW - WeatherReading.FRESH_MS, NOW - WeatherReading.FRESH_MS, NOW);
        assertNotNull(reading);
        assertEquals("clear", AutoWallpaper.categoryFor(reading, NOW - 1));
        assertNull(AutoWallpaper.categoryFor(reading, NOW));
    }

    @Test public void selectsOnlyChangedCategoryWithReadableNonemptyFile() throws Exception {
        File folder = temporary.newFolder("auto-wallpapers");
        AutoWallpaper policy = new AutoWallpaper(folder);
        WeatherReading clear = reading(0, true);
        assertNull(policy.select(clear, NOW, null));

        File image = new File(folder, "clear.jpg");
        try (FileOutputStream out = new FileOutputStream(image)) { out.write(1); }
        AutoWallpaper.Selection selected = policy.select(clear, NOW, null);
        assertNotNull(selected);
        assertEquals("clear", selected.category);
        assertEquals(image, selected.file);
        assertNull(policy.select(clear, NOW, "clear"));
        assertNull(policy.select(reading(71, true), NOW, "clear"));
    }

    @Test public void publicPhotoWinsAndMissingPublicPhotoFallsBackToLegacy() throws Exception {
        File publicPhotos = temporary.newFolder("public-weather");
        File legacy = temporary.newFolder("legacy-weather");
        File legacyPhoto = new File(legacy, "clear.jpg");
        try (FileOutputStream out = new FileOutputStream(legacyPhoto)) { out.write(1); }
        AutoWallpaper policy = new AutoWallpaper(publicPhotos, legacy);
        assertEquals(legacyPhoto, policy.select(reading(0,true), NOW, null).file);
        File publicPhoto = new File(publicPhotos, "clear.jpg");
        try (FileOutputStream out = new FileOutputStream(publicPhoto)) { out.write(1); }
        assertEquals(publicPhoto, policy.select(reading(0,true), NOW, null).file);
        assertTrue(publicPhoto.delete());
        assertEquals(legacyPhoto, policy.select(reading(0,true), NOW, null).file);
        assertTrue(legacyPhoto.delete());
        assertNull(policy.select(reading(0,true), NOW, null));
    }

    private static String category(int code, boolean day) {
        return AutoWallpaper.categoryFor(reading(code, day), NOW);
    }

    private static WeatherReading reading(int code, boolean day) {
        return WeatherReading.create("Test", 10, code, day, NOW, NOW, NOW);
    }
}
