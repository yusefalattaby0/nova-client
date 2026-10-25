package dev.nova.module.render;

import dev.nova.render.EspDraw;
import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds player sites (stashes, bases, farms) by grouping player-made blocks into clusters.
 *
 * Nearby chunks that contain player-made blocks are merged into one site. Each site gets a type guess
 * (Stash / Base / Farm / Unknown) with a confidence %, shown in an on-screen list, as a marker in the world,
 * and in a tooltip when you aim at it. Sites with TNT, fire or lots of loose items are tagged "raided?".
 *
 * It only sees blocks (not what is inside chests), so every result is an educated guess.
 * Amethyst buds are also shown by growth stage, since they only grow while their chunk is loaded.
 */
public class BaseFinder extends BlockEspModule {
    // block kinds
    private static final int BUDDING = 0, SMALL = 1, MEDIUM = 2, LARGE = 3, CLUSTER = 4;
    private static final int CONTAINER = 5, HOPPER = 6, FURNACE = 7, REDSTONE = 8, WORK = 9, BED = 10,
            GLASS = 11, TORCH = 12, TNT = 13, FIRE = 14;
    // site marker kinds
    private static final int S_STASH = 15, S_BASE = 16, S_FARM = 17, S_UNKNOWN = 18, S_RAIDED = 19;
    // grouping and surface
    private final Setting.Num joinChunks = new Setting.Num("Join Distance", 3, 1, 8, 1, " chunks");
    private final Setting.Num surfaceY = new Setting.Num("Surface Level (Y)", 55, 30, 120, 1, "");
    private final Setting.Num surfacePenalty = new Setting.Num("Surface Penalty", 60, 0, 100, 5, "%");
    private final Setting.Enm<MarkerStyle> markerStyle = new Setting.Enm<>("Site Box", MarkerStyle.VOLUME);

    // per-chunk rating levels
    private static final int R_LOW = 20, R_MID = 21, R_HIGH = 22, R_MAX = 23;

    public enum Type {
        STASH("Stash"), BASE("Base"), FARM("Farm"), UNKNOWN("Unknown");
        public final String label;
        Type(String label) { this.label = label; }
    }

    /** One detected site. */
    public record Site(Type type, boolean raided, int conf, int score, Vec3 center, AABB bounds, int minY, int maxY,
                       int chunks, int sections, String counts, List<String> evidence) {
        public String label() {
            return raided ? type.label + " (raided?)" : type.label;
        }
    }

    private record ChunkRating(Vec3 pos, int rating, boolean isSite) {}

    /** How likely one chunk is to hold a base, for the map screen. */
    public record Cell(int cx, int cz, int rating, int minY, int maxY, boolean inSite) {}

