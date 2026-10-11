package dev.nova.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Thin wrapper over the GUI drawing calls so version changes only touch this file.
 */
final class Draw {
    private Draw() {}

    static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    /** Rounded rectangle built from row fills; fine for radii up to ~6. */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            rect(g, x, y, w, h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double dx = Math.sqrt(r * (double) r - dy * dy);
            int inset = r - (int) Math.round(dx);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(Minecraft.getInstance().font, s, x, y, color, false);
    }

    static int width(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    static void clipOn(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    static void clipOff(GuiGraphicsExtractor g) {
        g.disableScissor();
    }
}
