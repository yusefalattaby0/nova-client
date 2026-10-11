package dev.nova.render;

import dev.nova.module.render.EspMode;
import dev.nova.util.Colors;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only place that touches Minecraft's world-drawing API (Gizmos).
 * If a Minecraft update changes drawing, this is the one file to adjust.
 */
public final class EspDraw {
    private EspDraw() {}

    public static void box(AABB b, int rgb, int outlineAlpha, int fillAlpha,
                           float width, EspMode mode, boolean throughWalls) {
        switch (mode) {
            case OUTLINE -> {
                if (outlineAlpha > 0) cuboid(b, GizmoStyle.stroke(Colors.argb(outlineAlpha, rgb), width), throughWalls);
            }
            case FILLED -> {
                if (fillAlpha > 0) cuboid(b, GizmoStyle.fill(Colors.argb(fillAlpha, rgb)), throughWalls);
            }
            case BOTH -> {
                if (outlineAlpha > 0 && fillAlpha > 0) {
                    cuboid(b, GizmoStyle.strokeAndFill(
                            Colors.argb(outlineAlpha, rgb), width, Colors.argb(fillAlpha, rgb)), throughWalls);
                } else if (outlineAlpha > 0) {
                    cuboid(b, GizmoStyle.stroke(Colors.argb(outlineAlpha, rgb), width), throughWalls);
                } else if (fillAlpha > 0) {
                    cuboid(b, GizmoStyle.fill(Colors.argb(fillAlpha, rgb)), throughWalls);
                }
            }
            case CORNERS -> {
                if (outlineAlpha > 0) corners(b, Colors.argb(outlineAlpha, rgb), width, throughWalls);
            }
        }
    }

    public static void line(Vec3 from, Vec3 to, int rgb, int alpha, float width, boolean throughWalls) {
        if (alpha <= 0) return;
        var props = Gizmos.line(from, to, Colors.argb(alpha, rgb), width);
        if (throughWalls) props.setAlwaysOnTop();
    }

    private static void cuboid(AABB b, GizmoStyle style, boolean throughWalls) {
        var props = Gizmos.cuboid(b, style);
        if (throughWalls) props.setAlwaysOnTop();
    }

    private static void corners(AABB b, int argb, float width, boolean throughWalls) {
        double lx = Math.min(0.3, (b.maxX - b.minX) / 3.0);
        double ly = Math.min(0.3, (b.maxY - b.minY) / 3.0);
        double lz = Math.min(0.3, (b.maxZ - b.minZ) / 3.0);
        double[] xs = {b.minX, b.maxX};
        double[] ys = {b.minY, b.maxY};
        double[] zs = {b.minZ, b.maxZ};
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    double x = xs[xi], y = ys[yi], z = zs[zi];
                    double dx = xi == 0 ? lx : -lx;
                    double dy = yi == 0 ? ly : -ly;
                    double dz = zi == 0 ? lz : -lz;
                    var p1 = Gizmos.line(new Vec3(x, y, z), new Vec3(x + dx, y, z), argb, width);
                    var p2 = Gizmos.line(new Vec3(x, y, z), new Vec3(x, y + dy, z), argb, width);
                    var p3 = Gizmos.line(new Vec3(x, y, z), new Vec3(x, y, z + dz), argb, width);
                    if (throughWalls) {
                        p1.setAlwaysOnTop();
                        p2.setAlwaysOnTop();
                        p3.setAlwaysOnTop();
                    }
                }
            }
        }
    }

}
