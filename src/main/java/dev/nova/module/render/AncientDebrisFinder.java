package dev.nova.module.render;

import dev.nova.render.EspDraw;
import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds ancient debris and groups nearby pieces into labeled areas. */
public class AncientDebrisFinder extends BlockEspModule {
    private static final int DEBRIS = 0, AREA = 1;

    private record Area(Vec3 center, int count, int minY, int maxY) {}

    private final Setting.Bool showBlocks = new Setting.Bool("Show Debris Blocks", true);
    private final Setting.Bool showAreas = new Setting.Bool("Show Areas", true);
    private final Setting.Num joinRadius = new Setting.Num("Area Join Radius", 6, 2, 16, 1, " blocks");
    private final Setting.Num minPerArea = new Setting.Num("Min Debris Per Area", 2, 1, 20, 1, "");
    private final Setting.Bool labels = new Setting.Bool("Show Labels", true);
    private final Setting.Num labelSize = new Setting.Num("Label Size", 1.5, 0.5, 8, 0.5, "x");
    private final Setting.Num maxLabels = new Setting.Num("Max Labels", 10, 1, 40, 1, "");
    private final Setting.Col debrisColor = new Setting.Col("Debris Color", 0xC2733C);
    private final Setting.Col areaColor = new Setting.Col("Area Color", 0xFFB02E);

    private List<Area> areas = List.of();

    public AncientDebrisFinder() {
        super("Ancient Debris Finder", "Highlights ancient debris and areas where it clusters.");

        add(showBlocks);
        add(showAreas);
        add(joinRadius, showAreas::get);
        add(minPerArea, showAreas::get);
        add(labels, showAreas::get);
        add(labelSize, () -> showAreas.get() && labels.get());
        add(maxLabels, () -> showAreas.get() && labels.get());

        addAppearanceSettings();

        add(debrisColor, () -> colorMode.get() == ColorMode.STATIC);
        add(areaColor, () -> colorMode.get() == ColorMode.STATIC && showAreas.get());

        addScanSettings();
        addBehaviorSettings();

        range.set(192.0);
        mode.set(EspMode.BOTH);
        lineWidth.set(2.5);
        distanceFade.set(false);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        areas = List.of();
    }

    @Override
    protected int classify(BlockState state) {
        return state.getBlock() == Blocks.ANCIENT_DEBRIS ? DEBRIS : -1;
    }

    private static long cell(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFFL);
    }

    @Override
    protected List<Target> postProcess(List<Target> all) {
        List<Target> out = new ArrayList<>();
        List<BlockPos> debris = new ArrayList<>();
        for (Target t : all) {
            if (t.kind() != DEBRIS) continue;
            debris.add(t.pos());
            if (showBlocks.get()) out.add(t);
        }

        List<Area> found = new ArrayList<>();
        if (showAreas.get() && !debris.isEmpty()) {
            int r = joinRadius.i();
            // bucket debris into cells so neighbours are quick to find
            Map<Long, List<BlockPos>> grid = new HashMap<>();
            for (BlockPos p : debris) {
                grid.computeIfAbsent(cell(Math.floorDiv(p.getX(), r), Math.floorDiv(p.getY(), r),
                        Math.floorDiv(p.getZ(), r)), k -> new ArrayList<>()).add(p);
            }
            Set<BlockPos> seen = new HashSet<>();
            for (BlockPos start : debris) {
                if (!seen.add(start)) continue;
                List<BlockPos> group = new ArrayList<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                queue.add(start);
                while (!queue.isEmpty()) {
                    BlockPos p = queue.poll();
                    group.add(p);
                    int cx = Math.floorDiv(p.getX(), r), cy = Math.floorDiv(p.getY(), r), cz = Math.floorDiv(p.getZ(), r);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                List<BlockPos> near = grid.get(cell(cx + dx, cy + dy, cz + dz));
                                if (near == null) continue;
                                for (BlockPos q : near) {
                                    if (p.distSqr(q) <= (double) r * r && seen.add(q)) queue.add(q);
                                }
                            }
                        }
                    }
                }
                if (group.size() < minPerArea.i()) continue;

                double sx = 0, sy = 0, sz = 0;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
                for (BlockPos p : group) {
                    sx += p.getX();
                    sy += p.getY();
                    sz += p.getZ();
                    minY = Math.min(minY, p.getY());
                    maxY = Math.max(maxY, p.getY());
                    x0 = Math.min(x0, p.getX());
                    x1 = Math.max(x1, p.getX());
                    z0 = Math.min(z0, p.getZ());
                    z1 = Math.max(z1, p.getZ());
                }
                int n = group.size();
                Vec3 center = new Vec3(sx / n + 0.5, sy / n + 0.5, sz / n + 0.5);
                found.add(new Area(center, n, minY, maxY));
                out.add(new Target(BlockPos.containing(center), new AABB(x0 - 1, minY - 1, z0 - 1, x1 + 2, maxY + 2, z1 + 2), AREA));
            }
        }
        areas = found;
        return out;
    }

    @Override
    protected void renderExtra(Minecraft mc, Vec3 cam) {
        if (!showAreas.get() || !labels.get() || areas.isEmpty()) return;
        List<Area> sorted = new ArrayList<>(areas);
        sorted.sort(Comparator.comparingDouble(a -> a.center().distanceToSqr(cam)));
        int n = Math.min(sorted.size(), maxLabels.i());
        for (int i = 0; i < n; i++) {
            Area a = sorted.get(i);
            double dist = a.center().distanceTo(cam);
            float scale = labelSize.f() * (float) Math.max(1.0, dist / 8.0);
            String text = "Ancient debris x" + a.count() + " (Y " + a.minY() + ".." + a.maxY() + ") " + Math.round(dist) + "m";
            EspDraw.text(a.center().add(0, 2.0, 0), text, areaColor.get(), scale, throughWalls.get());
        }
    }

    @Override
    protected int baseColor(int kind) {
        return kind == AREA ? areaColor.get() : debrisColor.get();
    }
}
