package dev.nova.module.render;

import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.render.EspDraw;
import dev.nova.setting.Setting;
import dev.nova.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Draws a line behind every nearby player showing where they have been. The map screen reuses these paths. */
public class Trails extends Module {
    private final Setting.Num length = add(new Setting.Num("Trail Length", 60, 10, 300, 10, " points"));
    private final Setting.Num interval = add(new Setting.Num("Point Interval", 4, 1, 20, 1, " ticks"));
    private final Setting.Num range = add(new Setting.Num("Range", 128, 16, 512, 16, ""));
    private final Setting.Num width = add(new Setting.Num("Line Width", 2.5, 0.5, 6, 0.5, " px"));
    private final Setting.Bool includeSelf = add(new Setting.Bool("Include Yourself", false));
    private final Setting.Bool byPlayer = add(new Setting.Bool("Color By Player", true));
    private final Setting.Col color = add(new Setting.Col("Color", 0x7C5CFF), () -> !byPlayer.get());
    private final Setting.Bool throughWalls = add(new Setting.Bool("Through Walls", true));

    public static final class Path {
        public final String name;
        public final ArrayDeque<Vec3> points = new ArrayDeque<>();
        public int rgb;
        long lastSeen;

        Path(String name) { this.name = name; }
    }

    private final Map<UUID, Path> paths = new HashMap<>();
    private int tick;

    public Trails() {
        super("Trails", "Draws a path behind nearby players.", Category.RENDER);
    }

    /** A snapshot of the recorded paths (empty when the module is off). */
    public List<Path> snapshot() {
        return new ArrayList<>(paths.values());
    }

    public static Trails get() { return ModuleManager.get(Trails.class); }

    @Override
    protected void onDisable() {
        paths.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            paths.clear();
            return;
        }
        tick++;
        if (tick % Math.max(1, interval.i()) != 0) return;

        Entity self = mc.player;
        double r = range.d();
        for (Entity e : mc.level.getEntities(self, self.getBoundingBox().inflate(r), x -> x instanceof Player)) {
            if (e.isRemoved()) continue;
            Path p = paths.computeIfAbsent(e.getUUID(), id -> new Path(e.getName().getString()));
            p.lastSeen = tick;
            p.rgb = Colors.hsb((e.getUUID().hashCode() & 0xFFFF) / 65535f, 0.65f, 1f);
            Vec3 now = e.position();
            Vec3 last = p.points.peekLast();
            if (last == null || last.distanceToSqr(now) > 0.25) p.points.addLast(now);
            while (p.points.size() > length.i()) p.points.pollFirst();
        }
        if (includeSelf.get()) {
            Path p = paths.computeIfAbsent(self.getUUID(), id -> new Path(self.getName().getString()));
            p.lastSeen = tick;
            p.rgb = 0xFFFFFF;
            Vec3 now = self.position();
            Vec3 last = p.points.peekLast();
            if (last == null || last.distanceToSqr(now) > 0.25) p.points.addLast(now);
            while (p.points.size() > length.i()) p.points.pollFirst();
        } else {
            paths.remove(self.getUUID());
        }

        // forget players we have not seen for about 30 seconds
        int stale = Math.max(1, interval.i()) * 150;
        for (Iterator<Path> it = paths.values().iterator(); it.hasNext(); ) {
            if (tick - it.next().lastSeen > stale) it.remove();
        }
    }

    @Override
    public void onFrame(Minecraft mc) {
        if (mc.level == null || paths.isEmpty()) return;
        for (Path p : new ArrayList<>(paths.values())) {
            int n = p.points.size();
            if (n < 2) continue;
            int rgb = byPlayer.get() ? p.rgb : color.get();
            Vec3 prev = null;
            int i = 0;
            for (Vec3 pt : p.points) {
                if (prev != null) {
                    int alpha = 60 + Math.round(195f * i / n);
                    EspDraw.line(prev.add(0, 0.1, 0), pt.add(0, 0.1, 0), rgb, alpha, width.f(), throughWalls.get());
                }
                prev = pt;
                i++;
            }
        }
    }
}
