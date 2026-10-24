package dev.nova.gui;

import dev.nova.module.ModuleManager;
import dev.nova.module.render.BaseFinder;
import dev.nova.util.Colors;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Base Finder's screen overlay: a list of nearby sites and a tooltip for the one you aim at. */
public final class BaseHud {
    private BaseHud() {}

    private static final int PANEL_BG = 0xD00B1216;
    private static final int TEXT = 0xFFF2F5F7;
    private static final int SUB = 0xFF8A949C;
    private static final int LINE = 0x33FFFFFF;

    private record Entry(BaseFinder.Site site, double dist) {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
if (mc.player == null || mc.level == null) return;
        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        if (bf == null || !bf.enabled()) return;

        float pt = dt.getGameTimeDeltaPartialTick(false);
        Entity view = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        Vec3 eye = view.getEyePosition(pt);
        Vec3 look = view.getViewVector(pt);

        List<Entry> entries = new ArrayList<>();
        for (BaseFinder.Site s : bf.sites()) entries.add(new Entry(s, s.center().distanceTo(eye)));
        entries.sort(Comparator.comparingDouble(Entry::dist));

        if (bf.panelOn()) drawPanel(g, bf, entries, eye);
        if (bf.tooltipOn()) drawTooltip(g, mc, bf, entries, eye, look);
    }

    private static String direction(Vec3 from, Vec3 to) {
        double deg = Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)); // 0 = east, 90 = south
        if (deg < 0) deg += 360;
        String[] names = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        return names[(int) Math.round(deg / 45.0) % 8];
    }

    private static void drawPanel(GuiGraphicsExtractor g, BaseFinder bf, List<Entry> entries, Vec3 eye) {
        int shown = Math.min(entries.size(), bf.panelEntries());
        int x = 6, y = 6, w = 200;
        int rowH = 26;
        int h = 22 + Math.max(1, shown) * rowH + (shown > 0 ? 12 : 4);

        Draw.round(g, x, y, w, h, 4, PANEL_BG);
        Draw.text(g, "BaseFinder", x + 8, y + 7, TEXT);
        String count = entries.size() + " nearby";
        Draw.text(g, count, x + w - 8 - Draw.width(count), y + 7, SUB);
        Draw.rect(g, x + 6, y + 19, w - 12, 1, LINE);

        if (shown == 0) {
            Draw.text(g, "Scanning for bases...", x + 8, y + 27, SUB);
            return;
        }
        for (int i = 0; i < shown; i++) {
            BaseFinder.Site s = entries.get(i).site();
            int rgb = bf.colorOf(s);
            int ry = y + 24 + i * rowH;

            Draw.text(g, Integer.toString(i + 1), x + 8, ry, SUB);
            Draw.text(g, s.label(), x + 20, ry, TEXT);
            String pct = s.conf() + "%";
            Draw.text(g, pct, x + w - 8 - Draw.width(pct), ry, 0xFF000000 | rgb);

            String detail = direction(eye, s.center()) + " " + Math.round(entries.get(i).dist()) + "m / Y "
                    + s.minY() + ".." + s.maxY() + " / " + s.chunks() + (s.chunks() == 1 ? " chunk" : " chunks");
            Draw.text(g, detail, x + 20, ry + 10, SUB);

            int bx = x + 20, bw = w - 28;
            Draw.rect(g, bx, ry + 21, bw, 2, LINE);
            Draw.rect(g, bx, ry + 21, Math.max(1, bw * s.conf() / 100), 2, 0xFF000000 | rgb);
        }
        Draw.text(g, "Aim at a marker for details", x + 8, y + h - 12, 0xFF5E676E);
    }

    private static void drawTooltip(GuiGraphicsExtractor g, Minecraft mc, BaseFinder bf,
                                    List<Entry> entries, Vec3 eye, Vec3 look) {
        Entry aimed = null;
        double best = 0.9976; // about 4 degrees
        for (Entry e : entries) {
            if (e.dist() < 1) continue;
            Vec3 dir = e.site().center().subtract(eye).scale(1.0 / e.dist());
            double dot = dir.dot(look);
            if (dot > best) {
                best = dot;
                aimed = e;
            }
        }
        if (aimed == null) return;

        BaseFinder.Site s = aimed.site();
        int rgb = bf.colorOf(s);
        List<String> lines = new ArrayList<>();
        lines.add(s.label() + "  " + s.conf() + "%");
        lines.add(s.counts());
        lines.addAll(s.evidence());

        String top = "BASE ESP";
        String dist = Math.round(aimed.dist()) + "m";
        String score = s.score() + " score";

        int w = Draw.width(top) + Draw.width(dist) + 24;
        for (String l : lines) w = Math.max(w, Draw.width(l) + 16);
        w = Math.max(w, Draw.width(score) + Draw.width(lines.get(0)) + 28);
        int h = 14 + lines.size() * 11 + 6;

        int x = mc.getWindow().getGuiScaledWidth() / 2 + 14;
        int y = mc.getWindow().getGuiScaledHeight() / 2 - h / 2 - 10;

        Draw.round(g, x, y, w, h, 4, PANEL_BG);
        Draw.rect(g, x, y + 3, 2, h - 6, 0xFF000000 | rgb);
        Draw.text(g, top, x + 8, y + 4, SUB);
        Draw.text(g, dist, x + w - 8 - Draw.width(dist), y + 4, SUB);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? TEXT : SUB;
            Draw.text(g, lines.get(i), x + 8, y + 15 + i * 11, color);
        }
        Draw.text(g, score, x + w - 8 - Draw.width(score), y + 15, SUB);
    }
}
