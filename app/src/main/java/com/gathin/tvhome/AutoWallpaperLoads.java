package com.gathin.tvhome;

/** Main-thread request state. One decode at a time; only the latest visible request may publish. */
final class AutoWallpaperLoads {
    private boolean active;
    private boolean closed;
    private long generation;
    private String displayed;
    private String displayedIdentity;
    private AutoWallpaper.Selection desired;
    private Request inFlight;

    void start() { if (!closed) active = true; }

    void stop() {
        active = false;
        desired = null;
        generation++;
    }

    void close() {
        stop();
        closed = true;
    }

    String displayedCategory() { return displayed; }
    void forgetDisplayed() { displayed=null;displayedIdentity=null; }

    void request(AutoWallpaper.Selection selection) {
        // An inactive callback must not latch a category and suppress the first request after resume.
        if (!active || closed) return;
        String next = selection == null ? null : selection.identity;
        String previous = desired == null ? null : desired.identity;
        if (next == null ? previous == null : next.equals(previous)) return;
        generation++;
        desired = selection;
    }

    Request begin() {
        if (!active || closed || inFlight != null || desired == null
                || desired.identity.equals(displayedIdentity)) return null;
        inFlight = new Request(generation, desired);
        return inFlight;
    }

    /** True only when a successful decode is still the requested, visible category. */
    boolean complete(Request request, boolean decoded) {
        if (inFlight != request) return false;
        inFlight = null;
        if (!active || closed || request.generation != generation || desired == null) return false;
        desired = null;
        if (!decoded) return false;
        displayed = request.selection.category;
        displayedIdentity = request.selection.identity;
        return true;
    }

    static final class Request {
        final long generation;
        final AutoWallpaper.Selection selection;

        Request(long generation, AutoWallpaper.Selection selection) {
            this.generation = generation;
            this.selection = selection;
        }
    }
}
