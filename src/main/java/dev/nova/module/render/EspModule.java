package dev.nova.module.render;

import dev.nova.module.Module;
import dev.nova.render.EspDraw;
import dev.nova.setting.Setting;
import dev.nova.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared scanning, drawing and customization for block-entity highlighters. */
public abstract class EspModule extends Module {
    public record Target(BlockPos pos, AABB box, int kind) {}

    protected final Setting.Enm<EspMode> mode = new Setting.Enm<>("Mode", EspMode.BOTH);
    protected final Setting.Enm<ColorMode> colorMode = new Setting.Enm<>("Color Mode", ColorMode.STATIC);
    protected final Setting.Num outlineOpacity = new Setting.Num("Outline Opacity", 100, 0, 100, 1, "%");
    protected final Setting.Num fillOpacity = new Setting.Num("Fill Opacity", 25, 0, 100, 1, "%");
    protected final Setting.Num lineWidth = new Setting.Num("Line Width", 2, 0.5, 6, 0.5, " px");
    protected final Setting.Num expand = new Setting.Num("Box Expand", 0.01, 0, 0.25, 0.01, "");
    protected final Setting.Num range = new Setting.Num("Range (0 = unlimited)", 128, 0, 512, 8, "");
    protected final Setting.Bool distanceFade = new Setting.Bool("Distance Fade", true);
    protected final Setting.Bool throughWalls = new Setting.Bool("Through Walls", true);
    protected final Setting.Bool tracers = new Setting.Bool("Tracers", false);
    protected final Setting.Num tracerOpacity = new Setting.Num("Tracer Opacity", 60, 0, 100, 1, "%");
    protected final Setting.Num scanInterval = new Setting.Num("Scan Interval", 10, 2, 60, 1, " ticks");

    private List<Target> cache = List.of();
    private int tickCounter;

    protected EspModule(String name, String description) {
        super(name, description, Category.RENDER);
    }

    /** Mode, opacities, line width and color mode. Call from the subclass constructor. */
    protected final void addAppearanceSettings() {
        add(mode);
        add(outlineOpacity, () -> mode.get() != EspMode.FILLED);
        add(fillOpacity, () -> mode.get() == EspMode.FILLED || mode.get() == EspMode.BOTH);
        add(lineWidth, () -> mode.get() != EspMode.FILLED);
        add(colorMode);
    }

    /** Range, fade, through walls, tracers and scan rate. Call last in the subclass constructor. */
    protected final void addBehaviorSettings() {
        add(expand);
        add(range);
        add(distanceFade);
        add(throughWalls);
        add(tracers);
        add(tracerOpacity, tracers::get);
        add(scanInterval);
    }

    /** Inspect one block entity and add zero or more targets. */
    protected abstract void collect(Level level, BlockEntity be, List<Target> out);

    /** Base color (RGB) for a target before Rainbow/Pulse is applied. */
    protected abstract int baseColor(int kind);

    protected void onRescan(Minecraft mc, List<Target> targets) {}

    @Override
    protected void onDisable() {
        cache = List.of();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            cache = List.of();
            return;
        }
        if (tickCounter++ % Math.max(1, scanInterval.i()) != 0) return;

        int renderDistance = mc.options.getEffectiveRenderDistance();
        int chunkRange = range.d() > 0 ? Math.min(renderDistance, (int) Math.ceil(range.d() / 16.0) + 1) : renderDistance;
        BlockPos p = mc.player.blockPosition();
        int cx = p.getX() >> 4;
        int cz = p.getZ() >> 4;

        List<Target> out = new ArrayList<>();
        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                ChunkAccess access = mc.level.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    collect(mc.level, be, out);
                }
            }
        }
        cache = out;
        onRescan(mc, out);
    }

    private int resolveColor(int base) {
        return switch (colorMode.get()) {
            case STATIC -> base;
            case RAINBOW -> Colors.rainbow();
            case PULSE -> Colors.pulse(base);
        };
    }

    public final void render(Minecraft mc) {
        if (mc.level == null || mc.player == null || cache.isEmpty()) return;

        Vec3 cam = mc.player.getEyePosition();
        Vec3 tracerFrom = mc.player.getEyePosition().add(mc.player.getViewVector(1.0f).scale(0.6));
        double maxRange = range.d();
        float width = lineWidth.f();
        EspMode m = mode.get();

        for (Target t : cache) {
            Vec3 center = t.box().getCenter();
            double dist = cam.distanceTo(center);
            if (maxRange > 0 && dist > maxRange) continue;

            float fade = 1f;
            if (distanceFade.get()) {
                double ref = maxRange > 0 ? maxRange : 128.0;
                fade = (float) Math.max(0.2, 1.0 - (dist / ref) * 0.8);
            }

            int rgb = resolveColor(baseColor(t.kind()));
            int outA = Math.round(outlineOpacity.f() * 2.55f * fade);
            int fillA = Math.round(fillOpacity.f() * 2.55f * fade);

            EspDraw.box(t.box().inflate(expand.d()), rgb, outA, fillA, width, m, throughWalls.get());

            if (tracers.get()) {
                int tracerA = Math.round(tracerOpacity.f() * 2.55f * fade);
                EspDraw.line(tracerFrom, center, rgb, tracerA, 1.5f, throughWalls.get());
            }
        }
    }
}
