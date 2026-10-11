package dev.nova.module.render;

import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Highlights monster spawners (and optionally trial spawners). */
public class SpawnerFinder extends EspModule {
    private static final int SPAWNER = 0;
    private static final int TRIAL = 1;

    private final Setting.Bool includeTrial = new Setting.Bool("Include Trial Spawners", false);
    private final Setting.Bool chatAlert = new Setting.Bool("Chat Alert", true);
    private final Setting.Col spawnerColor = new Setting.Col("Spawner Color", 0xFF3B3B);
    private final Setting.Col trialColor = new Setting.Col("Trial Spawner Color", 0x3BE0FF);

    private final Set<BlockPos> known = new HashSet<>();
    private Level lastLevel;

    public SpawnerFinder() {
        super("Spawner Finder", "Highlights monster spawners.");

        add(includeTrial);
        add(chatAlert);

        addAppearanceSettings();

        add(spawnerColor, () -> colorMode.get() != ColorMode.RAINBOW);
        add(trialColor, () -> includeTrial.get() && colorMode.get() != ColorMode.RAINBOW);

        addBehaviorSettings();
        // Spawners are rare and valuable: default to a wider range with tracers off.
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

    @Override
    protected void onRescan(Minecraft mc, List<Target> targets) {
        if (mc.level != lastLevel) {
            known.clear();
            lastLevel = mc.level;
        }
        int fresh = 0;
        BlockPos first = null;
        for (Target t : targets) {
            if (known.add(t.pos())) {
                fresh++;
                if (first == null) first = t.pos();
            }
        }
        if (fresh == 0 || !chatAlert.get() || mc.player == null) return;

        String msg = fresh == 1
                ? "Spawner found at " + first.getX() + ", " + first.getY() + ", " + first.getZ()
                : fresh + " new spawners found (first at " + first.getX() + ", " + first.getY() + ", " + first.getZ() + ")";
        mc.gui.getChat().addMessage(Component.literal("[Nova] " + msg));
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        known.clear();
        lastLevel = null;
    }
}
