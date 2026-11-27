package com.solimes.client.core;

/** All built-in Mod Menu options. To add a new one, register it here - nothing else changes. */
public final class SolimesOptions {
    private SolimesOptions() {}

    public static final String GENERAL = "General";
    public static final String VISUALS = "Visuals";
    public static final String HUD = "HUD";
    public static final String PERFORMANCE = "Performance";

    public static OptionRegistry create() {
        OptionRegistry r = new OptionRegistry();

        // General (written by the launcher, read by the mod)
        r.register(new Options.Bool(GENERAL, "allowModMenu", "Allow Mod Menu", true));
        r.register(new Options.Text(GENERAL, "windowTitle", "Window title", "Solimes Client"));
        r.register(new Options.Bool(GENERAL, "clientBadge", "Client badge next to name", true));

        // Visuals
        r.register(new Options.Bool(VISUALS, "damageTintEnabled", "Custom damage tint", true));
        r.register(new Options.Color(VISUALS, "damageTintColor", "Damage tint color", 0x80FF0000));
        r.register(new Options.Choice(VISUALS, "outlineMode", "Block outline", "vanilla", "vanilla", "solid", "rgb"));
        r.register(new Options.Color(VISUALS, "outlineColor", "Outline color", 0xFF000000));
        r.register(new Options.Int(VISUALS, "outlineRgbSpeed", "Outline RGB speed", 5, 1, 20));
        r.register(new Options.Int(VISUALS, "outlineBrightness", "Outline brightness %", 100, 10, 100));

        // HUD
        r.register(new Options.Bool(HUD, "fpsEnabled", "FPS counter", true));
        r.register(new Options.Color(HUD, "fpsColor", "FPS color", 0xFFFFFFFF));
        r.register(new Options.Choice(HUD, "fpsPosition", "FPS position", "top-left",
                "top-left", "top-right", "bottom-left", "bottom-right"));
        r.register(new Options.Bool(HUD, "crosshairEnabled", "Custom crosshair", false));
        r.register(new Options.Int(HUD, "crosshairPreset", "Crosshair preset (1-9)", 1, 1, CrosshairPresets.COUNT));
        r.register(new Options.Color(HUD, "crosshairColor", "Crosshair color", 0xFFFFFFFF));

        // Performance
        r.register(new Options.Bool(PERFORMANCE, "optimizedMode", "Optimized mode (max FPS)", false));
        r.register(new Options.Bool(PERFORMANCE, "optRemoveFog", "Optimized: remove fog", true));
        r.register(new Options.Bool(PERFORMANCE, "optReduceParticles", "Optimized: minimal particles", true));
        return r;
    }
}
