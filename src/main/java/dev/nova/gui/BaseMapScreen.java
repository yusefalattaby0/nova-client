package dev.nova.gui;

import dev.nova.NovaClient;
import dev.nova.module.ModuleManager;
import dev.nova.module.client.GuiSettings;
import dev.nova.module.render.BaseFinder;
import dev.nova.module.render.Trails;
import dev.nova.util.Colors;
import dev.nova.util.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Top-down chunk map around you, with three layers that match the three signals Base Finder uses:
 * chunk analysis (the rating heat map), live players (markers, names and trails) and resources
 * (spawners and storage). The right side shows how strong each signal is and the likeliest base.
 */
public class BaseMapScreen extends Screen {
    private static final int R = 8;               // chunks each way from you
    private static final int N = R * 2 + 1;

    private static final int PANEL = 0xF0101015;
    private static final int PANEL_LINE = 0xFF24242C;
    private static final int TEXT = 0xFFF2F2F7;
    private static final int SUB = 0xFF8A8A99;
    private static final int YELLOW = 0xFFE9B949, PURPLE = 0xFF9D6BFF, LILAC = 0xFFC9B4FF;

    private record Poi(double x, double z, int kind) {}   // kind 0 = spawner, 1 = storage
    private record Marker(String name, double x, double z, int rgb, UUID id) {}

    private final int closeKey = Compat.boundKeyCode(NovaClient.BASE_MAP_KEY, GLFW.GLFW_KEY_B);

    private boolean showChunks = true, showPlayers = true, showResources = true;

    private final int[] terrain = new int[N * N];
    private long lastScan;
    private final List<Poi> pois = new ArrayList<>();
    private final List<Marker> players = new ArrayList<>();

    // chip hit boxes {x, y, w, h, layer}
    private final List<int[]> chipHits = new ArrayList<>();

    public BaseMapScreen() {
        super(Component.literal("Base Finder"));
    }

    @Override public boolean isPauseScreen() { return false; }

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
        if (e.button() == 0) {
            for (int[] h : chipHits) {
                if (e.x() >= h[0] && e.x() < h[0] + h[2] && e.y() >= h[1] && e.y() < h[1] + h[3]) {
                    switch (h[4]) {
                        case 0 -> showChunks = !showChunks;
                        case 1 -> showPlayers = !showPlayers;
                        default -> showResources = !showResources;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    // ---------------------------------------------------------------- scanning

    private void scan(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        BlockPos pp = mc.player.blockPosition();
        int pcx = pp.getX() >> 4, pcz = pp.getZ() >> 4;

        pois.clear();
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int cx = pcx + dx, cz = pcz + dz;
                int idx = (dz + R) * N + (dx + R);
                ChunkAccess access = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) {
                    terrain[idx] = 0xFF17171C;
                    continue;
                }

                int wx = (cx << 4) + 8, wz = (cz << 4) + 8;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                BlockPos top = new BlockPos(wx, y - 1, wz);
                BlockState state = level.getBlockState(top);
                int col = state.getMapColor(level, top).col;
                int r = ((col >> 16) & 255) * 60 / 100, g = ((col >> 8) & 255) * 60 / 100, b = (col & 255) * 60 / 100;
                int shade = ((cx * 31 + cz * 17) & 3) * 5;
                terrain[idx] = 0xFF000000 | (Math.min(255, r + shade) << 16) | (Math.min(255, g + shade) << 8) | Math.min(255, b + shade);

                int storage = 0;
                double sx = 0, sz = 0;
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    Block bl = be.getBlockState().getBlock();
                    BlockPos bp = be.getBlockPos();
                    if (bl == Blocks.SPAWNER || bl == Blocks.TRIAL_SPAWNER) {
                        pois.add(new Poi(bp.getX() + 0.5, bp.getZ() + 0.5, 0));
                    } else if (bl instanceof ChestBlock || bl == Blocks.BARREL || bl instanceof ShulkerBoxBlock) {
                        storage++;
                        sx += bp.getX() + 0.5;
                        sz += bp.getZ() + 0.5;
                    }
                }
                if (storage > 0) pois.add(new Poi(sx / storage, sz / storage, 1));
            }
        }

        players.clear();
        Entity self = mc.player;
        double reach = R * 16 + 32;
        for (Entity e : level.getEntities(self, self.getBoundingBox().inflate(reach), x -> x instanceof Player)) {
            if (e.isRemoved()) continue;
            int rgb = Colors.hsb((e.getUUID().hashCode() & 0xFFFF) / 65535f, 0.65f, 1f);
            players.add(new Marker(e.getName().getString(), e.getX(), e.getZ(), rgb, e.getUUID()));
        }
    }

