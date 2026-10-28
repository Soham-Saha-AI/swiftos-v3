package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GlassDirectionTest {
    @Test public void dpadAndDiagonalDirectionsAgreeWithScreenCoordinates() {
        assertEquals(0f, GlassDirection.direction(1, 0), 0.001f);
        assertEquals(90f, GlassDirection.direction(0, 1), 0.001f);
        assertEquals(180f, GlassDirection.direction(-1, 0), 0.001f);
        assertEquals(270f, GlassDirection.direction(0, -1), 0.001f);
        assertEquals(225f, GlassDirection.direction(-10, -10), 0.001f);
    }
    @Test public void crossingSeamDoesNotMakeAnAlmostFullTurn() {
        assertEquals(365f, GlassDirection.nearest(355f, 5f), 0.001f);
        assertEquals(-5f, GlassDirection.nearest(5f, 355f), 0.001f);
    }
    @Test public void rapidReversalsStayWithinHalfTurnOfDisplayedPosition() {
        for (float current : new float[]{-725, -5, 0, 45, 355, 721}) {
            for (float direction : new float[]{0, 90, 180, 270, 359}) {
                float next = GlassDirection.nearest(current, direction);
                assertTrue(Math.abs(next - current) <= 180.001f);
                assertEquals(GlassDirection.normalise(direction), GlassDirection.normalise(next), 0.001f);
            }
        }
    }
}
