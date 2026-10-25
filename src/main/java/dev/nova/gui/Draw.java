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

    static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Rounded rectangle with a 1px outline. */
    static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int line) {
        round(g, x, y, w, h, r, line);
        round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Filled circle from row fills. */
    static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * (double) r - dy * (double) dy + r * 0.3)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        disc(g, cx, cy, r, color);
    }

    static void textCentered(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
        text(g, s, cx - width(s) / 2, y, color);
    }

    /** Small label with a background. Returns its width. */
    static int chip(GuiGraphicsExtractor g, String s, int x, int y, int fill, int textColor) {
        int w = width(s) + 10;
        round(g, x, y, w, 12, 3, fill);
        text(g, s, x + 5, y + 2, textColor);
        return w;
    }

    static int mix(int a, int b, float t) {
        return dev.nova.util.Colors.lerp(a, b, Math.max(0f, Math.min(1f, t)));
    }

    /** Fake bold: the same text drawn twice, one pixel apart. */
    static void bold(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        text(g, s, x, y, color);
        text(g, s, x + 1, y, color);
    }

    /** Little on/off pill, like the ClickGUI switch. */
    static void pill(GuiGraphicsExtractor g, int x, int y, boolean on, int accent) {
        round(g, x, y, 26, 12, 6, on ? accent : 0xFF2E2E39);
        int kx = x + (on ? 16 : 2);
        round(g, kx, y + 2, 8, 8, 4, 0xFFFFFFFF);
    }
}
