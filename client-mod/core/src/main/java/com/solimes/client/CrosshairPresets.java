package com.solimes.client.core;

/** The 9 crosshair shapes as 11x11 bitmaps (true = pixel on). Preset numbers are 1..9. */
public final class CrosshairPresets {
    private CrosshairPresets() {}

    public static final int COUNT = 9;
    public static final int SIZE = 11;
    private static final String[] NAMES = {
            "Cross", "Dot", "Gap cross", "Circle", "Square", "X", "Diamond", "T-shape", "Dot with arms"
    };

    public static String name(int preset) { return NAMES[check(preset) - 1]; }

    public static boolean[][] bitmap(int preset) {
        int p = check(preset);
        boolean[][] bmp = new boolean[SIZE][SIZE];
        int c = SIZE / 2;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int x = col - c, y = row - c; // y grows downward
                int ax = Math.abs(x), ay = Math.abs(y), m = Math.max(ax, ay);
                boolean on;
                switch (p) {
                    case 1: on = (x == 0 || y == 0) && m <= 4; break;
                    case 2: on = ax <= 1 && ay <= 1; break;
                    case 3: on = (x == 0 || y == 0) && m >= 2 && m <= 5; break;
                    case 4: on = Math.abs(Math.sqrt(x * x + y * y) - 4.0) < 0.7; break;
                    case 5: on = m == 4; break;
                    case 6: on = ax == ay && ax <= 4; break;
                    case 7: on = ax + ay == 4; break;
                    case 8: on = (y == 0 && ax <= 4) || (x == 0 && y >= 0 && y <= 4); break;
                    default: on = (ax <= 1 && ay <= 1) || ((x == 0 || y == 0) && m >= 3 && m <= 5); break;
                }
                bmp[row][col] = on;
            }
        }
        return bmp;
    }

    private static int check(int preset) {
        if (preset < 1 || preset > COUNT) throw new IllegalArgumentException("preset must be 1.." + COUNT);
        return preset;
    }
}
