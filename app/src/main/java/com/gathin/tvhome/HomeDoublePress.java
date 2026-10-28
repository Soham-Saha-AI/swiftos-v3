package com.gathin.tvhome;

/** Recognizes pairs of native Home launches without delaying the ordinary first press. */
final class HomeDoublePress {
    static final long WINDOW_MS = 400L;

    private long firstPressAt = -1L;

    /** Returns true only for the second physical Home press in a pair. */
    boolean onHome(long now, boolean fromPhysicalHomeKey) {
        if (!fromPhysicalHomeKey || now < 0L) {
            clear();
            return false;
        }
        if (firstPressAt >= 0L && now >= firstPressAt && now - firstPressAt <= WINDOW_MS) {
            clear();
            return true;
        }
        // An expired pair or regressed clock starts a new pair with this press.
        firstPressAt = now;
        return false;
    }

    void clear() {
        firstPressAt = -1L;
    }
}
