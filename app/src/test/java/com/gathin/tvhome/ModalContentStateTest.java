package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModalContentStateTest {
    @Test public void closingRestoresExactVisibilityAndAlpha() {
        for (int visibility : new int[]{0, 4, 8}) {
            Foreground view = new Foreground(visibility, 0.65f);
            ModalContentState state = new ModalContentState(view);
            state.suppress(4);
            assertEquals(4, view.visibility);
            view.alpha = 0.2f;
            state.restore();
            assertEquals(visibility, view.visibility);
            assertEquals(0.65f, view.alpha, 0f);
        }
    }
    @Test public void repeatedPresentDoesNotCaptureAlreadyHiddenState() {
        Foreground view = new Foreground(0, 1f);
        ModalContentState state = new ModalContentState(view);
        state.suppress(4);
        state.suppress(4);
        state.restore();
        assertEquals(0, view.visibility);
    }
    @Test public void failureCleanupAndLaterOpenUseFreshSnapshot() {
        Foreground view = new Foreground(0, 1f);
        ModalContentState state = new ModalContentState(view);
        try {
            state.suppress(4);
            throw new IllegalStateException("presentation failed");
        } catch (IllegalStateException failure) { state.restore(); }
        state.restore(); // release/destroy after failed presentation
        assertEquals(0, view.visibility);
        view.visibility = 8;
        view.alpha = 0.4f;
        state.suppress(4);
        state.restore();
        assertEquals(8, view.visibility);
        assertEquals(0.4f, view.alpha, 0f);
    }
    @Test public void closingBeforePresentationLeavesForegroundUntouched() {
        Foreground view = new Foreground(0, 0.8f);
        ModalContentState state = new ModalContentState(view);
        state.restore();
        assertEquals(0, view.visibility);
        assertEquals(0.8f, view.alpha, 0f);
    }
    private static final class Foreground implements ModalContentState.Target {
        int visibility;
        float alpha;
        Foreground(int visibility, float alpha) { this.visibility = visibility; this.alpha = alpha; }
        public int visibility() { return visibility; }
        public float alpha() { return alpha; }
        public void visibility(int value) { visibility = value; }
        public void alpha(float value) { alpha = value; }
    }
}
