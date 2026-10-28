package com.gathin.tvhome;

/** Keeps an opening's reveal intent across superseding catalogue refreshes. */
final class LibraryLoads {
    private volatile int latest;
    // Mutated only on the UI thread; workers only read the generation.
    private boolean revealPending;
    int start(boolean reveal) {
        if (reveal) revealPending = true;
        return ++latest;
    }
    int current() { return latest; }
    boolean isLatest(int token) { return token == latest; }
    boolean takeReveal(int token) {
        if (!isLatest(token)) return false;
        boolean reveal = revealPending;
        revealPending = false;
        return reveal;
    }
    void cancel() { revealPending = false; ++latest; }
}
