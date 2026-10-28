package com.gathin.tvhome;

/** Geometry for the tiny directional reflection, independent of rendering. */
final class GlassDirection {
    private GlassDirection() { }
    static float direction(float dx, float dy) {
        return normalise((float) Math.toDegrees(Math.atan2(dy, dx)));
    }
    static float normalise(float angle) {
        float value = angle % 360f;
        return value < 0f ? value + 360f : value;
    }
    /** Cross the 0/360 seam by the short route without resetting the displayed position. */
    static float nearest(float current, float direction) {
        float delta = normalise(direction - current + 180f) - 180f;
        return current + delta;
    }
}
