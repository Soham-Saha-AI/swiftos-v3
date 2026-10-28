package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Finite, retargetable feedback. It never owns clicks, long clicks, navigation or action timing. */
final class ControlMotion {
    private static final DecelerateInterpolator SETTLE = new DecelerateInterpolator(1.5f);
    private static final OvershootInterpolator FOCUS = new OvershootInterpolator(0.65f);
    private static final FloatPropertyCompat<View> SCALE = new FloatPropertyCompat<View>("controlScale") {
        @Override public float getValue(View view) { return view.getScaleX(); }
        @Override public void setValue(View view, float value) { view.setScaleX(value); view.setScaleY(value); }
    };

    private ControlMotion() { }

    /** Call from the existing focus callback, preserving its navigation/artwork hooks. */
    static void focus(View view, boolean focused, boolean animations, boolean reduced, boolean advanced) {
        focus(view, focused, animations, reduced, advanced, true);
    }

    static void focus(View view, boolean focused, boolean animations, boolean reduced, boolean advanced, boolean overshoot) {
        focus(view, focused, animations, reduced, advanced, overshoot, Motion.CONTROL);
    }

    static void appFocus(View view, boolean focused, boolean animations, boolean reduced, boolean advanced, boolean overshoot) {
        focus(view, focused, animations, reduced, advanced, overshoot, Motion.HOME_APP);
    }

    static void libraryFocus(View view, boolean focused, boolean animations, boolean reduced) {
        focus(view, focused, animations, reduced, true, false, Motion.LIBRARY_APP);
    }

    static void headerFocus(View view, boolean focused, boolean animations, boolean reduced, boolean advanced) {
        focus(view, focused, animations, reduced, advanced, false, Motion.HEADER);
    }

    private static void focus(View view, boolean focused, boolean animations, boolean reduced, boolean advanced, boolean overshoot, int profile) {
        State state = state(view, animations, reduced, advanced);
        state.profile = profile;
        state.focused = focused;
        state.overshoot = overshoot;
        state.pressed = false;
        animate(view, state, focused, false, true);
    }

    /** Observe before the existing key handler. False always leaves dispatch with its current owner. */
    static boolean key(View view, int key, KeyEvent event, boolean animations, boolean reduced, boolean advanced) {
        return key(view, key, event, animations, reduced, advanced, Motion.CONTROL);
    }

    static boolean appKey(View view, int key, KeyEvent event, boolean animations, boolean reduced, boolean advanced) {
        return key(view, key, event, animations, reduced, advanced, Motion.HOME_APP);
    }

    static boolean libraryKey(View view, int key, KeyEvent event, boolean animations, boolean reduced) {
        return key(view, key, event, animations, reduced, true, Motion.LIBRARY_APP);
    }

    static boolean headerKey(View view, int key, KeyEvent event, boolean animations, boolean reduced, boolean advanced) {
        return key(view, key, event, animations, reduced, advanced, Motion.HEADER);
    }

    private static boolean key(View view, int key, KeyEvent event, boolean animations, boolean reduced, boolean advanced, int profile) {
        if (key != KeyEvent.KEYCODE_DPAD_CENTER && key != KeyEvent.KEYCODE_ENTER
                && key != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
        State state = state(view, animations, reduced, advanced);
        state.profile = profile;
        boolean focused = Motion.feedbackFocus(profile, view.isFocused(), state.focused);
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            state.pressed = view.isEnabled();
            animate(view, state, focused, state.pressed);
        } else if ((event.getAction() == KeyEvent.ACTION_UP || event.isCanceled()) && state.pressed) {
            state.pressed = false;
            animate(view, state, focused, false);
        }
        return false;
    }

