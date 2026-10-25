package dev.nova.gui;

import dev.nova.NovaClient;
import dev.nova.module.client.GuiSettings;
import dev.nova.module.utility.RegionMap;
import dev.nova.util.Compat;
import dev.nova.util.RegionPing;
import dev.nova.util.WorldMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Dotted world map with live pings per region, a ranked list, and a connect button. */
public class RegionMapScreen extends Screen {
    private static final int PANEL = 0xF0101015;
    private static final int PANEL_LINE = 0xFF24242C;
    private static final int TEXT = 0xFFF2F2F7;
    private static final int SUB = 0xFF8A8A99;
    private static final int DOT = 0xFF34343E;

    private static final int GREEN = 0xFF6BCB77, YELLOW = 0xFFFFC857, ORANGE = 0xFFFF9A3B, RED = 0xFFFF5555;

    private final int closeKey = Compat.boundKeyCode(NovaClient.REGION_KEY, GLFW.GLFW_KEY_N);
    private RegionPing.Region selected;
    private String status = "";

    // hit boxes recorded while drawing
    private int connX, connY, connW, connH;
    private final List<int[]> rowHits = new ArrayList<>(); // {x, y, w, h, regionIndexInRanked}
    private List<RegionPing.Region> rankedNow = List.of();

    public RegionMapScreen() {
        super(Component.literal("Region Map"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        RegionPing.refresh();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == closeKey) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (e.button() == 0) {
            if (mx >= connX && mx < connX + connW && my >= connY && my < connY + connH) {
                RegionPing.Region r = selected != null ? selected : RegionPing.best();
                if (r != null) status = RegionPing.connect(r);
                return true;
            }
            for (int[] h : rowHits) {
                if (mx >= h[0] && mx < h[0] + h[2] && my >= h[1] && my < h[1] + h[3] && h[4] < rankedNow.size()) {
                    selected = rankedNow.get(h[4]);
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    private static int pingColor(int ms, int good, int bad) {
        if (ms < 0) return SUB;
        if (ms < good) return GREEN;
        if (ms <= (good + bad) / 2) return YELLOW;
        if (ms <= bad) return ORANGE;
        return RED;
    }

    private static String msText(int ms) {
        return ms < 0 ? "--" : ms + "ms";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        RegionMap mod = RegionMap.get();
        int good = mod == null ? 60 : mod.goodBelow();
        int bad = mod == null ? 150 : mod.badAbove();
        int refreshMs = mod == null ? 5000 : mod.refreshMillis();
        if (RegionPing.millisSinceRefresh() > refreshMs) RegionPing.refresh();

        g.fill(0, 0, width, height, 0x90000000);

        int sp = Math.max(2, Math.min(4, (width - 230) / 100));
        int mapW = WorldMap.W * sp, mapH = WorldMap.H * sp;
        int panelW = mapW + 20, panelH = mapH + 40;
        int rightW = 172;
        int total = panelW + 12 + rightW;
        int px = (width - total) / 2;
        int py = Math.max(8, (height - panelH - 44) / 2);
        int accent = 0xFF000000 | GuiSettings.accent();

        rankedNow = RegionPing.ranked();
        RegionPing.Region best = RegionPing.best();
        if (selected == null) selected = best;

        // ---- map panel ----
        Draw.roundBorder(g, px, py, panelW, panelH, 5, PANEL, PANEL_LINE);
        Draw.round(g, px + 10, py + 9, 10, 10, 3, accent);
        Draw.bold(g, "Region Map", px + 26, py + 10, TEXT);
        String live = "LIVE PING";
        int lw = Draw.width(live) + 18;
        Draw.roundBorder(g, px + panelW - lw - 10, py + 8, lw, 13, 6, 0xFF2A2412, 0xFF8A6A1F);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        Draw.disc(g, px + panelW - lw - 10 + 8, py + 14, 2, blink ? YELLOW : 0xFF8A6A1F);
        Draw.text(g, live, px + panelW - lw - 10 + 14, py + 11, YELLOW);

        int ox = px + 10, oy = py + 30;
        int dotSize = Math.max(1, sp - 1);
        for (int y = 0; y < WorldMap.H; y++) {
            for (int x = 0; x < WorldMap.W; x++) {
                if (WorldMap.land(x, y)) g.fill(ox + x * sp, oy + y * sp, ox + x * sp + dotSize, oy + y * sp + dotSize, DOT);
            }
        }

        // positions
        int n = RegionPing.REGIONS.size();
        int[] nx = new int[n], ny = new int[n];
        for (int i = 0; i < n; i++) {
            RegionPing.Region r = RegionPing.REGIONS.get(i);
            nx[i] = ox + (int) Math.round(WorldMap.col(r.lon) * sp);
            ny[i] = oy + (int) Math.round(WorldMap.row(r.lat) * sp);
        }
        int bi = best == null ? 0 : RegionPing.REGIONS.indexOf(best);
        int youX = nx[bi] - 8, youY = ny[bi] + 3;

        // glows, then dotted arcs, then markers and labels
        for (int i = 0; i < n; i++) {
            int c = pingColor(RegionPing.REGIONS.get(i).ping, good, bad) & 0xFFFFFF;
            Draw.disc(g, nx[i], ny[i], 4 * sp + 6, 0x14000000 | c);
            Draw.disc(g, nx[i], ny[i], 2 * sp + 4, 0x24000000 | c);
        }
        for (int i = 0; i < n; i++) {
            if (i == bi) continue;
            int c = pingColor(RegionPing.REGIONS.get(i).ping, good, bad) & 0xFFFFFF;
            arc(g, youX, youY, nx[i], ny[i], 0xB0000000 | c);
        }
        for (int i = 0; i < n; i++) {
            RegionPing.Region r = RegionPing.REGIONS.get(i);
            int c = pingColor(r.ping, good, bad);
            Draw.disc(g, nx[i], ny[i], 4, 0xFF0A0A0E);
            Draw.disc(g, nx[i], ny[i], 3, c);
        }
        Draw.disc(g, youX, youY, 3, 0xFFFFFFFF);
        Draw.text(g, "YOU", youX - 8, youY + 6, TEXT);
        for (int i = 0; i < n; i++) {
            RegionPing.Region r = RegionPing.REGIONS.get(i);
            int c = pingColor(r.ping, good, bad);
            String name = r.name.equals("EU CENTRAL") ? "EU CENTRAL" : r.name;
            String ms = msText(r.ping);
            int w = Draw.width(name) + Draw.width(ms) + 18;
            int lx = Math.max(px + 6, Math.min(px + panelW - w - 6, nx[i] - w / 2));
            int ly = ny[i] - 22;
            if (ly < py + 26) ly = ny[i] + 8;
            Draw.roundBorder(g, lx, ly, w, 14, 4, 0xF00B0B0F, 0xFF000000 | (c & 0xFFFFFF));
            Draw.text(g, name, lx + 6, ly + 3, TEXT);
            Draw.text(g, ms, lx + w - 6 - Draw.width(ms), ly + 3, c);
        }

        // ---- best badge under the map ----
        String badge = best == null ? "MEASURING..." : "BEST: " + best.shortName + " \u00B7 " + best.ping + "MS";
        int bw = Draw.width(badge) + 20;
        int by = py + panelH + 10;
        Draw.rect(g, px, by, bw, 18, 0xFFF2F2F7);
        Draw.rect(g, px + 2, by + 2, bw - 4, 14, 0xFF0B0B0F);
        Draw.bold(g, badge, px + 10, by + 5, 0xFFFFFFFF);
        Draw.text(g, "pick the server with the best ping", px + bw + 10, by + 5, SUB);

        // ---- right panel ----
        int rx = px + panelW + 12;
        Draw.roundBorder(g, rx, py, rightW, panelH, 5, PANEL, PANEL_LINE);
        Draw.text(g, "BEST REGIONS", rx + 10, py + 12, SUB);
        rowHits.clear();
        int ry = py + 28;
        for (int i = 0; i < rankedNow.size(); i++) {
            RegionPing.Region r = rankedNow.get(i);
            int c = pingColor(r.ping, good, bad);
            boolean sel = r == selected;
            if (sel) Draw.roundBorder(g, rx + 6, ry - 3, rightW - 12, 22, 4, 0xFF10241A, GREEN);
            Draw.text(g, Integer.toString(i + 1), rx + 12, ry + 3, SUB);
            Draw.bold(g, r.name, rx + 24, ry + 3, TEXT);
            int barX = rx + 24 + 66, barW = 28;
            Draw.rect(g, barX, ry + 6, barW, 3, 0xFF2A2A33);
            float frac = r.ping < 0 ? 0f : Math.max(0.06f, Math.min(1f, 1f - r.ping / 300f));
            Draw.rect(g, barX, ry + 6, Math.max(1, Math.round(barW * frac)), 3, c);
            String ms = r.ping < 0 ? "--" : r.ping + " ms";
            Draw.bold(g, ms, rx + rightW - 12 - Draw.width(ms), ry + 3, c);
            rowHits.add(new int[]{rx + 6, ry - 3, rightW - 12, 22, i});
            ry += 26;
        }

        // connect button
        RegionPing.Region target = selected != null ? selected : best;
        connW = rightW - 20;
        connH = 22;
        connX = rx + 10;
        connY = py + panelH - connH - 12;
        boolean hover = mx >= connX && mx < connX + connW && my >= connY && my < connY + connH;
        Draw.roundBorder(g, connX, connY, connW, connH, 6, hover ? 0xFF16382A : 0xFF10241A, GREEN);
        String label = target == null ? "MEASURING..." : "CONNECT \u00B7 " + target.name;
        Draw.textCentered(g, label, connX + connW / 2, connY + 7, GREEN);

        // status message above the button
        if (!status.isEmpty()) {
            int sy = connY - 8;
            List<String> lines = wrap(status, rightW - 20);
            sy -= lines.size() * 10;
            for (String l : lines) {
                Draw.text(g, l, rx + 10, sy, 0xFFFFC857);
                sy += 10;
            }
        }
    }

    /** A dotted curve from one point to another, bowed upward. */
    private static void arc(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 6) return;
        double cx = (x0 + x1) / 2.0, cy = (y0 + y1) / 2.0 - len * 0.28;
        int steps = Math.max(8, (int) (len / 4));
        for (int i = 0; i <= steps; i += 1) {
            if (i % 2 == 1) continue;
            double t = i / (double) steps;
            double a = (1 - t) * (1 - t), b = 2 * (1 - t) * t, c = t * t;
            int x = (int) Math.round(a * x0 + b * cx + c * x1);
            int y = (int) Math.round(a * y0 + b * cy + c * y1);
            g.fill(x, y, x + 2, y + 1, color);
        }
    }

    private static List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (Draw.width(next) > maxW && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
