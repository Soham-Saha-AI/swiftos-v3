package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class InitialWallpaperLoadTest {
    @Test public void ordinaryDecodeReleasesFirstDrawOnce() {
        InitialWallpaperLoad load = new InitialWallpaperLoad();
        long token = load.begin(100);
        assertTrue(load.waiting(100));
        assertTrue(load.waiting(849));
        assertTrue(load.complete(token));
        assertFalse(load.waiting(200));
        assertFalse(load.pending());
        assertFalse(load.complete(token));
    }

    @Test public void deadlineReleasesDrawButStillAllowsEventualWallpaper() {
        InitialWallpaperLoad load = new InitialWallpaperLoad();
        long token = load.begin(100);
        assertFalse(load.waiting(850));
        assertTrue(load.pending());
        assertTrue(load.current(token));
        assertTrue(load.complete(token));
        assertFalse(load.pending());
    }

    @Test public void stopAndCloseInvalidateLateResults() {
        InitialWallpaperLoad load = new InitialWallpaperLoad();
        long token = load.begin(100);
        load.cancel();
        load.cancel();
        assertFalse(load.waiting(100));
        assertFalse(load.current(token));
        assertFalse(load.complete(token));
    }

    @Test public void staleReplyCannotConsumeNewLifecycleRequest() {
        InitialWallpaperLoad load = new InitialWallpaperLoad();
        long old = load.begin(100);
        load.cancel();
        long next = load.begin(1000);
        assertFalse(load.complete(old));
        assertTrue(load.waiting(1500));
        assertTrue(load.current(next));
        assertTrue(load.complete(next));
    }
}