    public enum MarkerStyle {
        VOLUME("Around Blocks"), MARKER("Small Marker");
        private final String label;
        MarkerStyle(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final Map<Block, Integer> KINDS = new HashMap<>();

    static {
        put(CONTAINER, Blocks.BARREL, Blocks.TRAPPED_CHEST); // ender chests are not storage here
        put(HOPPER, Blocks.HOPPER);
        put(FURNACE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER);
        put(REDSTONE, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.OBSERVER, Blocks.REPEATER, Blocks.COMPARATOR,
                Blocks.REDSTONE_LAMP, Blocks.NOTE_BLOCK, Blocks.DROPPER, Blocks.DISPENSER);
        put(WORK, Blocks.CRAFTING_TABLE, Blocks.ANVIL, Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE,
                Blocks.BEACON, Blocks.JUKEBOX);
        put(GLASS, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS);
        put(TORCH, Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
                Blocks.LANTERN, Blocks.SOUL_LANTERN);
        put(TNT, Blocks.TNT);
        put(FIRE, Blocks.FIRE, Blocks.SOUL_FIRE);
    }

    private static void put(int kind, Block... blocks) {
        for (Block b : blocks) KINDS.put(b, kind);
    }

    /** Per-chunk tally used while grouping. */
    private static final class Acc {
        final int[] counts = new int[15];
        int cx, cz, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, n, sectionMask;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        long sumX, sumY, sumZ;
        int loose;
    }

    // amethyst
    private final Setting.Bool showBudding = new Setting.Bool("Budding Amethyst", true);
    private final Setting.Bool showSmall = new Setting.Bool("Small Bud (stage 1)", true);
    private final Setting.Bool showMedium = new Setting.Bool("Medium Bud (stage 2)", true);
    private final Setting.Bool showLarge = new Setting.Bool("Large Bud (stage 3)", true);
    private final Setting.Bool showCluster = new Setting.Bool("Full Cluster (grown)", true);

    // what counts as a sign of players
    private final Setting.Bool useContainers = new Setting.Bool("Chests & Barrels", true);
    private final Setting.Bool useHoppers = new Setting.Bool("Hoppers", true);
    private final Setting.Bool useFurnaces = new Setting.Bool("Furnaces", true);
    private final Setting.Bool useRedstone = new Setting.Bool("Redstone & Pistons", true);
    private final Setting.Bool useWork = new Setting.Bool("Workstations", true);
    private final Setting.Bool useBeds = new Setting.Bool("Beds", true);
    private final Setting.Bool useGlass = new Setting.Bool("Glass", false);
    private final Setting.Bool useTorches = new Setting.Bool("Torches & Lanterns", false);
    private final Setting.Bool useDamage = new Setting.Bool("Detect Raid Damage", true);
    private final Setting.Bool showBlocks = new Setting.Bool("Show Individual Blocks", false);

    // site detection and display
    private final Setting.Num minScore = new Setting.Num("Min Site Score", 8, 2, 80, 1, " pts");
    private final Setting.Num raidSensitivity = new Setting.Num("Raid Sensitivity (lower = more)", 8, 1, 30, 1, " pts");
    private final Setting.Bool panel = new Setting.Bool("Show List Panel", true);
    private final Setting.Num panelEntries = new Setting.Num("List Entries", 5, 1, 10, 1, "");
    private final Setting.Bool tooltip = new Setting.Bool("Show Aim Tooltip", true);
    private final Setting.Bool labels = new Setting.Bool("Show World Labels", true);
    private final Setting.Num labelSize = new Setting.Num("Label Size", 1.5, 0.5, 8, 0.5, "x");
    private final Setting.Num maxLabels = new Setting.Num("Max Labels", 8, 1, 40, 1, "");

    // per-chunk rating
    private final Setting.Bool rateChunks = new Setting.Bool("Rate Chunks", true);
    private final Setting.Num minRating = new Setting.Num("Min Chunk Rating", 25, 5, 90, 5, "%");
    private final Setting.Bool chunkLabels = new Setting.Bool("Show Chunk Ratings", true);
    private final Setting.Num maxChunkLabels = new Setting.Num("Max Chunk Labels", 10, 1, 40, 1, "");

    // colors
    private final Setting.Col rateLowColor = new Setting.Col("Rating Low Color", 0x4DA3FF);
    private final Setting.Col rateMidColor = new Setting.Col("Rating Medium Color", 0xFFE74D);
    private final Setting.Col rateHighColor = new Setting.Col("Rating High Color", 0xFF9A3B);
    private final Setting.Col rateMaxColor = new Setting.Col("Rating Very High Color", 0xFF3B3B);
    private final Setting.Col buddingColor = new Setting.Col("Budding Color", 0x9D4EDD);
    private final Setting.Col smallColor = new Setting.Col("Small Bud Color", 0x6E8BFF);
    private final Setting.Col mediumColor = new Setting.Col("Medium Bud Color", 0xA06BFF);
    private final Setting.Col largeColor = new Setting.Col("Large Bud Color", 0xD36BFF);
    private final Setting.Col clusterColor = new Setting.Col("Cluster Color", 0xFF6BE6);
    private final Setting.Col blockColor = new Setting.Col("Block Color", 0xFFD83B);
    private final Setting.Col stashColor = new Setting.Col("Stash Color", 0xFF9A3B);
    private final Setting.Col baseTypeColor = new Setting.Col("Base Color", 0xFFE74D);
    private final Setting.Col farmColor = new Setting.Col("Farm Color", 0x6BCB77);
    private final Setting.Col unknownColor = new Setting.Col("Unknown Color", 0x9AA0A6);
    private final Setting.Col raidedColor = new Setting.Col("Raided Color", 0xFF3B3B);

    private final Map<Long, Integer> itemCounts = new HashMap<>();
    private List<Site> sites = List.of();
    private List<ChunkRating> ratings = List.of();
    private List<Cell> cells = List.of();
    private int itemTick;
    private boolean overworld = true;

    public BaseFinder() {
        super("Base Finder", "Finds stashes, bases and farms and guesses what they are.");

        add(showBudding);
        add(showSmall);
        add(showMedium);
        add(showLarge);
        add(showCluster);
        add(useContainers);
        add(useHoppers);
        add(useFurnaces);
        add(useRedstone);
        add(useWork);
        add(useBeds);
        add(useGlass);
        add(useTorches);
        add(useDamage);
        add(showBlocks);
        add(minScore);
        add(joinChunks);
        add(surfaceY);
        add(surfacePenalty);
        add(markerStyle);
        add(rateChunks);
        add(minRating, rateChunks::get);
        add(chunkLabels, rateChunks::get);
        add(maxChunkLabels, () -> rateChunks.get() && chunkLabels.get());
        add(raidSensitivity, useDamage::get);
        add(panel);
        add(panelEntries, panel::get);
        add(tooltip);
        add(labels);
        add(labelSize, labels::get);
        add(maxLabels, labels::get);

        addAppearanceSettings();

        add(buddingColor, () -> colorMode.get() == ColorMode.STATIC && showBudding.get());
        add(smallColor, () -> colorMode.get() == ColorMode.STATIC && showSmall.get());
        add(mediumColor, () -> colorMode.get() == ColorMode.STATIC && showMedium.get());
        add(largeColor, () -> colorMode.get() == ColorMode.STATIC && showLarge.get());
        add(clusterColor, () -> colorMode.get() == ColorMode.STATIC && showCluster.get());
        add(blockColor, () -> colorMode.get() == ColorMode.STATIC && showBlocks.get());
        add(rateLowColor, () -> colorMode.get() == ColorMode.STATIC && rateChunks.get());
        add(rateMidColor, () -> colorMode.get() == ColorMode.STATIC && rateChunks.get());
        add(rateHighColor, () -> colorMode.get() == ColorMode.STATIC && rateChunks.get());
        add(rateMaxColor, () -> colorMode.get() == ColorMode.STATIC && rateChunks.get());
        add(stashColor, () -> colorMode.get() == ColorMode.STATIC);
        add(baseTypeColor, () -> colorMode.get() == ColorMode.STATIC);
        add(farmColor, () -> colorMode.get() == ColorMode.STATIC);
        add(unknownColor, () -> colorMode.get() == ColorMode.STATIC);
        add(raidedColor, () -> colorMode.get() == ColorMode.STATIC && useDamage.get());

        addScanSettings();
        addBehaviorSettings();

        range.set(256.0);
        mode.set(EspMode.BOTH);
        fillOpacity.set(35.0);
        distanceFade.set(false);
        lineWidth.set(2.5);
    }

    // ---- accessors for the on-screen overlay ----

    public List<Site> sites() { return sites; }
    public List<Cell> cells() { return cells; }
    public boolean panelOn() { return panel.get(); }
    public int panelEntries() { return panelEntries.i(); }
    public boolean tooltipOn() { return tooltip.get(); }

    public int colorOf(Site s) {
        if (s.raided()) return raidedColor.get();
        return switch (s.type()) {
            case STASH -> stashColor.get();
            case BASE -> baseTypeColor.get();
            case FARM -> farmColor.get();
            case UNKNOWN -> unknownColor.get();
        };
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        itemCounts.clear();
        sites = List.of();
        ratings = List.of();
        cells = List.of();
    }

    // ---- scanning ----

    @Override
    protected int classify(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.BUDDING_AMETHYST) return showBudding.get() ? BUDDING : -1;
        if (b == Blocks.SMALL_AMETHYST_BUD) return showSmall.get() ? SMALL : -1;
        if (b == Blocks.MEDIUM_AMETHYST_BUD) return showMedium.get() ? MEDIUM : -1;
        if (b == Blocks.LARGE_AMETHYST_BUD) return showLarge.get() ? LARGE : -1;
        if (b == Blocks.AMETHYST_CLUSTER) return showCluster.get() ? CLUSTER : -1;

        int kind;
        if (b instanceof BedBlock) kind = BED;
        else if (b instanceof ChestBlock || b instanceof ShulkerBoxBlock) kind = CONTAINER;
        else {
            Integer k = KINDS.get(b);
            if (k == null) return -1;
            kind = k;
        }
        boolean on = switch (kind) {
            case CONTAINER -> useContainers.get();
            case HOPPER -> useHoppers.get();
            case FURNACE -> useFurnaces.get();
            case REDSTONE -> useRedstone.get();
            case WORK -> useWork.get();
            case BED -> useBeds.get();
            case GLASS -> useGlass.get();
            case TORCH -> useTorches.get();
            case TNT, FIRE -> useDamage.get();
            default -> false;
        };
        return on ? kind : -1;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    public void onTick(Minecraft mc) {
        super.onTick(mc);
        if (mc.level == null || mc.player == null) return;
        overworld = mc.level.dimension() == Level.OVERWORLD;
        if (!useDamage.get()) {
            itemCounts.clear();
            return;
        }
        if (itemTick++ % 10 != 0) return;
        double r = range.d() > 0 ? range.d() : 256.0;
        itemCounts.clear();
        AABB area = mc.player.getBoundingBox().inflate(r);
        for (Entity e : mc.level.getEntities(mc.player, area, x -> x instanceof ItemEntity)) {
            BlockPos p = e.blockPosition();
            itemCounts.merge(chunkKey(p.getX() >> 4, p.getZ() >> 4), 1, Integer::sum);
        }
    }

    // ---- grouping blocks into sites ----

    @Override
    protected List<Target> postProcess(List<Target> all) {
        // 1. tally signs per chunk
        Map<Long, Acc> chunks = new HashMap<>();
        for (Target t : all) {
            int k = t.kind();
            if (k < CONTAINER || k > FIRE) continue;
            BlockPos p = t.pos();
            int cx = p.getX() >> 4, cz = p.getZ() >> 4;
            Acc a = chunks.computeIfAbsent(chunkKey(cx, cz), key -> {
                Acc n = new Acc();
                n.cx = cx;
                n.cz = cz;
                return n;
            });
            a.counts[k]++;
            a.n++;
            a.sumX += p.getX();
            a.sumY += p.getY();
            a.sumZ += p.getZ();
            a.minY = Math.min(a.minY, p.getY());
            a.maxY = Math.max(a.maxY, p.getY());
            a.minX = Math.min(a.minX, p.getX());
            a.maxX = Math.max(a.maxX, p.getX());
            a.minZ = Math.min(a.minZ, p.getZ());
            a.maxZ = Math.max(a.maxZ, p.getZ());
            int section = Math.max(0, Math.min(31, (p.getY() >> 4) + 4));
            a.sectionMask |= 1 << section;
        }
        for (Map.Entry<Long, Acc> e : chunks.entrySet()) {
            Integer loose = itemCounts.get(e.getKey());
            if (loose != null) e.getValue().loose = loose;
        }

        // 2. merge touching chunks into groups
        List<Site> found = new ArrayList<>();
        List<Target> groupSlabs = new ArrayList<>();
        List<ChunkRating> groupRatings = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> siteChunks = new HashSet<>();
        for (Map.Entry<Long, Acc> start : chunks.entrySet()) {
            if (!seen.add(start.getKey())) continue;
            List<Acc> group = new ArrayList<>();
            ArrayDeque<Acc> queue = new ArrayDeque<>();
            queue.add(start.getValue());
            while (!queue.isEmpty()) {
                Acc a = queue.poll();
                group.add(a);
                int jr = joinChunks.i();
                for (int dx = -jr; dx <= jr; dx++) {
                    for (int dz = -jr; dz <= jr; dz++) {
                        long key = chunkKey(a.cx + dx, a.cz + dz);
                        Acc next = chunks.get(key);
                        if (next != null && seen.add(key)) queue.add(next);
                    }
                }
            }

            // totals for the whole connected group
            int[] tot = new int[15];
            double sumY = 0;
            long n = 0;
            int groupMinY = Integer.MAX_VALUE;
            int minCx = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE, minCz = Integer.MAX_VALUE, maxCz = Integer.MIN_VALUE;
            for (Acc a : group) {
                for (int i = 0; i < tot.length; i++) tot[i] += a.counts[i];
                sumY += a.sumY;
                n += a.n;
                groupMinY = Math.min(groupMinY, a.minY);
                minCx = Math.min(minCx, a.cx);
                maxCx = Math.max(maxCx, a.cx);
                minCz = Math.min(minCz, a.cz);
                maxCz = Math.max(maxCz, a.cz);
            }
            if (n == 0) continue;
            double avgY = sumY / n;
            double factor = surfaceFactor(avgY);

            Site site = evaluate(group, factor);
            if (site != null) {
                found.add(site);
                for (Acc a : group) siteChunks.add(chunkKey(a.cx, a.cz));
            }

            if (rateChunks.get()) {
                double act = activityOf(tot) * factor;
                int rating = (int) Math.round(100 * act / (act + 15.0));
                if (rating >= minRating.i()) {
                    // the plate sits just under the lowest detected block, so underground sites are drawn underground
                    AABB slab = new AABB(minCx * 16, groupMinY - 0.9, minCz * 16, (maxCx + 1) * 16, groupMinY - 0.6, (maxCz + 1) * 16);
                    groupSlabs.add(new Target(BlockPos.containing(minCx * 16, groupMinY, minCz * 16), slab, levelOf(rating)));
                    groupRatings.add(new ChunkRating(new Vec3((minCx + maxCx + 1) * 8.0, avgY + 1.5, (minCz + maxCz + 1) * 8.0),
                            rating, site != null));
                }
            }
        }
        sites = found;

        List<Cell> cellList = new ArrayList<>();
        for (Acc a : chunks.values()) {
            if (a.n == 0) continue;
            double act = activityOf(a.counts) * surfaceFactor(a.sumY / (double) a.n);
            int rating = (int) Math.round(100 * act / (act + 15.0));
            cellList.add(new Cell(a.cx, a.cz, rating, a.minY, a.maxY, siteChunks.contains(chunkKey(a.cx, a.cz))));
        }
        cells = cellList;

        // 3. what gets drawn: amethyst, optional single blocks, and one marker per site
        List<Target> out = new ArrayList<>();
        for (Target t : all) {
            boolean sign = t.kind() >= CONTAINER && t.kind() <= FIRE;
            if (sign && !showBlocks.get()) continue;
            out.add(t);
        }
        out.addAll(groupSlabs);
        ratings = groupRatings;
        for (Site s : found) {
            int kind = s.raided() ? S_RAIDED : switch (s.type()) {
                case STASH -> S_STASH;
                case BASE -> S_BASE;
                case FARM -> S_FARM;
                case UNKNOWN -> S_UNKNOWN;
            };
            Vec3 c = s.center();
            AABB box = markerStyle.get() == MarkerStyle.VOLUME
                    ? s.bounds()
                    : new AABB(c.x - 0.45, c.y - 0.45, c.z - 0.45, c.x + 0.45, c.y + 0.45, c.z + 0.45);
            out.add(new Target(BlockPos.containing(c), box, kind));
        }
        return out;
    }

    private Site evaluate(List<Acc> group, double factor) {
        int[] c = new int[15];
        long sumX = 0, sumY = 0, sumZ = 0, n = 0;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, mask = 0, loose = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Acc a : group) {
            for (int i = 0; i < c.length; i++) c[i] += a.counts[i];
            sumX += a.sumX;
            sumY += a.sumY;
            sumZ += a.sumZ;
            n += a.n;
            minY = Math.min(minY, a.minY);
            maxY = Math.max(maxY, a.maxY);
            minX = Math.min(minX, a.minX);
            maxX = Math.max(maxX, a.maxX);
            minZ = Math.min(minZ, a.minZ);
            maxZ = Math.max(maxZ, a.maxZ);
            mask |= a.sectionMask;
            loose += a.loose;
        }
        if (n == 0) return null;

        int containers = c[CONTAINER], hoppers = c[HOPPER], furnaces = c[FURNACE], redstone = c[REDSTONE];
        int work = c[WORK], beds = c[BED], glass = c[GLASS], torches = c[TORCH];

        double activity = 2.0 * containers + 1.5 * hoppers + 1.5 * furnaces + redstone + 2.0 * work
                + 3.0 * beds + 0.3 * glass + 0.3 * torches;
        activity *= factor; // near the surface counts for less
        if (activity < minScore.d()) return null;

        double avgY = sumY / (double) n;
        double stash = Math.max(0, 2.5 * containers + hoppers + (avgY < 30 ? 6 : 0) - 3.0 * beds - 1.5 * work - furnaces);
        double base = 4.0 * beds + 2.0 * work + 1.5 * furnaces + 0.5 * containers + 0.3 * glass + 0.3 * torches;
        double farm = (hoppers + redstone >= 6) ? Math.max(0, 2.0 * hoppers + 1.5 * redstone - 0.5 * containers) : 0;

        double[] scores = {stash * factor, base * factor, farm * factor};
        int bestIdx = 0;
        for (int i = 1; i < scores.length; i++) if (scores[i] > scores[bestIdx]) bestIdx = i;
        double s1 = scores[bestIdx], s2 = 0;
        for (int i = 0; i < scores.length; i++) if (i != bestIdx) s2 = Math.max(s2, scores[i]);

        Type type;
        int conf;
        if (s1 < 4) {
            type = Type.UNKNOWN;
            conf = (int) Math.max(10, Math.min(50, Math.round(100 * activity / (activity + 30))));
        } else {
            type = bestIdx == 0 ? Type.STASH : bestIdx == 1 ? Type.BASE : Type.FARM;
            conf = (int) Math.min(95, Math.round(100 * s1 / (s1 + s2 + 6)));
            if (conf < 40) type = Type.UNKNOWN;
        }

        // Loose items are everywhere (mob drops, dropped stacks), so they can only add a little on their own.
        double damage = 3.0 * c[TNT] + 2.0 * c[FIRE] + Math.min(4.0, 0.15 * loose);
        boolean raided = useDamage.get() && damage >= raidSensitivity.d();

        List<String> counts = new ArrayList<>();
        addCount(counts, containers, "container", "containers");
        addCount(counts, hoppers, "hopper", "hoppers");
        addCount(counts, furnaces, "furnace", "furnaces");
        addCount(counts, beds, "bed", "beds");
        addCount(counts, work, "workstation", "workstations");
        addCount(counts, redstone, "redstone part", "redstone parts");
        String countText = counts.isEmpty() ? "no clear signs" : String.join(", ", counts.subList(0, Math.min(3, counts.size())));

        List<String> evidence = new ArrayList<>();
        switch (type) {
            case STASH -> evidence.add(avgY < 30 ? "Lots of storage, deep underground" : "Lots of storage, little living space");
            case BASE -> evidence.add("Beds and workstations: lived in");
            case FARM -> evidence.add("Hoppers and redstone: likely a farm");
            case UNKNOWN -> evidence.add("Not enough clear evidence");
        }
        if (factor < 1.0) evidence.add("Near the surface: probably not a base");
        if (raided) evidence.add("Damage signs nearby: may be raided");

        Vec3 center = new Vec3(sumX / (double) n + 0.5, sumY / (double) n + 0.5, sumZ / (double) n + 0.5);
        // The box hugs the detected blocks at their real depth, padded a little and at least 3 blocks tall.
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(1.0, 0.5, 1.0);
        return new Site(type, raided, conf, (int) Math.round(activity), center, bounds, minY, maxY,
                group.size(), Integer.bitCount(mask), countText, evidence);
    }

