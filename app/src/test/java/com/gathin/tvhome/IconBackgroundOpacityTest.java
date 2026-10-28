package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class IconBackgroundOpacityTest {
    @Test public void unsafePreferencesClampAndEndpointsAreExact() {
        assertEquals(0, IconBackgroundOpacity.alpha(-15));
        assertEquals(128, IconBackgroundOpacity.alpha(50));
        assertEquals(255, IconBackgroundOpacity.alpha(140));
    }
    @Test public void sharedCacheNeverAliasesDifferentPlateStrengthsOrLabels() {
        assertNotEquals(IconBackgroundOpacity.bundledKey("app", 0), IconBackgroundOpacity.bundledKey("app", 100));
        assertNotEquals(IconBackgroundOpacity.fallbackKey("app", "A", 50), IconBackgroundOpacity.fallbackKey("app", "B", 50));
        assertNotEquals(IconBackgroundOpacity.fallbackKey("app", "A", 0), IconBackgroundOpacity.fallbackKey("app", "A", 100));
        assertEquals(IconBackgroundOpacity.bundledKey("app", 100), IconBackgroundOpacity.bundledKey("app", 999));
    }
    @Test public void packageInvalidationPrefixCoversEveryOpacityAndLabel() {
        for (int percent : new int[]{0,25,50,75,100}) {
            assertTrue(IconBackgroundOpacity.fallbackKey("app", "name", percent).startsWith("fallback:app\n"));
            assertFalse(IconBackgroundOpacity.fallbackKey("app.other", "name", percent).startsWith("fallback:app\n"));
        }
    }
    @Test public void hardwareCacheModesStayDistinctWithoutChangingInvalidationPrefix() {
        assertNotEquals(IconBackgroundOpacity.bundledKey("app", 50, false),
                IconBackgroundOpacity.bundledKey("app", 50, true));
        assertNotEquals(IconBackgroundOpacity.fallbackKey("app", "name", 50, false),
                IconBackgroundOpacity.fallbackKey("app", "name", 50, true));
        assertTrue(IconBackgroundOpacity.fallbackKey("app", "name", 50, true).startsWith("fallback:app\n"));
    }
    @Test public void hardwareCardsRequireSupportedAcceleratedSurfaceAndAllCpuConsumersOff() {
        assertTrue(IconBackgroundOpacity.hardwareEligible(26, true, false, false, false));
        assertTrue(IconBackgroundOpacity.hardwareEligible(35, true, false, false, false));
        assertFalse(IconBackgroundOpacity.hardwareEligible(25, true, false, false, false));
        assertFalse(IconBackgroundOpacity.hardwareEligible(26, false, false, false, false));
        assertFalse(IconBackgroundOpacity.hardwareEligible(26, true, true, false, false));
        assertFalse(IconBackgroundOpacity.hardwareEligible(26, true, false, true, false));
        assertFalse(IconBackgroundOpacity.hardwareEligible(26, true, false, false, true));
    }
    @Test public void detachedTilesKeepTheirWindowModeAndAnAttachedSoftwareWindowVetoesTheDeclaration() {
        assertTrue(IconBackgroundOpacity.windowAccelerated(false, false, true));
        assertFalse(IconBackgroundOpacity.windowAccelerated(false, false, false));
        assertTrue(IconBackgroundOpacity.windowAccelerated(true, true, true));
        assertTrue(IconBackgroundOpacity.windowAccelerated(true, true, false));
        assertFalse(IconBackgroundOpacity.windowAccelerated(true, false, true));
        assertFalse(IconBackgroundOpacity.windowAccelerated(true, false, false));
    }
}
