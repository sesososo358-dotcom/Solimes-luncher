package com.solimes.client.core;

/** Call tick() once per rendered frame; value() is refreshed twice per second. */
public final class FpsCounter {
    private long windowStart = -1;
    private int frames;
    private int value;

    public int tick(long nowNanos) {
        if (windowStart < 0) windowStart = nowNanos;
        frames++;
        long dt = nowNanos - windowStart;
        if (dt >= 500_000_000L) {
            value = (int) Math.round(frames * 1_000_000_000.0 / dt);
            frames = 0;
            windowStart = nowNanos;
        }
        return value;
    }

    public int value() { return value; }
}
