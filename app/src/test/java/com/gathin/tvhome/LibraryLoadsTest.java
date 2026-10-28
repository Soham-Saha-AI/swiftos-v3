package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class LibraryLoadsTest {
    @Test public void refreshWhileOpeningRetainsOneReveal() {
        LibraryLoads loads = new LibraryLoads();
        int opening = loads.start(true);
        int refresh = loads.start(false);
        assertFalse(loads.takeReveal(opening));
        assertTrue(loads.takeReveal(refresh));
        assertFalse(loads.takeReveal(refresh));
    }
    @Test public void severalRefreshesNeverLoseOrDuplicateOpening() {
        LibraryLoads loads = new LibraryLoads();
        loads.start(true);
        int old = loads.start(false);
        int latest = loads.start(false);
        assertFalse(loads.isLatest(old));
        assertFalse(loads.takeReveal(old));
        assertTrue(loads.takeReveal(latest));
        assertFalse(loads.takeReveal(loads.start(false)));
    }
    @Test public void closeCancelsPendingAndOldReplyCannotConsumeReopenedIntent() {
        LibraryLoads loads = new LibraryLoads();
        int old = loads.start(true);
        loads.cancel();
        assertFalse(loads.isLatest(old));
        assertFalse(loads.takeReveal(loads.current()));
        int reopened = loads.start(true);
        assertFalse(loads.takeReveal(old));
        assertTrue(loads.takeReveal(reopened));
    }
    @Test public void visibleRefreshDoesNotPresentAgain() {
        LibraryLoads loads = new LibraryLoads();
        assertTrue(loads.takeReveal(loads.start(true)));
        assertFalse(loads.takeReveal(loads.start(false)));
    }
}