    // ---------------------------------------------------------------- drawing

    private static int ratingColor(int rating) {
        if (rating >= 80) return 0xFF3B3B;
        if (rating >= 60) return 0xFF9A3B;
        if (rating >= 40) return 0xFFE74D;
        return 0x4DA3FF;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScan > 1000) {
            scan(mc);
            lastScan = now;
        }
        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        boolean bfOn = bf != null && bf.enabled();
        int accent = 0xFF000000 | GuiSettings.accent();

        g.fill(0, 0, width, height, 0x90000000);

        int cell = Math.max(8, Math.min(19, (height - 110) / N));
        int mapPx = cell * N;
        int leftW = mapPx + 20, leftH = mapPx + 60;
        int rightW = 236;
        int px = (width - (leftW + 12 + rightW)) / 2;
        int py = Math.max(6, (height - leftH) / 2);
        int mapX = px + 10, mapY = py + 34;

        BlockPos pp = mc.player.blockPosition();
        int pcx = pp.getX() >> 4, pcz = pp.getZ() >> 4;

        // ---- left panel: header + chips ----
        Draw.roundBorder(g, px, py, leftW, leftH, 5, PANEL, PANEL_LINE);
        Draw.round(g, px + 10, py + 10, 10, 10, 3, accent);
        Draw.bold(g, "Base Finder", px + 26, py + 11, TEXT);

        chipHits.clear();
        String[] names = {"CHUNK ANALYSIS", "LIVE PLAYERS", "RESOURCES"};
        int[] fills = {YELLOW, PURPLE, LILAC};
        boolean[] on = {showChunks, showPlayers, showResources};
        int cx = px + leftW - 10;
        for (int i = 2; i >= 0; i--) {
            int w = Draw.width(names[i]) + 10;
            cx -= w;
            int fill = on[i] ? fills[i] : 0xFF2A2A33;
            Draw.round(g, cx, py + 9, w, 13, 4, fill);
            Draw.text(g, names[i], cx + 5, py + 12, on[i] ? 0xFF14141A : SUB);
            chipHits.add(new int[]{cx, py + 9, w, 13, i});
            cx -= 4;
        }

        // ---- terrain ----
        for (int dz = 0; dz < N; dz++) {
            for (int dx = 0; dx < N; dx++) {
                int x0 = mapX + dx * cell, y0 = mapY + dz * cell;
                g.fill(x0, y0, x0 + cell, y0 + cell, terrain[dz * N + dx]);
                g.fill(x0 + cell - 1, y0, x0 + cell, y0 + cell, 0x40000000);
                g.fill(x0, y0 + cell - 1, x0 + cell, y0 + cell, 0x40000000);
            }
        }

        // ---- chunk analysis layer ----
        BaseFinder.Cell likely = null;
        List<BaseFinder.Cell> cells = bfOn ? bf.cells() : List.of();
        for (BaseFinder.Cell c : cells) {
            int dx = c.cx() - pcx, dz = c.cz() - pcz;
            if (Math.abs(dx) > R || Math.abs(dz) > R) continue;
            if (likely == null || c.rating() > likely.rating()) likely = c;
            if (!showChunks || c.rating() < 10) continue;
            int x0 = mapX + (dx + R) * cell, y0 = mapY + (dz + R) * cell;
            int alpha = Math.min(0xA0, 0x30 + c.rating());
            g.fill(x0, y0, x0 + cell, y0 + cell, Colors.argb(alpha, ratingColor(c.rating())));
            if (c.inSite()) Draw.border(g, x0, y0, cell, cell, 0xCCFFFFFF);
        }

