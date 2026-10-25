package dev.nova.gui;

import dev.nova.NovaClient;
import dev.nova.module.client.GuiSettings;
import dev.nova.module.utility.DonutWheel;
import dev.nova.util.Compat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The radial wheel. Hold the key, point at a slice, let go. */
public class WheelScreen extends Screen {
    private static final int N = 8;
    private static final int SLICE_BG = 0xEE232329;
    private static final int TEXT = 0xFFF2F2F7;
    private static final int SUB = 0xFF8A8A99;

    // shaped once per size: for each slice, a list of row runs {y, x0, x1} relative to the center
    private static int cachedOuter = -1, cachedInner = -1;
    private static List<int[]>[] runs;

    private final DonutWheel wheel;
    private final int openKey;
    private int hover = -1;

    public WheelScreen(DonutWheel wheel) {
        super(Component.literal("Wheel"));
        this.wheel = wheel;
        this.openKey = Compat.boundKeyCode(NovaClient.WHEEL_KEY, GLFW.GLFW_KEY_G);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---- input ----

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == openKey) {
            finish();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0 && wheel.clickRuns() && hover >= 0) {
            finish();
            return true;
        }
        if (e.button() == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    private void finish() {
        int pick = hover;
        onClose();
        if (pick >= 0 && pick < DonutWheel.SLICES.size()) wheel.run(DonutWheel.SLICES.get(pick));
    }

    // ---- geometry ----

    @SuppressWarnings("unchecked")
    private static void build(int outer, int inner) {
        if (outer == cachedOuter && inner == cachedInner) return;
        cachedOuter = outer;
        cachedInner = inner;
        runs = new List[N];
        for (int i = 0; i < N; i++) runs[i] = new ArrayList<>();
        for (int dy = -outer; dy <= outer; dy++) {
            int prev = -1, start = 0;
            for (int dx = -outer; dx <= outer + 1; dx++) {
                int id = dx > outer ? -1 : sliceAt(dx, dy, outer, inner, true);
                if (id != prev) {
                    if (prev >= 0) runs[prev].add(new int[]{dy, start, dx - 1});
                    prev = id;
                    start = dx;
                }
            }
        }
    }

    /** Which slice a point belongs to, or -1 outside the ring (or inside the gap between slices). */
    private static int sliceAt(int dx, int dy, int outer, int inner, boolean withGap) {
        double d2 = dx * (double) dx + dy * (double) dy;
        if (d2 > outer * (double) outer || d2 < inner * (double) inner) return -1;
        double ang = Math.toDegrees(Math.atan2(dy, dx));
        if (ang < 0) ang += 360;
        double shifted = (ang + 22.5) % 360.0;
        int id = (int) (shifted / 45.0) % N;
        if (withGap) {
            double off = shifted % 45.0;
            double toEdge = Math.min(off, 45.0 - off);
            if (Math.sqrt(d2) * Math.sin(Math.toRadians(toEdge)) < 1.5) return -1;
        }
        return id;
    }

    // ---- drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int cx = width / 2, cy = height / 2;
        int outer = Math.min(wheel.radius() + 40, Math.min(width, height) / 2 - 10);
        int inner = Math.max(30, outer * 55 / 100);
        build(outer, inner);

        g.fill(0, 0, width, height, 0x80000000);

        int dx = mx - cx, dy = my - cy;
        hover = (dx * dx + dy * dy < (inner - 6) * (inner - 6) || dx * dx + dy * dy > (outer + 40) * (outer + 40))
                ? -1 : sliceAt(dx, dy, 100000, 0, false);
        int accent = 0xFF000000 | GuiSettings.accent();

        for (int i = 0; i < N; i++) {
            int color = i == hover ? accent : SLICE_BG;
            for (int[] r : runs[i]) g.fill(cx + r[1], cy + r[0], cx + r[2] + 1, cy + r[0] + 1, color);
        }

        // icons and labels
        int mid = (outer + inner) / 2;
        List<DonutWheel.Slice> slices = DonutWheel.SLICES;
        for (int i = 0; i < N && i < slices.size(); i++) {
            DonutWheel.Slice s = slices.get(i);
            double a = Math.toRadians(i * 45.0);
            int sx = cx + (int) Math.round(Math.cos(a) * mid);
            int sy = cy + (int) Math.round(Math.sin(a) * mid);
            boolean hot = i == hover;
            boolean on = DonutWheel.isOn(s);
            Icons.draw(g, s.icon(), sx, sy - 12, 2, hot ? 0xFFFFFFFF : TEXT);
            Draw.textCentered(g, s.label(), sx, sy + 2, hot ? 0xFFFFFFFF : TEXT);
            String tag = s.kind().tag + (s.kind() == DonutWheel.Kind.TOGGLE ? (on ? " ON" : " OFF") : "");
            int tagColor = hot ? 0xFFE9E3FF : (s.kind() == DonutWheel.Kind.TOGGLE && on ? 0xFF6BCB77 : SUB);
            Draw.textCentered(g, tag, sx, sy + 13, tagColor);
        }

        // center
        int cr = inner - 8;
        Draw.disc(g, cx, cy, cr + 2, hover >= 0 ? accent : 0xFF4A4A57);
        Draw.disc(g, cx, cy, cr, 0xFF16161B);
        if (hover >= 0 && hover < slices.size()) {
            DonutWheel.Slice s = slices.get(hover);
            Draw.textCentered(g, s.label(), cx, cy - 8, TEXT);
            Draw.textCentered(g, s.kind().hint, cx, cy + 4, SUB);
            double a = Math.toRadians(hover * 45.0);
            int px = cx + (int) Math.round(Math.cos(a) * (cr - 5));
            int py = cy + (int) Math.round(Math.sin(a) * (cr - 5));
            Draw.disc(g, px, py, 2, 0xFFFFD83B);
        } else {
            Draw.textCentered(g, "Nova Wheel", cx, cy - 8, TEXT);
            Draw.textCentered(g, "MOVE TO PICK", cx, cy + 4, SUB);
        }
    }
}
