package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class AutoWallpaperLoadsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void inactiveCallbackDoesNotSuppressFirstResumeRequest() {
        AutoWallpaperLoads loads = new AutoWallpaperLoads();
        loads.request(selection("clear"));
        assertNull(loads.begin());
        loads.start();
        loads.request(selection("clear"));
        AutoWallpaperLoads.Request request = loads.begin();
        assertNotNull(request);
        assertTrue(loads.complete(request, true));
        assertEquals("clear", loads.displayedCategory());
    }

    @Test public void duplicateWeatherDoesNotStartAnotherDecode() {
        AutoWallpaperLoads loads = started();
        loads.request(selection("cloud"));
        AutoWallpaperLoads.Request request = loads.begin();
        loads.request(selection("cloud"));
        assertNull(loads.begin());
        assertTrue(loads.complete(request, true));
        loads.request(selection("cloud"));
        assertNull(loads.begin());
    }

    @Test public void stopThenResumeRejectsOldResultBeforeStartingNewDecode() {
        AutoWallpaperLoads loads = started();
        loads.request(selection("clear"));
        AutoWallpaperLoads.Request old = loads.begin();
        loads.stop();
        loads.start();
        loads.request(selection("clear"));
        assertNull(loads.begin()); // The old worker still owns the single in-flight slot.
        assertFalse(loads.complete(old, true));
        assertNull(loads.displayedCategory());
        AutoWallpaperLoads.Request resumed = loads.begin();
        assertNotNull(resumed);
        assertTrue(loads.complete(resumed, true));
    }

    @Test public void closeRejectsResultsAndCannotBeRestarted() {
        AutoWallpaperLoads loads = started();
        loads.request(selection("night"));
        AutoWallpaperLoads.Request request = loads.begin();
        loads.close();
        assertFalse(loads.complete(request, true));
        loads.start();
        loads.request(selection("rain"));
        assertNull(loads.begin());
        assertNull(loads.displayedCategory());
    }

    @Test public void latestCategoryWinsWithoutQueuingExtraWorkers() {
        AutoWallpaperLoads loads = started();
        loads.request(selection("clear"));
        AutoWallpaperLoads.Request clear = loads.begin();
        loads.request(selection("cloud"));
        loads.request(selection("rain"));
        assertNull(loads.begin());
        assertFalse(loads.complete(clear, true));
        AutoWallpaperLoads.Request latest = loads.begin();
        assertEquals("rain", latest.selection.category);
        assertTrue(loads.complete(latest, true));
        assertEquals("rain", loads.displayedCategory());
        assertNull(loads.begin());
    }

    @Test public void returningToDisplayedCategoryCancelsPendingChange() {
        AutoWallpaperLoads loads = started();
        display(loads, "clear");
        loads.request(selection("rain"));
        AutoWallpaperLoads.Request rain = loads.begin();
        loads.request(selection("clear"));
        assertFalse(loads.complete(rain, true));
        assertEquals("clear", loads.displayedCategory());
        assertNull(loads.begin());
    }

    @Test public void failedDecodeKeepsCurrentAndRetriesOnlyOnNewCallback() {
        AutoWallpaperLoads loads = started();
        display(loads, "clear");
        loads.request(selection("cloud"));
        assertFalse(loads.complete(loads.begin(), false));
        assertEquals("clear", loads.displayedCategory());
        assertNull(loads.begin()); // No loop on a missing or corrupt image.
        loads.request(selection("cloud"));
        assertTrue(loads.complete(loads.begin(), true));
        assertEquals("cloud", loads.displayedCategory());
    }

    @Test public void unsupportedOrMissingImageCancelsPendingAndRetainsDisplayed() {
        AutoWallpaperLoads loads = started();
        display(loads, "night");
        loads.request(selection("rain"));
        AutoWallpaperLoads.Request rain = loads.begin();
        loads.request(null);
        assertFalse(loads.complete(rain, true));
        assertEquals("night", loads.displayedCategory());
        assertNull(loads.begin());
    }

    @Test public void replacingSameCategoryAndPathQueuesNewImage() throws Exception {
        File file = temporary.newFile("clear.jpg");
        Files.write(file.toPath(), new byte[]{1});
        AutoWallpaperLoads loads = started();
        loads.request(new AutoWallpaper.Selection("clear", file));
        assertTrue(loads.complete(loads.begin(), true));
        Files.write(file.toPath(), new byte[]{1, 2});
        loads.request(new AutoWallpaper.Selection("clear", file));
        AutoWallpaperLoads.Request replacement = loads.begin();
        assertNotNull(replacement);
        assertTrue(loads.complete(replacement, true));
        assertEquals("clear", loads.displayedCategory());
        loads.request(new AutoWallpaper.Selection("clear", file));
        assertNull(loads.begin());
    }

    @Test public void newerSameCategoryFileRejectsOldDecode() throws Exception {
        File file = temporary.newFile("cloud.jpg");
        Files.write(file.toPath(), new byte[]{1});
        AutoWallpaperLoads loads = started();
        loads.request(new AutoWallpaper.Selection("cloud", file));
        AutoWallpaperLoads.Request old = loads.begin();
        assertTrue(file.setLastModified(file.lastModified() + 10_000));
        loads.request(new AutoWallpaper.Selection("cloud", file));
        assertFalse(loads.complete(old, true));
        assertNull(loads.displayedCategory());
        AutoWallpaperLoads.Request replacement = loads.begin();
        assertNotNull(replacement);
        assertTrue(loads.complete(replacement, true));
        assertEquals("cloud", loads.displayedCategory());
    }

    private static AutoWallpaperLoads started() {
        AutoWallpaperLoads loads = new AutoWallpaperLoads();
        loads.start();
        return loads;
    }

    private static void display(AutoWallpaperLoads loads, String category) {
        loads.request(selection(category));
        assertTrue(loads.complete(loads.begin(), true));
    }

    private static AutoWallpaper.Selection selection(String category) {
        return new AutoWallpaper.Selection(category, new File(category + ".jpg"));
    }
}
