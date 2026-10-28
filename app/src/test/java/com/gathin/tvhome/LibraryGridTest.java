package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LibraryGridTest {
    @Test public void verticalMovementUsesSixColumns() {
        assertEquals(10, LibraryGrid.next(4, 24, LibraryGrid.DOWN, false, 4));
        assertEquals(4, LibraryGrid.next(10, 24, LibraryGrid.UP, false, 4));
        assertEquals(16, LibraryGrid.next(10, 24, LibraryGrid.DOWN, true, 4));
    }

    @Test public void horizontalMovementNeverWrapsRows() {
        assertEquals(5, LibraryGrid.next(5, 24, LibraryGrid.RIGHT, false, 5));
        assertEquals(6, LibraryGrid.next(6, 24, LibraryGrid.LEFT, false, 0));
        assertEquals(4, LibraryGrid.next(3, 24, LibraryGrid.RIGHT, false, 3));
        assertEquals(2, LibraryGrid.next(3, 24, LibraryGrid.LEFT, false, 3));
    }

    @Test public void rtlMirrorsOnlyHorizontalMovement() {
        assertEquals(4, LibraryGrid.next(3, 24, LibraryGrid.LEFT, true, 3));
        assertEquals(2, LibraryGrid.next(3, 24, LibraryGrid.RIGHT, true, 3));
        assertEquals(5, LibraryGrid.next(5, 24, LibraryGrid.LEFT, true, 5));
        assertEquals(6, LibraryGrid.next(6, 24, LibraryGrid.RIGHT, true, 0));
    }

    @Test public void partialLastRowClampsThenRestoresThePreferredColumn() {
        assertEquals(19, LibraryGrid.next(17, 20, LibraryGrid.DOWN, false, 5));
        assertEquals(17, LibraryGrid.next(19, 20, LibraryGrid.UP, false, 5));
        assertEquals(19, LibraryGrid.next(19, 20, LibraryGrid.RIGHT, false, 1));
    }

    @Test public void topBottomAndSingleAppStayInsideTheCatalogue() {
        assertEquals(3, LibraryGrid.next(3, 24, LibraryGrid.UP, false, 3));
        assertEquals(22, LibraryGrid.next(22, 24, LibraryGrid.DOWN, false, 4));
        assertEquals(0, LibraryGrid.next(0, 1, LibraryGrid.DOWN, false, 0));
        assertEquals(0, LibraryGrid.next(0, 1, LibraryGrid.RIGHT, false, 0));
        assertEquals(-1, LibraryGrid.next(0, 0, LibraryGrid.DOWN, false, 0));
    }

    @Test public void stalePositionsAndMissingPreferredColumnAreClamped() {
        assertEquals(1, LibraryGrid.next(99, 8, LibraryGrid.UP, false, -1));
        assertEquals(6, LibraryGrid.next(-1, 8, LibraryGrid.DOWN, false, -1));
        assertEquals(3, LibraryGrid.next(3, 8, 0, false, -1));
    }
}
