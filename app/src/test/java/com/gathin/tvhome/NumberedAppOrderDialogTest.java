package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class NumberedAppOrderDialogTest {
    @Test public void movingAcrossDockBoundaryShiftsNeighboursAndCanBeUndone() {
        List<String> keys = new ArrayList<>(Arrays.asList("netflix", "prime", "youtube", "hotstar", "vlc"));
        assertTrue(NumberedAppOrderDialog.moveTo(keys, 4, 0));
        assertEquals(Arrays.asList("vlc", "netflix", "prime", "youtube", "hotstar"), keys);
        assertTrue(NumberedAppOrderDialog.moveTo(keys, 0, 4));
        assertEquals(Arrays.asList("netflix", "prime", "youtube", "hotstar", "vlc"), keys);
    }

    @Test public void invalidOrUnchangedDestinationsLeaveOrderUntouched() {
        List<String> keys = new ArrayList<>(Arrays.asList("youtube", "vlc"));
        assertFalse(NumberedAppOrderDialog.moveTo(keys, 0, 0));
        assertFalse(NumberedAppOrderDialog.moveTo(keys, -1, 1));
        assertFalse(NumberedAppOrderDialog.moveTo(keys, 0, 2));
        assertFalse(NumberedAppOrderDialog.moveTo(new ArrayList<>(), 0, 0));
        assertEquals(Arrays.asList("youtube", "vlc"), keys);
    }

    @Test public void snapshotDeduplicatesComponentsAndKeepsCallerOrderUnchanged() {
        List<NumberedAppOrderDialog.Item> original = new ArrayList<>(Arrays.asList(
                new NumberedAppOrderDialog.Item("a/.Main", "One"),
                new NumberedAppOrderDialog.Item("a/.Main", "Duplicate"),
                null,
                new NumberedAppOrderDialog.Item("", "Invalid"),
                new NumberedAppOrderDialog.Item("b/.Main", "")));
        List<NumberedAppOrderDialog.Item> staged = NumberedAppOrderDialog.snapshot(original);
        assertEquals(2, staged.size());
        assertEquals("One", staged.get(0).label);
        assertEquals("b/.Main", staged.get(1).label);
        NumberedAppOrderDialog.moveTo(staged, 0, 1);
        assertEquals(Arrays.asList("b/.Main", "a/.Main"), staged.stream().map(item -> item.key).collect(Collectors.toList()));
        assertEquals(5, original.size());
        assertEquals("a/.Main", original.get(0).key);
        assertTrue(NumberedAppOrderDialog.snapshot(null).isEmpty());
    }
}
