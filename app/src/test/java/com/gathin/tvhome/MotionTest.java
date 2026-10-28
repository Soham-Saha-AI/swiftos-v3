package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringForce;
import androidx.dynamicanimation.animation.SpringFrames;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/** Reduced motion's spring: no bounce, and settled within 5 frames at 60 Hz for any move on a 1080p screen. */
public class MotionTest {

    @Test
    public void reducedSpringSettlesWithinFiveFrames() {
        for (float px : new float[]{50, 150, 300, 600, 1000, 1920}) {
            int frames = SpringFrames.toSettle(Motion.REDUCED_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY,
                    Motion.REDUCED_MIN_CHANGE, px);
            assertTrue(px + " px took " + frames + " frames", frames <= 5);
        }
    }

    @Test
    public void reducedAnimationsLastAtMost100ms() {
        assertTrue(Motion.REDUCED_MS <= 100);
    }

    @Test public void pressReleaseReturnsToCurrentFocusTarget() {
        assertEquals(1.04f * 0.985f, Motion.controlScale(true, false, true, true, true), 0.0001f);
        assertEquals(1.04f, Motion.controlScale(true, false, true, true, false), 0.0001f);
        assertEquals(1f, Motion.controlScale(true, false, true, false, false), 0.0001f);
    }

    @Test public void disabledAndReducedControlsNeverZoomOrCompress() {
        for (boolean focused : new boolean[]{false, true}) for (boolean pressed : new boolean[]{false, true}) {
            assertEquals(1f, Motion.controlScale(true, false, false, focused, pressed), 0f);
            assertEquals(1f, Motion.controlScale(true, true, true, focused, pressed), 0f);
            assertEquals(1f, Motion.controlScale(false, false, true, focused, pressed), 0f);
        }
    }

    @Test public void appFocusIsVisibleAndPressReleaseRetainsItsTarget() {
        assertEquals(1.12f, Motion.appFocusScale(true, false), 0f);
        assertEquals(1.04f, Motion.focusScale(true, false), 0f);
        assertEquals(1.12f * 0.985f, Motion.appControlScale(true, false, true, true, true), 0.0001f);
        assertEquals(1.12f, Motion.appControlScale(true, false, true, true, false), 0f);
        assertEquals(1f, Motion.appControlScale(true, false, true, false, false), 0f);
        assertEquals(0.985f, Motion.appControlScale(true, false, true, false, true), 0f);
    }

    @Test public void appFocusHonoursDisabledAnimationsReducedMotionAndDisabledControls() {
        for (boolean focused : new boolean[]{false, true}) for (boolean pressed : new boolean[]{false, true}) {
            assertEquals(1f, Motion.appControlScale(false, false, true, focused, pressed), 0f);
            assertEquals(1f, Motion.appControlScale(true, true, true, focused, pressed), 0f);
            assertEquals(1f, Motion.appControlScale(true, false, false, focused, pressed), 0f);
        }
        assertEquals(1f, Motion.appFocusScale(false, false), 0f);
        assertEquals(1f, Motion.appFocusScale(true, true), 0f);
    }

    @Test public void appReleaseSettlesFasterThanNewFocusWithoutChangingPressTiming() {
        assertEquals(170, Motion.appTransitionDuration(true, true, false));
        assertEquals(130, Motion.appTransitionDuration(true, false, false));
        assertEquals(70, Motion.appTransitionDuration(false, true, true));
        assertEquals(110, Motion.appTransitionDuration(false, true, false));
        assertEquals(110, Motion.appTransitionDuration(false, false, false));
    }

    @Test public void sharedSpringProfilesKeepHomeSeparateAndRespectMotionPreferences() {
        assertEquals(1.04f, Motion.controlScale(true, false, true, true, false), 0f);
        assertEquals(1.06f, Motion.profileScale(Motion.HEADER, true, false), 0f);
        assertEquals(1.12f, Motion.profileScale(Motion.LIBRARY_APP, true, false), 0f);
        org.junit.Assert.assertFalse(Motion.usesSpring(Motion.HOME_APP));
        for (int profile : new int[]{Motion.CONTROL, Motion.HEADER, Motion.LIBRARY_APP}) {
            assertTrue(Motion.usesSpring(profile));
            for (boolean focused : new boolean[]{false, true}) for (boolean pressed : new boolean[]{false, true}) {
                assertEquals(1f, Motion.profileControlScale(profile, false, false, true, focused, pressed), 0f);
                assertEquals(1f, Motion.profileControlScale(profile, true, true, true, focused, pressed), 0f);
                assertEquals(1f, Motion.profileControlScale(profile, true, false, false, focused, pressed), 0f);
            }
            float scale = Motion.profileScale(profile, true, false);
            assertEquals(scale * 0.985f, Motion.profileControlScale(profile, true, false, true, true, true), 0.0001f);
            assertEquals(scale, Motion.profileControlScale(profile, true, false, true, true, false), 0f);
        }
    }

    @Test public void sharedSpringsSettleWithoutIdleWorkWithin300ms() {
        for (float distance : new float[]{0.04f, 0.06f, 0.12f}) {
            int frames = SpringFrames.toSettle(Motion.CONTROL_STIFFNESS, Motion.CONTROL_DAMPING,
                    Motion.CONTROL_MIN_SCALE, distance);
            assertTrue("focus spring: " + frames, frames <= 18);
        }
        assertTrue(SpringFrames.toSettle(Motion.CONTROL_PRESS_STIFFNESS, Motion.CONTROL_PRESS_DAMPING,
                Motion.CONTROL_MIN_SCALE, 1.12f * 0.015f) <= 12);
        assertTrue(SpringFrames.toSettle(Motion.CONTROL_STIFFNESS, Motion.CONTROL_DAMPING,
                Motion.CONTROL_MIN_LIFT, 4.5f) <= 18);
    }

    @Test public void rapidFocusReversalRetainsMomentumWithoutPositionJump() {
        float[] sample = SpringFrames.reverseDuringTravel(Motion.CONTROL_STIFFNESS, Motion.CONTROL_DAMPING, 0.06f);
        assertTrue(sample[1] > 0f);
        assertTrue(sample[2] > sample[0]); // One continuous frame of the momentum already in flight.
        assertTrue(sample[2] > sample[3]); // Cancelling/resetting velocity would abruptly reverse it.
        assertTrue(Math.abs(sample[2] - sample[0]) < 0.03f);
    }

    @Test public void listOwnedKeyFeedbackKeepsTheSelectedRowsFocusCue() {
        assertTrue(Motion.feedbackFocus(Motion.CONTROL, false, true));
        org.junit.Assert.assertFalse(Motion.feedbackFocus(Motion.CONTROL, false, false));
        org.junit.Assert.assertFalse(Motion.feedbackFocus(Motion.HOME_APP, false, true));
        assertTrue(Motion.feedbackFocus(Motion.HOME_APP, true, false));
        boolean focused = Motion.feedbackFocus(Motion.CONTROL, false, true);
        assertEquals(1.04f * 0.985f,
                Motion.profileControlScale(Motion.CONTROL, true, false, true, focused, true), 0.0001f);
        assertEquals(1.04f,
                Motion.profileControlScale(Motion.CONTROL, true, false, true, focused, false), 0f);
    }
}