        // ---- likely base highlight ----
        if (likely != null && likely.rating() >= 25) {
            int x0 = mapX + (likely.cx() - pcx + R) * cell, y0 = mapY + (likely.cz() - pcz + R) * cell;
            Draw.border(g, x0 - 3, y0 - 3, cell + 6, cell + 6, 0xFFFF5566);
            Draw.border(g, x0 - 6, y0 - 6, cell + 12, cell + 12, 0x80FF5566);
        }

        // ---- trails ----
        Trails trails = Trails.get();
        if (showPlayers && trails != null && trails.enabled()) {
            for (Trails.Path p : trails.snapshot()) {
                int i = 0;
                for (Vec3 pt : p.points) {
                    if (i++ % 2 == 1) continue;
                    int sx = toX(pt.x, pcx, mapX, cell), sy = toY(pt.z, pcz, mapY, cell);
                    if (sx < mapX || sy < mapY || sx > mapX + mapPx - 2 || sy > mapY + mapPx - 2) continue;
                    g.fill(sx, sy, sx + 2, sy + 2, Colors.argb(0xB0, p.rgb));
                }
            }
        }

        // ---- resources ----
        if (showResources) {
            for (Poi p : pois) {
                int sx = toX(p.x(), pcx, mapX, cell), sy = toY(p.z(), pcz, mapY, cell);
                if (sx < mapX + 6 || sy < mapY + 6 || sx > mapX + mapPx - 6 || sy > mapY + mapPx - 6) continue;
                int c = p.kind() == 0 ? 0xFFFF5566 : 0xFFFFB347;
                Draw.disc(g, sx, sy, 7, 0xFF0B0B10);
                Draw.disc(g, sx, sy, 6, 0xFF1A1A22);
                Icons.draw(g, p.kind() == 0 ? "skull" : "chest", sx, sy, 1, c);
            }
        }

        // ---- players ----
        if (showPlayers) {
            for (Marker m : players) {
                int sx = toX(m.x(), pcx, mapX, cell), sy = toY(m.z(), pcz, mapY, cell);
                if (sx < mapX + 4 || sy < mapY + 4 || sx > mapX + mapPx - 4 || sy > mapY + mapPx - 4) continue;
                Draw.disc(g, sx, sy, 5, 0xFF0B0B10);
                Draw.disc(g, sx, sy, 3, 0xFF000000 | m.rgb());
                int w = Draw.width(m.name()) + 8;
                int lx = Math.min(sx + 8, mapX + mapPx - w - 2);
                Draw.round(g, lx, sy - 6, w, 12, 3, 0xE00B0B10);
                Draw.text(g, m.name(), lx + 4, sy - 4, 0xFF000000 | m.rgb());
            }
        }

        // ---- you ----
        double fx = (mc.player.getX() - (pcx << 4)) / 16.0, fz = (mc.player.getZ() - (pcz << 4)) / 16.0;
        int youX = mapX + (int) Math.round((R + fx) * cell), youY = mapY + (int) Math.round((R + fz) * cell);
        Draw.disc(g, youX, youY, 5, accent);
        Draw.disc(g, youX, youY, 3, 0xFFFFFFFF);

        // ---- hover info along the bottom ----
        int stripY = mapY + mapPx + 8;
        String hint;
        int hoverColor = SUB;
        if (!bfOn) {
            hint = "Turn on Base Finder to rate chunks.";
            hoverColor = 0xFFFFC857;
        } else if (mx >= mapX && my >= mapY && mx < mapX + mapPx && my < mapY + mapPx) {
            int dx = (mx - mapX) / cell - R, dz = (my - mapY) / cell - R;
            BaseFinder.Cell found = null;
            for (BaseFinder.Cell c : cells) if (c.cx() == pcx + dx && c.cz() == pcz + dz) found = c;
            hint = "Chunk [" + dx + ", " + dz + "]  X " + ((pcx + dx) << 4) + "  Z " + ((pcz + dz) << 4)
                    + (found == null ? "  no signs" : "  " + found.rating() + "% / Y " + found.minY() + ".." + found.maxY());
            hoverColor = TEXT;
        } else {
            hint = "Hover a chunk for details";
        }
        Draw.text(g, hint, px + 10, stripY + 2, hoverColor);