    /** 1.0 normally; smaller for sites near the overworld surface, which are less likely to be bases. */
    private double surfaceFactor(double avgY) {
        if (!overworld || avgY < surfaceY.d()) return 1.0;
        return Math.max(0.0, 1.0 - surfacePenalty.d() / 100.0);
    }

    /** How much player activity one tally of blocks suggests. */
    private static double activityOf(int[] c) {
        return 2.0 * c[CONTAINER] + 1.5 * c[HOPPER] + 1.5 * c[FURNACE] + c[REDSTONE] + 2.0 * c[WORK]
                + 3.0 * c[BED] + 0.3 * c[GLASS] + 0.3 * c[TORCH];
    }

    private static int levelOf(int rating) {
        if (rating >= 80) return R_MAX;
        if (rating >= 60) return R_HIGH;
        if (rating >= 40) return R_MID;
        return R_LOW;
    }

    private int rateColor(int level) {
        return switch (level) {
            case R_MAX -> rateMaxColor.get();
            case R_HIGH -> rateHighColor.get();
            case R_MID -> rateMidColor.get();
            default -> rateLowColor.get();
        };
    }

    private static void addCount(List<String> out, int n, String one, String many) {
        if (n > 0) out.add(n + " " + (n == 1 ? one : many));
    }

