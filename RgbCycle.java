package com.solimes.client.core;

/** Animated rainbow color for the block outline. */
public final class RgbCycle {
    private RgbCycle() {}

    /** @param speed 1..20 (higher = faster), @param brightnessPercent 10..100 */
    public static int argb(long timeMs, int speed, int brightnessPercent) {
        double hue = ((timeMs * (double) speed) / 10000.0) % 1.0;
        double v = Math.max(10, Math.min(100, brightnessPercent)) / 100.0;
        double h6 = hue * 6.0;
        int i = (int) Math.floor(h6) % 6;
        double f = h6 - Math.floor(h6);
        double q = v * (1 - f), t = v * f;
        double r, g, b;
        switch (i) {
            case 0: r = v; g = t; b = 0; break;
            case 1: r = q; g = v; b = 0; break;
            case 2: r = 0; g = v; b = t; break;
            case 3: r = 0; g = q; b = v; break;
            case 4: r = t; g = 0; b = v; break;
            default: r = v; g = 0; b = q; break;
        }
        return 0xFF000000 | ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }
}
