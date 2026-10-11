package dev.nova.module.render;

import dev.nova.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Highlights monster spawners (and optionally trial spawners). */
public class SpawnerFinder extends EspModule {
    private static final int SPAWNER = 0;
    private static final int TRIAL = 1;

    private final Setting.Bool includeTrial = new Setting.Bool("Include Trial Spawners", false);
    private final Setting.Col spawnerColor = new Setting.Col("Spawner Color", 0xFF3B3B);
    private final Setting.Col trialColor = new Setting.Col("Trial Spawner Color", 0x3BE0FF);

    public SpawnerFinder() {
        super("Spawner Finder", "Highlights monster spawners.");

        add(includeTrial);

        addAppearanceSettings();

        add(spawnerColor, () -> colorMode.get() != ColorMode.RAINBOW);
        add(trialColor, () -> includeTrial.get() && colorMode.get() != ColorMode.RAINBOW);

        addBehaviorSettings();
        // Spawners are rare and valuable: default to a wider range.
        range.set(256.0);
    }

    @Override
    protected void collect(Level level, BlockEntity be, List<Target> out) {
        BlockState state = be.getBlockState();
        Block b = state.getBlock();
        int kind;
        if (b == Blocks.SPAWNER) kind = SPAWNER;
        else if (includeTrial.get() && b == Blocks.TRIAL_SPAWNER) kind = TRIAL;
        else return;

        BlockPos pos = be.getBlockPos();
        out.add(new Target(pos.immutable(), new AABB(pos), kind));
    }

    @Override
    protected int baseColor(int kind) {
        return kind == TRIAL ? trialColor.get() : spawnerColor.get();
    }
}