    // ---- world labels ----

    @Override
    protected void renderExtra(Minecraft mc, Vec3 cam) {
        if (rateChunks.get() && chunkLabels.get() && !ratings.isEmpty()) {
            List<ChunkRating> rs = new ArrayList<>(ratings);
            rs.removeIf(ChunkRating::isSite);
            rs.sort(Comparator.comparingDouble(r -> r.pos().distanceToSqr(cam)));
            int cn = Math.min(rs.size(), maxChunkLabels.i());
            for (int i = 0; i < cn; i++) {
                ChunkRating r = rs.get(i);
                if (r.isSite()) continue; // sites already have their own label
                double d = r.pos().distanceTo(cam);
                float sc = labelSize.f() * (float) Math.max(1.0, d / 8.0);
                EspDraw.text(r.pos(), "Base potential " + r.rating() + "%", rateColor(levelOf(r.rating())), sc, throughWalls.get());
            }
        }
        if (!labels.get() || sites.isEmpty()) return;
        List<Site> sorted = new ArrayList<>(sites);
        sorted.sort(Comparator.comparingDouble(s -> s.center().distanceToSqr(cam)));
        int n = Math.min(sorted.size(), maxLabels.i());
        for (int i = 0; i < n; i++) {
            Site s = sorted.get(i);
            double dist = s.center().distanceTo(cam);
            float scale = labelSize.f() * (float) Math.max(1.0, dist / 8.0);
            String text = s.label() + " " + s.conf() + "% " + Math.round(dist) + "m";
            EspDraw.text(new Vec3(s.center().x, s.bounds().maxY + 1.0, s.center().z), text, colorOf(s), scale, throughWalls.get());
        }
    }

    @Override
    protected int baseColor(int kind) {
        if (colorMode.get() != ColorMode.STATIC && (kind < S_STASH || kind >= R_LOW)) return blockColor.get();
        return switch (kind) {
            case BUDDING -> buddingColor.get();
            case SMALL -> smallColor.get();
            case MEDIUM -> mediumColor.get();
            case LARGE -> largeColor.get();
            case CLUSTER -> clusterColor.get();
            case R_LOW, R_MID, R_HIGH, R_MAX -> rateColor(kind);
            case S_STASH -> stashColor.get();
            case S_BASE -> baseTypeColor.get();
            case S_FARM -> farmColor.get();
            case S_UNKNOWN -> unknownColor.get();
            case S_RAIDED, TNT, FIRE -> raidedColor.get();
            default -> blockColor.get();
        };
    }
}
