package dev.nova.gui;

import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.module.client.GuiSettings;
import dev.nova.setting.Setting;
import dev.nova.util.Colors;
import dev.nova.util.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modern ClickGUI: sidebar of categories, module cards that expand to show settings.
 * Immediate-mode: every frame the draw code records hit rectangles, mouse events use them.
 */
public class ClickGui extends Screen {
    private static final int W = 520, H = 330, SIDE = 116;
    private static final int HEADER = 30;

    // theme
    private static final int SIDEBAR = 0xFF0E0E13;
    private static final int CARD = 0xFF1B1B22;
    private static final int CARD_HOVER = 0xFF21212A;
    private static final int TEXT = 0xFFEDEDF2;
    private static final int SUBTEXT = 0xFF8A8A99;
    private static final int TRACK = 0xFF2E2E39;
    private static final int PILL = 0xFF2A2A34;

    private interface Click { void on(double mx, double my, int button); }
    private interface Drag { void on(double mx); }

    private record Hit(int x, int y, int w, int h, Click click, Drag drag) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private Module.Category tab = Module.Category.RENDER;
    private final Set<Module> expanded = new HashSet<>();
    private final Set<Setting<?>> openPickers = new HashSet<>();
    private final Map<Object, Float> anims = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private Hit dragging;

    private double scroll;
    private int contentH, viewH;
    private int px, py;
    // current clip viewport for content hits
    private int clipY, clipH;

