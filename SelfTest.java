package com.solimes.client.core;

import java.nio.file.*;
import java.util.Arrays;

/** Dependency-free tests. Run: java -cp out com.solimes.client.core.SelfTest */
public final class SelfTest {
    private static int passed = 0;

    private static void check(boolean cond, String name) {
        if (!cond) throw new AssertionError("FAILED: " + name);
        passed++;
    }

    public static void main(String[] args) throws Exception {
        OptionRegistry r = SolimesOptions.create();

        // clamping + sanitizing
        r.<Integer>get("crosshairPreset").set(99);
        check(r.integer("crosshairPreset") == 9, "int clamps to max");
        r.<Integer>get("outlineRgbSpeed").set(-5);
        check(r.integer("outlineRgbSpeed") == 1, "int clamps to min");
        r.<String>get("outlineMode").set("nonsense");
        check(r.string("outlineMode").equals("vanilla"), "choice rejects invalid value");

        // duplicate keys are rejected (extensibility safety)
        boolean threw = false;
        try { r.register(new Options.Bool("X", "fpsEnabled", "dup", true)); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "duplicate key rejected");

        // config round-trip + unknown key preservation
        Path tmp = Files.createTempFile("solimes", ".properties");
        Files.write(tmp, Arrays.asList("allowModMenu=false", "windowTitle=C++ Edition", "launcherNote=keep-me", "crosshairPreset=abc"));
        OptionRegistry r2 = SolimesOptions.create();
        ConfigStore store = new ConfigStore(r2, tmp);
        store.load();
        check(!r2.bool("allowModMenu"), "loads boolean");
        check(r2.string("windowTitle").equals("C++ Edition"), "loads text");
        check(r2.integer("crosshairPreset") == 1, "invalid int ignored");
        r2.<Integer>get("crosshairPreset").set(7);
        r2.<Integer>get("damageTintColor").set(0xC0112233);
        store.save();
        OptionRegistry r3 = SolimesOptions.create();
        new ConfigStore(r3, tmp).load();
        check(r3.integer("crosshairPreset") == 7, "round-trip int");
        check(r3.<Integer>get("damageTintColor").get() == 0xC0112233, "round-trip color");
        check(Files.readAllLines(tmp).stream().anyMatch(l -> l.startsWith("launcherNote=")), "unknown key preserved");
        Files.deleteIfExists(tmp);

        // 9 distinct crosshairs, none empty
        boolean[][][] all = new boolean[CrosshairPresets.COUNT][][];
        for (int i = 1; i <= CrosshairPresets.COUNT; i++) {
            all[i - 1] = CrosshairPresets.bitmap(i);
            int on = 0;
            for (boolean[] row : all[i - 1]) for (boolean b : row) if (b) on++;
            check(on > 0, "crosshair " + i + " not empty");
        }
        for (int a = 0; a < all.length; a++)
            for (int b = a + 1; b < all.length; b++)
                check(!Arrays.deepEquals(all[a], all[b]), "crosshair " + (a + 1) + " differs from " + (b + 1));

        // RGB cycle: changes over time, respects brightness
        int c0 = RgbCycle.argb(0, 5, 100), c1 = RgbCycle.argb(700, 5, 100);
        check(c0 != c1, "rgb changes over time");
        check((c0 >>> 24) == 0xFF, "rgb opaque");
        int dim = RgbCycle.argb(0, 5, 50);
        check(((dim >> 16) & 0xFF) < ((c0 >> 16) & 0xFF), "brightness lowers channel");

        // FPS counter ~60
        FpsCounter f = new FpsCounter();
        long t = 0;
        int v = 0;
        for (int i = 0; i < 130; i++) { v = f.tick(t); t += 16_666_667L; }
        check(Math.abs(v - 60) <= 1, "fps counter ~60, got " + v);

        System.out.println("SelfTest OK: " + passed + " checks passed");
    }
}
