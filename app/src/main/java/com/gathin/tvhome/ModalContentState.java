package com.gathin.tvhome;

/** A modal hides foreground chrome without drawing it beneath clear glass or changing its layout. */
final class ModalContentState {
    interface Target {
        int visibility();
        float alpha();
        void visibility(int value);
        void alpha(float value);
    }
    private final Target target;
    private boolean suppressed;
    private int visibility;
    private float alpha;

    ModalContentState(Target target) { this.target = target; }

    void suppress(int invisible) {
        if (suppressed) return;
        visibility = target.visibility();
        alpha = target.alpha();
        suppressed = true;
        target.visibility(invisible);
    }

    /** Idempotent for Back, action launch, failed presentation, memory release and destruction. */
    void restore() {
        if (!suppressed) return;
        suppressed = false;
        target.alpha(alpha);
        target.visibility(visibility);
    }
}
