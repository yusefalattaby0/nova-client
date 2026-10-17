package dev.nova.module.render;

import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
import java.util.function.Predicate;

/**
 * Highlights ordinary blocks (not block entities) by scanning the chunks around you.
 *
 * To avoid lag it scans a few chunks per tick, nearest first, skips chunk sections that
 * cannot contain a wanted block at all, and keeps the results per chunk.
 */
public abstract class BlockEspModule extends EspModule {
    protected final Setting.Num chunksPerTick = new Setting.Num("Scan Speed", 8, 1, 64, 1, " chunks/tick");
    protected final Setting.Num maxBoxes = new Setting.Num("Max Boxes", 2500, 50, 20000, 50, "");

    private final Map<Long, List<Target>> results = new HashMap<>();
    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private ClientLevel lastLevel;
    private int tickCounter;

    protected BlockEspModule(String name, String description) {
        super(name, description);
    }

    /** Scan speed and box limit. Call before addBehaviorSettings(). */
    protected final void addScanSettings() {
        add(chunksPerTick);
        add(maxBoxes);
    }

    /** Returns a kind number (0, 1, 2 ...) if this block should be highlighted, or -1 to ignore it. */
    protected abstract int classify(BlockState state);

    /** Optional height filter. */
    protected boolean yAllowed(int y) {
        return true;
    }

    /** Optional quick check: could anything between these two heights matter? */
    protected boolean yRangeMayMatch(int lo, int hi) {
        return true;
    }

    @Override
    protected final void collect(Level level, BlockEntity be, List<Target> out) {
        // unused: this module scans blocks, not block entities
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        results.clear();
        queue.clear();
        lastLevel = null;
    }

    @Override
    public void onTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            results.clear();
            queue.clear();
            setTargets(List.of());
            return;
        }
        if (level != lastLevel) {
            results.clear();
            queue.clear();
            lastLevel = level;
        }

        BlockPos p = mc.player.blockPosition();
        int pcx = p.getX() >> 4;
        int pcz = p.getZ() >> 4;
        int radius = range.d() > 0 ? Math.min(MAX_CHUNK_RADIUS, (int) Math.ceil(range.d() / 16.0)) : MAX_CHUNK_RADIUS;

        int budget = chunksPerTick.i();
        while (budget > 0) {
            if (queue.isEmpty()) refill(pcx, pcz, radius);
            int[] c = queue.poll();
            if (c == null) break;
            long key = chunkKey(c[0], c[1]);
            ChunkAccess access = level.getChunk(c[0], c[1], ChunkStatus.FULL, false);
            if (access instanceof LevelChunk chunk) {
                results.put(key, scanChunk(chunk));
            } else {
                results.remove(key);
            }
            budget--;
        }

        if (tickCounter++ % Math.max(1, scanInterval.i()) == 0) aggregate(mc);
    }

    /** One number that identifies a chunk column (x in the top half, z in the bottom half). */
    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private void refill(int pcx, int pcz, int radius) {
        List<int[]> list = new ArrayList<>();
        Set<Long> keep = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                list.add(new int[]{pcx + dx, pcz + dz, dx * dx + dz * dz});
                keep.add(chunkKey(pcx + dx, pcz + dz));
            }
        }
        list.sort(Comparator.comparingInt(c -> c[2]));
        queue.addAll(list);
        results.keySet().retainAll(keep); // forget chunks we walked away from
    }

    private List<Target> scanChunk(LevelChunk chunk) {
        List<Target> out = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos cp = chunk.getPos();
        int baseX = cp.getMinBlockX();
        int baseZ = cp.getMinBlockZ();
        int minY = chunk.getMinY();
        Predicate<BlockState> wanted = s -> classify(s) >= 0;

        for (int i = 0; i < sections.length; i++) {
            int y0 = minY + i * 16;
            if (!yRangeMayMatch(y0, y0 + 15)) continue;
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(wanted)) continue;

            for (int ly = 0; ly < 16; ly++) {
                int by = y0 + ly;
                if (!yAllowed(by)) continue;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        int kind = classify(state);
                        if (kind < 0) continue;
                        BlockPos pos = new BlockPos(baseX + lx, by, baseZ + lz);
                        out.add(new Target(pos, new AABB(pos), kind));
                    }
                }
            }
        }
        return out;
    }

    private void aggregate(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        double maxRange = range.d();
        List<Target> all = new ArrayList<>();
        for (List<Target> list : results.values()) all.addAll(list);

        if (maxRange > 0) {
            double limit = maxRange * maxRange;
            all.removeIf(t -> t.box().getCenter().distanceToSqr(eye) > limit);
        }
        int cap = maxBoxes.i();
        if (all.size() > cap) {
            all.sort(Comparator.comparingDouble(t -> t.box().getCenter().distanceToSqr(eye)));
            all = new ArrayList<>(all.subList(0, cap));
        }
        setTargets(all);
    }
}