    public ClickGui() {
        super(Component.literal("Nova"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        int btn = e.button();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mx, my)) continue;
            if (h.click() != null) h.click().on(mx, my, btn);
            if (h.drag() != null && btn == 0) {
                h.drag().on(mx);
                dragging = h;
            }
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null && dragging.drag() != null) {
            dragging.drag().on(e.x());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = clampScroll(scroll - sy * 22);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private double clampScroll(double v) {
        return Math.max(0, Math.min(v, Math.max(0, contentH - viewH)));
    }

    // ----------------------------------------------------------------- helpers

    private float anim(Object key, float target) {
        float v = anims.getOrDefault(key, target);
        v += (target - v) * 0.28f;
        if (Math.abs(target - v) < 0.01f) v = target;
        anims.put(key, v);
        return v;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** True if a content rectangle is at least partly inside the scroll viewport. */
    private boolean visibleInClip(int y, int h) {
        return y + h > clipY && y < clipY + clipH;
    }

    private void hit(int x, int y, int w, int h, Click click, Drag drag) {
        hits.add(new Hit(x, y, w, h, click, drag));
    }

    private void contentHit(int x, int y, int w, int h, Click click, Drag drag) {
        if (visibleInClip(y, h)) {
            // clamp the rectangle to the viewport so it can't be clicked outside it
            int top = Math.max(y, clipY);
            int bottom = Math.min(y + h, clipY + clipH);
            hits.add(new Hit(x, top, w, bottom - top, click, drag));
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        int accent = 0xFF000000 | GuiSettings.accent();
        int r = GuiSettings.radius();

        if (GuiSettings.dimWorld()) g.fill(0, 0, width, height, 0x90000000);

        px = (width - W) / 2;
        py = (height - H) / 2;

        // window + sidebar
        Draw.round(g, px, py, W, H, r + 2, Colors.argb(GuiSettings.backgroundAlpha(), 0x15151B));
        Draw.round(g, px, py, SIDE, H, r + 2, SIDEBAR);
        Draw.rect(g, px + SIDE - r - 2, py, r + 2, H, SIDEBAR);

        // logo
        Draw.round(g, px + 14, py + 16, 3, 22, 1, accent);
        Draw.text(g, "NOVA", px + 24, py + 17, TEXT);
        Draw.text(g, "client", px + 24, py + 28, SUBTEXT);

        // category tabs
        int ty = py + 60;
        for (Module.Category c : Module.Category.values()) {
            boolean selected = c == tab;
            boolean hover = over(mx, my, px + 8, ty, SIDE - 16, 24);
            float a = anim("tab:" + c, selected ? 1f : hover ? 0.5f : 0f);
            if (a > 0) Draw.round(g, px + 8, ty, SIDE - 16, 24, 4, Colors.argb(Math.round(a * 0x30), GuiSettings.accent()));
            if (selected) Draw.round(g, px + 8, ty + 5, 2, 14, 1, accent);
            Draw.text(g, c.label, px + 20, ty + 8, selected ? TEXT : SUBTEXT);
            final Module.Category cat = c;
            hit(px + 8, ty, SIDE - 16, 24, (x, y, b) -> { tab = cat; scroll = 0; }, null);
            ty += 28;
        }
        Draw.text(g, "RShift to close", px + 14, py + H - 18, 0xFF55556A);

        // content header
        int cx = px + SIDE + 16;
        int cw = W - SIDE - 32;
        Draw.text(g, tab.label, cx, py + 16, TEXT);
        List<Module> mods = ModuleManager.in(tab);
        Draw.text(g, mods.size() + (mods.size() == 1 ? " module" : " modules"), cx, py + 28, SUBTEXT);

        // scrolling content
        int cy = py + 46;
        int ch = H - 46 - 14;
        clipY = cy;
        clipH = ch;
        viewH = ch;
        scroll = clampScroll(scroll);

        Draw.clipOn(g, cx - 2, cy, cw + 4, ch);
        int y = cy - (int) scroll;
        for (Module m : mods) {
            y += card(g, m, cx, y, cw, mx, my, accent) + 8;
        }
        contentH = y + (int) scroll - cy;
        Draw.clipOff(g);

        // scrollbar
        if (contentH > viewH) {
            int barH = Math.max(24, (int) (viewH * (viewH / (double) contentH)));
            int barY = cy + (int) ((viewH - barH) * (scroll / (contentH - viewH)));
            Draw.round(g, px + W - 8, barY, 3, barH, 1, Colors.argb(0x90, GuiSettings.accent()));
        }
    }

    // -------------------------------------------------------------------- card

    private static int rowHeight(Setting<?> s, boolean pickerOpen) {
        if (s instanceof Setting.Num) return 32;
        if (s instanceof Setting.Col) return 24 + (pickerOpen ? 46 : 0);
        return 22;
    }

    private int card(GuiGraphicsExtractor g, Module m, int x, int y, int w, int mx, int my, int accent) {
        boolean open = expanded.contains(m);

        List<Setting<?>> visible = new ArrayList<>();
        for (Setting<?> s : m.settings()) if (s.isVisible()) visible.add(s);

        int total = HEADER;
        if (open && !visible.isEmpty()) {
            total += 6;
            for (Setting<?> s : visible) total += rowHeight(s, openPickers.contains(s));
            total += 4;
        }

        boolean inView = visibleInClip(y, total);
        boolean hoverHeader = inView && over(mx, my, x, Math.max(y, clipY), w, HEADER)
                && my >= clipY && my < clipY + clipH;

        float hoverA = anim("hover:" + m.name, hoverHeader ? 1f : 0f);
        Draw.round(g, x, y, w, total, 5, Colors.lerp(CARD, CARD_HOVER, hoverA));

        float on = anim(m, m.enabled() ? 1f : 0f);
        if (on > 0.02f) Draw.round(g, x, y + 7, 2, HEADER - 14, 1, Colors.argb(Math.round(on * 255), GuiSettings.accent()));

        Draw.text(g, m.name, x + 14, y + 6, TEXT);
        Draw.text(g, m.description, x + 14, y + 17, SUBTEXT);

        int right = x + w - 12;
        if (m.toggleable()) {
            toggleSwitch(g, right - 26, y + 9, on, accent);
            right -= 40;
        }
        boolean hasSettings = !m.settings().isEmpty();
        if (hasSettings) {
            Draw.text(g, open ? "v" : ">", right - 8, y + 11, hoverHeader ? TEXT : SUBTEXT);
        }

        contentHit(x, y, w, HEADER, (cx, cy, b) -> {
            if (b == 1 && hasSettings) toggleOpen(m);
            else if (b == 0 && m.toggleable()) m.toggle();
            else if (b == 0 && hasSettings) toggleOpen(m);
        }, null);
        if (hasSettings) {
            contentHit(right - 16, y, 24, HEADER, (cx, cy, b) -> { if (b == 0) toggleOpen(m); }, null);
        }

        if (open && !visible.isEmpty()) {
            int sy = y + HEADER + 6;
            Draw.rect(g, x + 10, y + HEADER, w - 20, 1, 0xFF2A2A34);
            for (Setting<?> s : visible) {
                sy += row(g, s, x + 14, sy, w - 28, mx, my, accent);
            }
        }
        return total;
    }

    private void toggleOpen(Module m) {
        if (!expanded.remove(m)) expanded.add(m);
    }

    private void toggleSwitch(GuiGraphicsExtractor g, int x, int y, float t, int accent) {
        Draw.round(g, x, y, 26, 12, 6, Colors.lerp(TRACK, accent, t));
        int kx = x + 2 + Math.round(t * 14);
        Draw.round(g, kx, y + 2, 8, 8, 4, 0xFFFFFFFF);
    }

    // -------------------------------------------------------------------- rows

    private int row(GuiGraphicsExtractor g, Setting<?> s, int x, int y, int w, int mx, int my, int accent) {
        if (s instanceof Setting.Bool b) return boolRow(g, b, x, y, w, accent);
        if (s instanceof Setting.Num n) return numRow(g, n, x, y, w, accent);
        if (s instanceof Setting.Enm<?> e) return enumRow(g, e, x, y, w, mx, my);
        if (s instanceof Setting.Col c) return colorRow(g, c, x, y, w, accent);
        return 0;
    }

    private int boolRow(GuiGraphicsExtractor g, Setting.Bool b, int x, int y, int w, int accent) {
        Draw.text(g, b.name, x, y + 7, TEXT);
        float t = anim(b, b.get() ? 1f : 0f);
        toggleSwitch(g, x + w - 26, y + 5, t, accent);
        contentHit(x, y, w, 22, (cx, cy, btn) -> { if (btn == 0) b.toggle(); }, null);
        return 22;
    }

    private int numRow(GuiGraphicsExtractor g, Setting.Num n, int x, int y, int w, int accent) {
        Draw.text(g, n.name, x, y + 3, TEXT);
        String val = n.display();
        Draw.text(g, val, x + w - Draw.width(val), y + 3, SUBTEXT);

        int trackY = y + 20;
        float p = anim(n, (float) n.progress());
        int fillW = Math.round(p * w);
        Draw.round(g, x, trackY, w, 4, 2, TRACK);
        if (fillW > 0) Draw.round(g, x, trackY, Math.max(4, fillW), 4, 2, accent);
        Draw.round(g, x + fillW - 4, trackY - 2, 8, 8, 4, 0xFFFFFFFF);

        contentHit(x - 4, y + 12, w + 8, 18, null, mxx -> {
            double t = Math.max(0, Math.min(1, (mxx - x) / (double) w));
            n.set(n.min + t * (n.max - n.min));
        });
        return 32;
    }

    private int enumRow(GuiGraphicsExtractor g, Setting.Enm<?> e, int x, int y, int w, int mx, int my) {
        Draw.text(g, e.name, x, y + 7, TEXT);
        String val = e.get().toString();
        int pw = Draw.width(val) + 16;
        int pxx = x + w - pw;
        boolean hover = over(mx, my, pxx, y + 3, pw, 16) && my >= clipY && my < clipY + clipH;
        Draw.round(g, pxx, y + 3, pw, 16, 8, hover ? 0xFF343440 : PILL);
        Draw.text(g, val, pxx + 8, y + 7, 0xFF000000 | GuiSettings.accent());
        contentHit(pxx, y + 3, pw, 16, (cx, cy, btn) -> {
            if (btn == 0) e.cycle(1);
            else if (btn == 1) e.cycle(-1);
        }, null);
        return 22;
    }

    private int colorRow(GuiGraphicsExtractor g, Setting.Col c, int x, int y, int w, int accent) {
        boolean open = openPickers.contains(c);
        Draw.text(g, c.name, x, y + 8, TEXT);

        int swX = x + w - 28;
        Draw.round(g, swX - 1, y + 4, 30, 16, 5, open ? accent : 0xFF3A3A47);
        Draw.round(g, swX, y + 5, 28, 14, 4, 0xFF000000 | c.get());
        contentHit(x, y, w, 24, (cx, cy, btn) -> {
            if (btn != 0) return;
            if (!openPickers.remove(c)) openPickers.add(c);
        }, null);

        if (open) {
            float[] hsb = c.hsb();
            bar(g, c, x, y + 28, w, 0, hsb);
            bar(g, c, x, y + 28 + 14, w, 1, hsb);
            bar(g, c, x, y + 28 + 28, w, 2, hsb);
        }
        return rowHeight(c, open);
    }

    /** kind 0 = hue, 1 = saturation, 2 = brightness. */
    private void bar(GuiGraphicsExtractor g, Setting.Col c, int x, int y, int w, int kind, float[] hsb) {
        final int step = 2;
        for (int i = 0; i < w; i += step) {
            float t = i / (float) w;
            int rgb = switch (kind) {
                case 0 -> Colors.hsb(t, 1f, 1f);
                case 1 -> Colors.hsb(hsb[0], t, Math.max(hsb[2], 0.35f));
                default -> Colors.hsb(hsb[0], hsb[1], t);
            };
            g.fill(x + i, y, x + Math.min(i + step, w), y + 8, 0xFF000000 | rgb);
        }
        float v = hsb[kind];
        int mxp = x + Math.round(v * w);
        Draw.rect(g, mxp - 1, y - 2, 3, 12, 0xFFFFFFFF);

        contentHit(x, y - 3, w, 14, null, mxx -> {
            float t = (float) Math.max(0, Math.min(1, (mxx - x) / (double) w));
            float[] cur = c.hsb();
            switch (kind) {
                case 0 -> c.setHsb(t, cur[1] == 0 ? 1f : cur[1], cur[2] == 0 ? 1f : cur[2]);
                case 1 -> c.setHsb(cur[0], t, cur[2] == 0 ? 1f : cur[2]);
                default -> c.setHsb(cur[0], cur[1], t);
            }
        });
    }
}
