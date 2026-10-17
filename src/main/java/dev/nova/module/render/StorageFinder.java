package dev.nova.module.render;

import dev.nova.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Highlights chests, shulker boxes (the "chillers"), droppers and other containers. */
public class StorageFinder extends EspModule {
    public enum Kind {
        CHEST("Chests", 0xFFAA33, true),
        TRAPPED("Trapped Chests", 0xFF5555, true),
        ENDER("Ender Chests", 0xB26DFF, false),
        BARREL("Barrels", 0xC98A4B, false),
        SHULKER("Shulker Boxes", 0xFF77E6, true),
        DROPPER("Droppers", 0x55FFFF, true),
        DISPENSER("Dispensers", 0x5599FF, false),
        HOPPER("Hoppers", 0x9AA0A6, false);

        public final String label;
        public final int color;
        public final boolean defaultOn;

        Kind(String label, int color, boolean defaultOn) {
            this.label = label;
            this.color = color;
            this.defaultOn = defaultOn;
        }
    }

    private final Setting.Bool[] enabledKinds = new Setting.Bool[Kind.values().length];
    private final Setting.Col[] kindColors = new Setting.Col[Kind.values().length];
    private final Setting.Bool colorByType = new Setting.Bool("Color By Type", true);
    private final Setting.Col singleColor = new Setting.Col("Color", 0x4DA3FF);

    public StorageFinder() {
        super("Storage Finder", "Highlights chests, shulkers, droppers and more.");

        for (Kind k : Kind.values()) {
            enabledKinds[k.ordinal()] = add(new Setting.Bool(k.label, k.defaultOn));
        }

        addAppearanceSettings();

        add(colorByType, () -> colorMode.get() == ColorMode.STATIC);
        add(singleColor, () -> colorMode.get() == ColorMode.PULSE
                || (colorMode.get() == ColorMode.STATIC && !colorByType.get()));
        for (Kind k : Kind.values()) {
            kindColors[k.ordinal()] = add(new Setting.Col(k.label + " Color", k.color),
                    () -> colorMode.get() == ColorMode.STATIC && colorByType.get()
                            && enabledKinds[k.ordinal()].get());
        }

        addBehaviorSettings();

        // Long sight by default: only limited by how many chunks the server sends you.
        range.set(1024.0);
        distanceFade.set(false);
        scanInterval.set(20.0);
    }

    private static Kind kindOf(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.TRAPPED_CHEST) return Kind.TRAPPED;
        if (b instanceof ChestBlock) return Kind.CHEST; // chest, copper chests
        if (b == Blocks.ENDER_CHEST) return Kind.ENDER;
        if (b == Blocks.BARREL) return Kind.BARREL;
        if (b instanceof ShulkerBoxBlock) return Kind.SHULKER;
        if (b == Blocks.DROPPER) return Kind.DROPPER;
        if (b == Blocks.DISPENSER) return Kind.DISPENSER;
        if (b == Blocks.HOPPER) return Kind.HOPPER;
        return null;
    }

    private static AABB boxOf(Level level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(level, pos);
        AABB b = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        return b.move(pos);
    }

    @Override
    protected void collect(Level level, BlockEntity be, List<Target> out) {
        BlockState state = be.getBlockState();
        Kind kind = kindOf(state);
        if (kind == null || !enabledKinds[kind.ordinal()].get()) return;

        BlockPos pos = be.getBlockPos();
        AABB box = boxOf(level, pos, state);

        // Double chests: draw one merged box, from the LEFT half only.
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type == ChestType.RIGHT) return;
            if (type == ChestType.LEFT) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
                box = box.minmax(boxOf(level, other, level.getBlockState(other)));
            }
        }
        out.add(new Target(pos.immutable(), box, kind.ordinal()));
    }

    @Override
    protected int baseColor(int kind) {
        if (colorMode.get() == ColorMode.STATIC && colorByType.get()) return kindColors[kind].get();
        return singleColor.get();
    }
}
