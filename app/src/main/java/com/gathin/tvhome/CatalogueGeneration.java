package com.gathin.tvhome;

import java.util.concurrent.atomic.AtomicInteger;

/** One publication token per request. Cancellation never makes an older snapshot current again. */
final class CatalogueGeneration {
    private final AtomicInteger current = new AtomicInteger();
    int next() { return current.incrementAndGet(); }
    boolean accepts(int token) { return token == current.get(); }
}
