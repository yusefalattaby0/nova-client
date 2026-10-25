package dev.nova.module.utility;

import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.setting.Setting;

/** A small box with your coordinates and biome. */
public class InfoHud extends Module {
    private final Setting.Bool biome = add(new Setting.Bool("Show Biome", true));

    public InfoHud() {
        super("Coords HUD", "Shows your XYZ and biome in a small box.", Category.UTILITY);
    }

    public static InfoHud get() { return ModuleManager.get(InfoHud.class); }

    public boolean biomeOn() { return biome.get(); }
}
