package dev.nova.module.render;

import dev.nova.setting.Setting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Highlights respawn anchors, colored by whether they are charged. */
public class RespawnAnchorFinder extends BlockEspModule {
    private static final int UNCHARGED = 0;
    private static final int CHARGED = 1;

    private final Setting.Bool showUncharged = new Setting.Bool("Show Uncharged", true);
    private final Setting.Col chargedColor = new Setting.Col("Charged Color", 0xFF8A1F);
    private final Setting.Col unchargedColor = new Setting.Col("Uncharged Color", 0xB26DFF);

    public RespawnAnchorFinder() {
        super("Respawn Anchor Finder", "Highlights respawn anchors (charged ones in a different color).");

        add(showUncharged);

        addAppearanceSettings();

        add(chargedColor, () -> colorMode.get() != ColorMode.RAINBOW);
        add(unchargedColor, () -> showUncharged.get() && colorMode.get() != ColorMode.RAINBOW);

        addScanSettings();
        addBehaviorSettings();

        range.set(128.0);
    }

    @Override
    protected int classify(BlockState state) {
        if (state.getBlock() != Blocks.RESPAWN_ANCHOR) return -1;
        boolean charged = state.getValue(RespawnAnchorBlock.CHARGE) > 0;
        if (!charged && !showUncharged.get()) return -1;
        return charged ? CHARGED : UNCHARGED;
    }

    @Override
    protected int baseColor(int kind) {
        return kind == CHARGED ? chargedColor.get() : unchargedColor.get();
    }
}
