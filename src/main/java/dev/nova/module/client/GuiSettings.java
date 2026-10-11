package dev.nova.module.client;

import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.setting.Setting;
import dev.nova.util.Colors;

/** Always-on module that holds ClickGUI appearance settings. */
public class GuiSettings extends Module {
    private final Setting.Col accent = add(new Setting.Col("Accent Color", 0x7C5CFF));
    private final Setting.Bool rainbowAccent = add(new Setting.Bool("Rainbow Accent", false));
    private final Setting.Num background = add(new Setting.Num("Background Opacity", 94, 40, 100, 1, "%"));
    private final Setting.Num radius = add(new Setting.Num("Corner Radius", 4, 0, 6, 1, " px"));
    private final Setting.Bool dim = add(new Setting.Bool("Dim World", true));

    public GuiSettings() {
        super("Interface", "ClickGUI colors and style.", Category.CLIENT);
    }

    @Override public boolean toggleable() { return false; }

    private static GuiSettings self() { return ModuleManager.get(GuiSettings.class); }

    public static int accent() {
        GuiSettings s = self();
        if (s == null) return 0x7C5CFF;
        return s.rainbowAccent.get() ? Colors.rainbow() : s.accent.get();
    }

    public static int backgroundAlpha() {
        GuiSettings s = self();
        return s == null ? 240 : Math.round(s.background.f() * 2.55f);
    }

    public static int radius() {
        GuiSettings s = self();
        return s == null ? 4 : s.radius.i();
    }

    public static boolean dimWorld() {
        GuiSettings s = self();
        return s == null || s.dim.get();
    }
}
