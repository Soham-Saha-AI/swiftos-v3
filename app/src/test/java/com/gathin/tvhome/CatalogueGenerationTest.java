package com.gathin.tvhome;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class CatalogueGenerationTest {
    @Test public void latestRequestWinsEvenWhenOldWorkerFinishesLast() throws Exception {
        CatalogueGeneration generation = new CatalogueGeneration();
        int old = generation.next();
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean stalePublished = new AtomicBoolean(true);
        Thread worker = new Thread(() -> {
            try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
            stalePublished.set(generation.accepts(old));
        });
        worker.start();
        int newest = generation.next();
        assertTrue(generation.accepts(newest));
        release.countDown();
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertFalse(stalePublished.get());
    }
    @Test public void memoryPressureOrDestroyInvalidatesWithoutAnotherLoad() {
        CatalogueGeneration generation = new CatalogueGeneration();
        int pending = generation.next();
        generation.next();
        assertFalse(generation.accepts(pending));
    }
}
