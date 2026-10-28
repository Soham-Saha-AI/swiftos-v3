package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeDoublePressTest {
    @Test public void firstPressStaysOrdinaryAndSecondPressOpensLibrary() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(1_000L, true));
        assertTrue(presses.onHome(1_250L, true));
    }

    @Test public void anExpiredSecondPressStartsAnotherPair() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(1_000L, true));
        assertFalse(presses.onHome(1_401L, true));
        assertTrue(presses.onHome(1_700L, true));
    }

    @Test public void thirdPressIsOrdinaryAndCanStartTheNextPair() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(100L, true));
        assertTrue(presses.onHome(200L, true));
        assertFalse(presses.onHome(300L, true));
        assertTrue(presses.onHome(400L, true));
    }

    @Test public void aProgrammaticHomeLaunchCancelsAnArmedPair() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(100L, true));
        assertFalse(presses.onHome(150L, false));
        assertFalse(presses.onHome(200L, true));
        assertTrue(presses.onHome(300L, true));
    }

    @Test public void programmaticLaunchesNeverArmAPair() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(100L, false));
        assertFalse(presses.onHome(200L, false));
        assertFalse(presses.onHome(300L, true));
    }

    @Test public void theWindowIncludesItsBoundaryAndSupportsUptimeZero() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(0L, true));
        assertTrue(presses.onHome(HomeDoublePress.WINDOW_MS, true));
        assertFalse(presses.onHome(1_000L, true));
        assertFalse(presses.onHome(1_001L + HomeDoublePress.WINDOW_MS, true));
    }

    @Test public void aRegressedClockDiscardsTheFutureArm() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(1_000L, true));
        assertFalse(presses.onHome(900L, true));
        assertTrue(presses.onHome(1_100L, true));
    }

    @Test public void invalidTimeCancelsThePair() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(100L, true));
        assertFalse(presses.onHome(-1L, true));
        assertFalse(presses.onHome(200L, true));
    }

    @Test public void clearCancelsAPairWhenLeavingTheRelevantFlow() {
        HomeDoublePress presses = new HomeDoublePress();
        assertFalse(presses.onHome(100L, true));
        presses.clear();
        assertFalse(presses.onHome(200L, true));
        assertTrue(presses.onHome(300L, true));
    }
}
