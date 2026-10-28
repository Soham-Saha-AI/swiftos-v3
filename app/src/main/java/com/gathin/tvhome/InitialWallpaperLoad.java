package com.gathin.tvhome;

/** One startup decode: a bounded first-draw gate, with late results still valid until cancelled. */
final class InitialWallpaperLoad {
    static final long WAIT_MS = 750;
    private volatile long generation;
    private volatile boolean pending;
    private long deadline;

    long begin(long now) {
        deadline = now + WAIT_MS;
        long token = ++generation;
        pending = true;
        return token;
    }

    boolean waiting(long now) { return pending && now < deadline; }

    boolean pending() { return pending; }

    /** Workers can stop between decode attempts after dismissal without touching views. */
    boolean current(long token) { return pending && token == generation; }

    boolean complete(long token) {
        if (!current(token)) return false;
        pending = false;
        return true;
    }

    void cancel() {
        pending = false;
        generation++;
    }
}