    /** Simple controls only: callers with an existing key handler must use focus/key explicitly. */
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Observer always returns false; View owns touch/click dispatch.
    static void bind(View view, boolean animations, boolean reduced) {
        Object existing = view.getTag(R.id.control_motion_state);
        boolean changed = !(existing instanceof State) || ((State) existing).animations != animations
                || ((State) existing).reduced != reduced;
        State bound = state(view, animations, reduced, false);
        if (bound.bound) {
            if (changed) focus(view, view.isFocused(), animations, reduced, false);
            return;
        }
        bound.bound = true;
        View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            focus(v, focused, bound.animations, bound.reduced, false);
            if (previous != null) previous.onFocusChange(v, focused);
        });
        view.setOnKeyListener((v, key, event) -> key(v, key, event, bound.animations, bound.reduced, false));
        view.setOnTouchListener((v, event) -> {
            State state = state(v, bound.animations, bound.reduced, false);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) state.pressed = v.isEnabled();
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) state.pressed = false;
            else if (action == MotionEvent.ACTION_MOVE && (event.getX() < 0 || event.getY() < 0
                    || event.getX() >= v.getWidth() || event.getY() >= v.getHeight())) state.pressed = false;
            else return false;
            animate(v, state, v.isFocused(), state.pressed);
            return false;
        });
        state(view, animations, reduced, false);
    }

    /** Recycled/detached controls cannot retain compression or an animation callback. */
    static void reset(View view) {
        Object value = view.getTag(R.id.control_motion_state);
        view.animate().cancel();
        if (value instanceof State) {
            ((State) value).pressed = false;
            ((State) value).focused = false;
            ((State) value).cancelSprings();
        }
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationY(0f);
        view.setTranslationZ(0f);
    }

    private static State state(View view, boolean animations, boolean reduced, boolean advanced) {
        Object value = view.getTag(R.id.control_motion_state);
        State state;
        if (value instanceof State) state = (State) value;
        else {
            state = new State(view);
            view.setTag(R.id.control_motion_state, state);
            view.addOnAttachStateChangeListener(state);
            if (view.isAttachedToWindow()) state.onViewAttachedToWindow(view);
        }
        state.animations = animations;
        state.reduced = reduced;
        state.advanced = advanced;
        return state;
    }

    private static void animate(View view, State state, boolean focused, boolean pressed) {
        animate(view, state, focused, pressed, false);
    }

    private static void animate(View view, State state, boolean focused, boolean pressed, boolean focusChange) {
        if (state.profile == Motion.HOME_APP) view.animate().cancel();
        boolean enabled = view.isEnabled();
        float scale = state.profile == Motion.HOME_APP
                ? Motion.appControlScale(state.animations, state.reduced, enabled, focused, pressed)
                : Motion.profileControlScale(state.profile, state.animations, state.reduced, enabled, focused, pressed);
        float lift = focused && enabled && state.animations && !state.reduced && state.advanced
                ? -Motion.FOCUS_LIFT_DP * view.getResources().getDisplayMetrics().density : 0f;
        if (!state.animations || state.reduced) {
            state.cancelSprings();
            view.animate().cancel();
            // Reduced motion retains the drawable's focus/pressed state without a spatial effect.
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setTranslationY(lift);
            view.setTranslationZ(0f);
            return;
        }
        if (Motion.usesSpring(state.profile)) {
            state.springTo(scale, lift, pressed);
            return;
        }
        view.animate().scaleX(scale).scaleY(scale).translationY(lift).translationZ(0f)
                .setDuration(Motion.appTransitionDuration(focusChange, focused, pressed))
                .setInterpolator(focused && !pressed && state.overshoot && focusChange ? FOCUS : SETTLE).start();
    }

    private static final class State implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnWindowFocusChangeListener {
        final View view;
        ViewTreeObserver observer;
        boolean animations, reduced, advanced, pressed, focused, bound;
        int profile;
        SpringAnimation scaleSpring, liftSpring;
        boolean overshoot = true;
        State(View view) { this.view = view; }

        void springTo(float scale, float lift, boolean pressed) {
            if (scaleSpring == null) {
                scaleSpring = new SpringAnimation(view, SCALE);
                liftSpring = new SpringAnimation(view, DynamicAnimation.TRANSLATION_Y);
                scaleSpring.setMinimumVisibleChange(Motion.CONTROL_MIN_SCALE);
                liftSpring.setMinimumVisibleChange(Motion.CONTROL_MIN_LIFT);
                scaleSpring.setSpring(new SpringForce());
                liftSpring.setSpring(new SpringForce());
            }
            float stiffness = pressed ? Motion.CONTROL_PRESS_STIFFNESS : Motion.CONTROL_STIFFNESS;
            float damping = pressed ? Motion.CONTROL_PRESS_DAMPING : Motion.CONTROL_DAMPING;
            scaleSpring.getSpring().setStiffness(stiffness).setDampingRatio(damping);
            liftSpring.getSpring().setStiffness(stiffness).setDampingRatio(damping);
            // A running spring keeps its position and velocity, including rapid focus reversals.
            if (scaleSpring.isRunning() || Math.abs(view.getScaleX() - scale) > Motion.CONTROL_MIN_SCALE / 2f)
                Motion.retarget(scaleSpring, scale);
            if (liftSpring.isRunning() || Math.abs(view.getTranslationY() - lift) > Motion.CONTROL_MIN_LIFT / 2f)
                Motion.retarget(liftSpring, lift);
            view.setTranslationZ(0f);
        }

        void cancelSprings() {
            if (scaleSpring == null) return;
            scaleSpring.cancel();
            liftSpring.cancel();
            scaleSpring.setStartVelocity(0f);
            liftSpring.setStartVelocity(0f);
        }
        @Override public void onViewAttachedToWindow(View v) {
            observer = v.getViewTreeObserver();
            observer.addOnWindowFocusChangeListener(this);
        }
        @Override public void onViewDetachedFromWindow(View v) {
            if (observer != null && observer.isAlive()) observer.removeOnWindowFocusChangeListener(this);
            observer = null;
            reset(v);
        }
        @Override public void onWindowFocusChanged(boolean hasFocus) {
            if (!hasFocus) {
                pressed = false;
                view.animate().cancel();
                cancelSprings();
                boolean feedbackFocused = Motion.feedbackFocus(profile, view.isFocused(), focused);
                float scale = profile == Motion.HOME_APP
                        ? Motion.appControlScale(animations, reduced, view.isEnabled(), feedbackFocused, false)
                        : Motion.profileControlScale(profile, animations, reduced, view.isEnabled(), feedbackFocused, false);
                view.setScaleX(scale);
                view.setScaleY(scale);
                if (profile != Motion.HOME_APP) view.setTranslationY(feedbackFocused && view.isEnabled()
                        && animations && !reduced && advanced
                        ? -Motion.FOCUS_LIFT_DP * view.getResources().getDisplayMetrics().density : 0f);
            }
        }
    }
}
