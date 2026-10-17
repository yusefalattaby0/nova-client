package dev.nova.module.render;

import dev.nova.setting.Setting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Highlights ore blocks, with per-ore toggles, colors and a height range. */
public class OreFinder extends BlockEspModule {
    public enum Ore {
        DIAMOND("Diamond", 0x35E0FF, true),
        EMERALD("Emerald", 0x2BFF6A, true),
        ANCIENT_DEBRIS("Ancient Debris", 0xC2733C, true),
        GOLD("Gold", 0xFFD83B, true),
        IRON("Iron", 0xE0B89A, true),
        REDSTONE("Redstone", 0xFF2A2A, false),
        LAPIS("Lapis", 0x3B5BFF, false),
        COPPER("Copper", 0xE07B39, false),
        COAL("Coal", 0x8A8A8A, false),
        NETHER_GOLD("Nether Gold", 0xFFB52E, false),
        QUARTZ("Nether Quartz", 0xF0E6D8, false);

        public final String label;
        public final int color;
        public final boolean defaultOn;

        Ore(String label, int color, boolean defaultOn) {
            this.label = label;
            this.color = color;
            this.defaultOn = defaultOn;
        }
    }

    private static final Map<Block, Ore> ORES = new HashMap<>();

    static {
        put(Ore.DIAMOND, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        put(Ore.EMERALD, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        put(Ore.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS);
        put(Ore.GOLD, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
        put(Ore.IRON, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
        put(Ore.REDSTONE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        put(Ore.LAPIS, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
        put(Ore.COPPER, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
        put(Ore.COAL, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        put(Ore.NETHER_GOLD, Blocks.NETHER_GOLD_ORE);
        put(Ore.QUARTZ, Blocks.NETHER_QUARTZ_ORE);
    }

    private static void put(Ore ore, Block... blocks) {
        for (Block b : blocks) ORES.put(b, ore);
    }

    private final Setting.Bool[] enabledOres = new Setting.Bool[Ore.values().length];
    private final Setting.Col[] oreColors = new Setting.Col[Ore.values().length];
    private final Setting.Bool colorByType = new Setting.Bool("Color By Type", true);
    private final Setting.Col singleColor = new Setting.Col("Color", 0x4DA3FF);
    private final Setting.Num minY = new Setting.Num("Min Height", -64, -64, 320, 1, "");
    private final Setting.Num maxY = new Setting.Num("Max Height", 320, -64, 320, 1, "");

    public OreFinder() {
        super("Ore Finder", "Highlights ores in nearby chunks.");

        for (Ore o : Ore.values()) {
            enabledOres[o.ordinal()] = add(new Setting.Bool(o.label, o.defaultOn));
        }

        addAppearanceSettings();

        add(colorByType, () -> colorMode.get() == ColorMode.STATIC);
        add(singleColor, () -> colorMode.get() == ColorMode.PULSE
                || (colorMode.get() == ColorMode.STATIC && !colorByType.get()));
        for (Ore o : Ore.values()) {
            oreColors[o.ordinal()] = add(new Setting.Col(o.label + " Color", o.color),
                    () -> colorMode.get() == ColorMode.STATIC && colorByType.get()
                            && enabledOres[o.ordinal()].get());
        }

        add(minY);
        add(maxY);
        addScanSettings();
        addBehaviorSettings();

        // Ores are everywhere, so a shorter default range keeps things readable and fast.
        range.set(64.0);
        mode.set(EspMode.OUTLINE);
    }

    @Override
    protected int classify(BlockState state) {
        Ore ore = ORES.get(state.getBlock());
        if (ore == null || !enabledOres[ore.ordinal()].get()) return -1;
        return ore.ordinal();
    }

    @Override
    protected boolean yAllowed(int y) {
        return y >= minY.i() && y <= maxY.i();
    }

    @Override
    protected boolean yRangeMayMatch(int lo, int hi) {
        return hi >= minY.i() && lo <= maxY.i();
    }

    @Override
    protected int baseColor(int kind) {
        if (colorMode.get() == ColorMode.STATIC && colorByType.get()) return oreColors[kind].get();
        return singleColor.get();
    }
}
