package dev.nova.gui;

import dev.nova.module.ModuleManager;
import dev.nova.module.client.GuiSettings;
import dev.nova.module.render.BaseFinder;
import dev.nova.module.utility.AutoRtp;
import dev.nova.module.utility.InfoHud;
import dev.nova.util.Compat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** On-screen boxes for the utility modules: Auto RTP panel and log, and the coordinates box. */
public final class UtilityHud {
    private UtilityHud() {}

    private static final int PANEL = 0xD00B1216;
    private static final int TEXT = 0xFFF2F5F7;
    private static final int SUB = 0xFF8A949C;
    private static final int LINE = 0x33FFFFFF;

    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        AutoRtp rtp = AutoRtp.get();
        InfoHud info = InfoHud.get();
        boolean rtpOn = rtp != null && rtp.enabled();

        boolean coords = (info != null && info.enabled()) || (rtpOn && rtp.coordsOn());
        if (coords) drawCoords(g, mc, info == null || info.biomeOn());
        if (rtpOn && rtp.panelOn()) {
            drawRtpPanel(g, mc, rtp);
            drawRtpLog(g, mc, rtp);
        }
    }

    private static void drawCoords(GuiGraphicsExtractor g, Minecraft mc, boolean biome) {
        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        int x = (bf != null && bf.enabled() && bf.panelOn()) ? 214 : 6;
        int y = 6;
        String xyz = String.format("XYZ: %d / %d / %d", (int) Math.floor(mc.player.getX()),
                (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
        String bio = "BIOME: " + Compat.biomeName(mc);
        int w = Math.max(Draw.width(xyz), biome ? Draw.width(bio) : 0) + 16;
        int h = biome ? 30 : 18;
        Draw.round(g, x, y, w, h, 3, PANEL);
        Draw.text(g, xyz, x + 8, y + 6, 0xFFDDE3E8);
        if (biome) Draw.text(g, bio, x + 8, y + 18, 0xFF7FD6FF);
    }

    private static String shortDistance(int blocks) {
        if (blocks >= 1000 && blocks % 1000 == 0) return (blocks / 1000) + "K";
        if (blocks >= 1000) return String.format("%.1fK", blocks / 1000.0);
        return Integer.toString(blocks);
    }

    private static void drawRtpPanel(GuiGraphicsExtractor g, Minecraft mc, AutoRtp rtp) {
        int accent = 0xFF000000 | GuiSettings.accent();
        int w = 156, h = 84;
        int x = mc.getWindow().getGuiScaledWidth() - w - 6, y = 22;

        Draw.round(g, x, y, w, h, 4, PANEL);
        Draw.round(g, x + 8, y + 8, 10, 10, 3, accent);
        Draw.bold(g, "Auto RTP", x + 24, y + 9, TEXT);
        Draw.pill(g, x + w - 34, y + 8, true, accent);
        Draw.rect(g, x + 8, y + 24, w - 16, 1, LINE);

        boolean done = rtp.state() == AutoRtp.State.DONE;
        Draw.text(g, "ATTEMPT", x + 10, y + 30, SUB);
        Draw.text(g, done ? "STATUS" : "LOOKING FOR", x + 76, y + 30, SUB);
        Draw.bold(g, Integer.toString(rtp.attempt()), x + 10, y + 41, TEXT);
        Draw.text(g, done ? "found land" : "unclaimed land", x + 76, y + 41, done ? 0xFF6BCB77 : TEXT);

        int cx = x + 10;
        cx += Draw.chip(g, "MIN DISTANCE " + shortDistance(rtp.minDistanceBlocks()), cx, y + 62, 0xFF1D262C, 0xFFB7C2CA) + 4;
        if (rtp.avoidsWater() && cx + Draw.width("AVOID WATER") + 10 < x + w) {
            Draw.chip(g, "AVOID WATER", cx, y + 62, 0xFF1D262C, 0xFFB7C2CA);
        }
    }

    private static void drawRtpLog(GuiGraphicsExtractor g, Minecraft mc, AutoRtp rtp) {
        List<AutoRtp.Line> lines = rtp.lines();
        if (lines.isEmpty()) return;
        int w = 6;
        for (AutoRtp.Line l : lines) w = Math.max(w, Draw.width(l.text() + " " + l.status()));
        w += 16;
        int h = lines.size() * 11 + 8;
        int x = 6;
        int y = mc.getWindow().getGuiScaledHeight() - 60 - h;
        Draw.round(g, x, y, w, h, 3, 0xA0000000);
        for (int i = 0; i < lines.size(); i++) {
            AutoRtp.Line l = lines.get(i);
            int ty = y + 5 + i * 11;
            Draw.text(g, l.text(), x + 8, ty, SUB);
            Draw.text(g, l.status(), x + 8 + Draw.width(l.text() + " "), ty, 0xFF000000 | l.rgb());
        }
    }
}
