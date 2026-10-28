package com.gathin.tvhome;

import android.os.Build;

/** Shared plate alpha and cache identity for Home and Library artwork workers. */
final class IconBackgroundOpacity {
    private IconBackgroundOpacity() { }
    static int clamp(int percent) { return Math.max(0, Math.min(100, percent)); }
    static int alpha(int percent) { return Math.round(clamp(percent) * 255f / 100f); }
    /** A detached child is not evidence that its declared hardware window is software-only. */
    static boolean windowAccelerated(boolean decorAttached, boolean decorAccelerated, boolean declaredAccelerated) {
        return decorAttached ? decorAccelerated : declaredAccelerated;
    }
    /** Shared Activity/window policy for Home and its Library popup. */
    static boolean hardwareEligible(int apiLevel, boolean hardwareAccelerated,
                                    boolean cinematicHome, boolean ambientGlow, boolean appGlow) {
        return apiLevel >= 26 && hardwareAccelerated && !cinematicHome && !ambientGlow && !appGlow;
    }
    static boolean hardwareEligible(boolean hardwareAccelerated,
                                    boolean cinematicHome, boolean ambientGlow, boolean appGlow) {
        return hardwareEligible(Build.VERSION.SDK_INT, hardwareAccelerated, cinematicHome, ambientGlow, appGlow);
    }
    static String bundledKey(String pkg, int percent) { return bundledKey(pkg, percent, false); }
    static String bundledKey(String pkg, int percent, boolean hardware) {
        return pkg + "@" + clamp(percent) + (hardware ? ":hw" : ":sw");
    }
    static String fallbackKey(String pkg, String label, int percent) {
        return fallbackKey(pkg, label, percent, false);
    }
    static String fallbackKey(String pkg, String label, int percent, boolean hardware) {
        // Keep the package prefix stable so a package update drops every opacity/label variant.
        return "fallback:" + pkg + '\n' + clamp(percent) + '\n' + (label == null ? "" : label)
                + '\n' + (hardware ? "hw" : "sw");
    }
}
