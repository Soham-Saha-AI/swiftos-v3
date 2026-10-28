package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Reduced motion (config.xml reduced_motion): the one spring and the one time limit every animation uses then. */
final class Motion {
    static final float APP_FOCUS_SCALE = 1.12f;
    static final int CONTROL = 0, HOME_APP = 1, LIBRARY_APP = 2, HEADER = 3;
    static final float CONTROL_STIFFNESS = 850f;
    static final float CONTROL_PRESS_STIFFNESS = 1500f;
    static final float CONTROL_DAMPING = 0.78f;
    static final float CONTROL_PRESS_DAMPING = 0.92f;
    static final float CONTROL_MIN_SCALE = 0.001f;
    static final float CONTROL_MIN_LIFT = 0.15f;

    /** The longest any reduced-motion animation runs, in milliseconds. */
    static final int REDUCED_MS = 90;
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    static final float REDUCED_STIFFNESS = 15000f;
    /** The spring stops once it is within this many pixels of its target. */
    static final float REDUCED_MIN_CHANGE = 2f;
    /** Small optical lift; shared by card geometry and the separate clear rim. */
    static final float FOCUS_LIFT_DP = 1.5f;

    static float focusScale(boolean animations, boolean reducedMotion) {
        return animations && !reducedMotion ? 1.04f : 1f;
    }

    /** App artwork needs a clear remote-focus cue; other controls keep their quieter scale. */
    static float appFocusScale(boolean animations, boolean reducedMotion) {
        return animations && !reducedMotion ? APP_FOCUS_SCALE : 1f;
    }

    static float controlScale(boolean animations, boolean reduced, boolean enabled, boolean focused, boolean pressed) {
        return (enabled && focused ? focusScale(animations, reduced) : 1f)
                * (enabled && pressed && animations && !reduced ? 0.985f : 1f);
    }

    static float appControlScale(boolean animations, boolean reduced, boolean enabled, boolean focused, boolean pressed) {
        return (enabled && focused ? appFocusScale(animations, reduced) : 1f)
                * (enabled && pressed && animations && !reduced ? 0.985f : 1f);
    }

    /** A release settles back to the current focus, rather than replaying the focus bounce. */
    static int appTransitionDuration(boolean focusChange, boolean focused, boolean pressed) {
        return pressed ? 70 : !focusChange ? 110 : focused ? 170 : 130;
    }

    static float profileScale(int profile, boolean animations, boolean reduced) {
        if (!animations || reduced) return 1f;
        if (profile == HOME_APP || profile == LIBRARY_APP) return APP_FOCUS_SCALE;
        return profile == HEADER ? 1.06f : focusScale(animations, reduced);
    }

    static boolean usesSpring(int profile) { return profile != HOME_APP; }

    /** ListView-selected rows have logical focus while their list owns Android focus/key dispatch. */
    static boolean feedbackFocus(int profile, boolean actual, boolean logical) {
        return actual || profile != HOME_APP && logical;
    }

    static float profileControlScale(int profile, boolean animations, boolean reduced,
                                     boolean enabled, boolean focused, boolean pressed) {
        return (enabled && focused ? profileScale(profile, animations, reduced) : 1f)
                * (enabled && pressed && animations && !reduced ? 0.985f : 1f);
    }

    /**
     * AndroidX 1.0.0 cancel retains its pending destination. A stopped spring's first call starts it,
     * and the second replaces that retained destination before Choreographer can deliver a frame.
     * A running spring receives only one retarget, retaining continuous position and velocity.
     */
    static void retarget(SpringAnimation spring, float target) {
        boolean restarting = !spring.isRunning();
        spring.animateToFinalPosition(target);
        if (restarting) spring.animateToFinalPosition(target);
    }

    private Motion() {
    }

    static void reduce(SpringAnimation spring) {
        spring.setSpring(new SpringForce().setStiffness(REDUCED_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE);
    }
}