        // ---- right panel: signals ----
        int rx = px + leftW + 12;
        int rightH = leftH;
        Draw.roundBorder(g, rx, py, rightW, rightH, 5, PANEL, PANEL_LINE);
        Draw.text(g, "SIGNALS", rx + 12, py + 12, SUB);

        int chunkSignal = likely == null ? 0 : likely.rating();
        int playerSignal = players.isEmpty() ? 0 : 100;
        int poiSignal = Math.min(100, pois.size() * 20);

        int y = py + 30;
        signal(g, rx + 12, y, rightW - 24, "target", "Chunk analysis", "block entropy \u00B7 build density", chunkSignal, accent);
        y += 46;
        String pl = players.size() + (players.size() == 1 ? " player in range" : " players in range");
        signal(g, rx + 12, y, rightW - 24, "eye", "Live player tracking", pl, playerSignal, accent);
        y += 46;
        String rs = pois.size() + (pois.size() == 1 ? " point of interest" : " points of interest");
        signal(g, rx + 12, y, rightW - 24, "window", "Resource detection", rs, poiSignal, accent);
        y += 52;

        // ---- likely base box ----
        int boxH = 52;
        Draw.roundBorder(g, rx + 12, y, rightW - 24, boxH, 5, 0xFF26121A, 0xFF8A3346);
        Draw.text(g, "LIKELY BASE", rx + 22, y + 8, 0xFFFF6B7D);
        if (likely == null || likely.rating() < 25) {
            Draw.text(g, bfOn ? "Nothing convincing nearby yet" : "Base Finder is off", rx + 22, y + 24, SUB);
        } else {
            int dx = likely.cx() - pcx, dz = likely.cz() - pcz;
            double lcx = (likely.cx() << 4) + 8, lcz = (likely.cz() << 4) + 8;
            int agree = 1;
            boolean nearPlayer = false, nearPoi = false;
            for (Marker m : players) if (Math.abs(m.x() - lcx) < 64 && Math.abs(m.z() - lcz) < 64) nearPlayer = true;
            for (Poi p : pois) if (Math.abs(p.x() - lcx) < 48 && Math.abs(p.z() - lcz) < 48) nearPoi = true;
            if (nearPlayer) agree++;
            if (nearPoi) agree++;
            int match = Math.min(99, Math.round(likely.rating() * 0.7f + (nearPlayer ? 15 : 0) + (nearPoi ? 15 : 0)));
            String line = "Chunk [" + dx + ", " + dz + "]";
            Draw.bold(g, line, rx + 22, y + 24, TEXT);
            int lx = rx + 22 + Draw.width(line) + 6;
            Draw.text(g, "\u00B7 " + match + "% match \u00B7 " + agree + " signals agree", lx, y + 24, SUB);
            Draw.text(g, "X " + (int) lcx + "  Z " + (int) lcz + "  Y " + likely.minY() + ".." + likely.maxY(), rx + 22, y + 37, SUB);
        }
    }

    private static int toX(double wx, int pcx, int mapX, int cell) {
        return mapX + (int) Math.round((wx / 16.0 - (pcx - R)) * cell);
    }

    private static int toY(double wz, int pcz, int mapY, int cell) {
        return mapY + (int) Math.round((wz / 16.0 - (pcz - R)) * cell);
    }

    private static void signal(GuiGraphicsExtractor g, int x, int y, int w, String icon, String title, String sub,
                               int percent, int accent) {
        Icons.draw(g, icon, x + 6, y + 8, 2, YELLOW);
        Draw.bold(g, title, x + 22, y, TEXT);
        Draw.text(g, sub, x + 22, y + 11, SUB);
        String pct = percent + "%";
        Draw.bold(g, pct, x + w - Draw.width(pct), y, TEXT);
        int barX = x + 22, barW = w - 22, barY = y + 26;
        Draw.rect(g, barX, barY, barW, 3, 0xFF2A2A33);
        int fill = Math.round(barW * percent / 100f);
        for (int i = 0; i < fill; i += 2) {
            int c = Draw.mix(YELLOW, PURPLE, i / (float) barW);
            g.fill(barX + i, barY, barX + Math.min(i + 2, fill), barY + 3, c);
        }
    }
}
