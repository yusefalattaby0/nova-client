package dev.nova.module.render;

import dev.nova.render.EspDraw;
import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks for signs of player activity and flags suspicious chunks.
 *
 * Every player-made block adds to a chunk's "base score". A chunk over the threshold is highlighted:
 *   orange = suspicious, yellow = active base (score at least double the threshold).
 * Damage signs (TNT, fire, loose dropped items) are counted too. A base with enough damage signs is
 * marked red as "likely raided".
 *
 * The client can't see inside chests or know history, so this is a guess from what is visible.
 * Amethyst buds are shown per growth stage: they only grow while their chunk is loaded.
 */
public class BaseFinder extends BlockEspModule {
    // block kinds
    private static final int BUDDING = 0, SMALL = 1, MEDIUM = 2, LARGE = 3, CLUSTER = 4;
    private static final int REDSTONE = 5, WORK = 6, BED = 7, GLASS = 8, TORCH = 9, STORAGE = 10, TNT = 11, FIRE = 12;
    // chunk kinds
    private static final int C_SUSPICIOUS = 13, C_BASE = 14, C_RAIDED = 15;

    private static final double[] WEIGHT = new double[16];

    static {
        WEIGHT[REDSTONE] = 1.0;
        WEIGHT[WORK] = 2.0;
        WEIGHT[BED] = 3.0;
        WEIGHT[GLASS] = 0.3;
        WEIGHT[TORCH] = 0.3;
        WEIGHT[STORAGE] = 2.0;
    }

    private static final Map<Block, Integer> KINDS = new HashMap<>();

    static {
        put(REDSTONE, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.OBSERVER, Blocks.REPEATER, Blocks.COMPARATOR,
                Blocks.REDSTONE_LAMP, Blocks.NOTE_BLOCK, Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER);
        put(WORK, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.ANVIL,
                Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.BEACON, Blocks.JUKEBOX);
        put(GLASS, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS);
        put(TORCH, Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
                Blocks.LANTERN, Blocks.SOUL_LANTERN);
        put(STORAGE, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.TRAPPED_CHEST);
        put(TNT, Blocks.TNT);
        put(FIRE, Blocks.FIRE, Blocks.SOUL_FIRE);
    }

    private static void put(int kind, Block... blocks) {
        for (Block b : blocks) KINDS.put(b, kind);
    }

    private record Label(Vec3 pos, String text, int rgb) {}

    private static final class Info {
        double base;
        double damage;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int cx, cz;
    }

    // amethyst
    private final Setting.Bool showBudding = new Setting.Bool("Budding Amethyst", true);
    private final Setting.Bool showSmall = new Setting.Bool("Small Bud (stage 1)", true);
    private final Setting.Bool showMedium = new Setting.Bool("Medium Bud (stage 2)", true);
    private final Setting.Bool showLarge = new Setting.Bool("Large Bud (stage 3)", true);
    private final Setting.Bool showCluster = new Setting.Bool("Full Cluster (grown)", true);

    // player-made blocks
    private final Setting.Bool useRedstone = new Setting.Bool("Redstone & Pistons", true);
    private final Setting.Bool useWork = new Setting.Bool("Workstations", true);
    private final Setting.Bool useBeds = new Setting.Bool("Beds", true);
    private final Setting.Bool useStorage = new Setting.Bool("Chests & Barrels", true);
    private final Setting.Bool useGlass = new Setting.Bool("Glass", false);
    private final Setting.Bool useTorches = new Setting.Bool("Torches & Lanterns", false);
    private final Setting.Bool useDamage = new Setting.Bool("Detect Raid Damage", true);
    private final Setting.Bool showBlocks = new Setting.Bool("Show Individual Blocks", false);

    // chunk logic
    private final Setting.Bool chunkBoxes = new Setting.Bool("Highlight Suspicious Chunks", true);
    private final Setting.Num threshold = new Setting.Num("Suspicion Threshold", 6, 1, 60, 1, " pts");
    private final Setting.Num raidSensitivity = new Setting.Num("Raid Sensitivity (lower = more)", 5, 1, 30, 1, " pts");
    private final Setting.Bool labels = new Setting.Bool("Show Labels", true);
    private final Setting.Num labelSize = new Setting.Num("Label Size", 1.5, 0.5, 8, 0.5, "x");
    private final Setting.Num maxLabels = new Setting.Num("Max Labels", 12, 1, 60, 1, "");

    // colors
    private final Setting.Col buddingColor = new Setting.Col("Budding Color", 0x9D4EDD);
    private final Setting.Col smallColor = new Setting.Col("Small Bud Color", 0x6E8BFF);
    private final Setting.Col mediumColor = new Setting.Col("Medium Bud Color", 0xA06BFF);
    private final Setting.Col largeColor = new Setting.Col("Large Bud Color", 0xD36BFF);
    private final Setting.Col clusterColor = new Setting.Col("Cluster Color", 0xFF6BE6);
    private final Setting.Col blockColor = new Setting.Col("Player Block Color", 0xFFD83B);
    private final Setting.Col suspiciousColor = new Setting.Col("Suspicious Chunk Color", 0xFF9A3B);
    private final Setting.Col baseColorSetting = new Setting.Col("Active Base Color", 0xFFE74D);
    private final Setting.Col raidedColor = new Setting.Col("Likely Raided Color", 0xFF3B3B);

    private final Map<Long, Integer> itemCounts = new HashMap<>();
    private List<Label> labelList = List.of();
    private int itemTick;

    public BaseFinder() {
        super("Base Finder", "Flags suspicious chunks and guesses if they were raided. Shows amethyst growth.");

        add(showBudding);
        add(showSmall);
        add(showMedium);
        add(showLarge);
        add(showCluster);
        add(useRedstone);
        add(useWork);
        add(useBeds);
        add(useStorage);
        add(useGlass);
        add(useTorches);
        add(useDamage);
        add(showBlocks);
        add(chunkBoxes);
        add(threshold, chunkBoxes::get);
        add(raidSensitivity, () -> chunkBoxes.get() && useDamage.get());
        add(labels, chunkBoxes::get);
        add(labelSize, () -> chunkBoxes.get() && labels.get());
        add(maxLabels, () -> chunkBoxes.get() && labels.get());

        addAppearanceSettings();

        add(buddingColor, () -> colorMode.get() == ColorMode.STATIC && showBudding.get());
        add(smallColor, () -> colorMode.get() == ColorMode.STATIC && showSmall.get());
        add(mediumColor, () -> colorMode.get() == ColorMode.STATIC && showMedium.get());
        add(largeColor, () -> colorMode.get() == ColorMode.STATIC && showLarge.get());
        add(clusterColor, () -> colorMode.get() == ColorMode.STATIC && showCluster.get());
        add(blockColor, () -> colorMode.get() == ColorMode.STATIC && showBlocks.get());
        add(suspiciousColor, () -> colorMode.get() == ColorMode.STATIC && chunkBoxes.get());
        add(baseColorSetting, () -> colorMode.get() == ColorMode.STATIC && chunkBoxes.get());
        add(raidedColor, () -> colorMode.get() == ColorMode.STATIC && chunkBoxes.get());

        addScanSettings();
        addBehaviorSettings();

        range.set(256.0);
        mode.set(EspMode.OUTLINE);
        distanceFade.set(false);
        lineWidth.set(3.0);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        itemCounts.clear();
        labelList = List.of();
    }

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
        else if (b instanceof ChestBlock || b instanceof ShulkerBoxBlock) kind = STORAGE;
        else {
            Integer k = KINDS.get(b);
            if (k == null) return -1;
            kind = k;
        }
        boolean on = switch (kind) {
            case REDSTONE -> useRedstone.get();
            case WORK -> useWork.get();
            case BED -> useBeds.get();
            case STORAGE -> useStorage.get();
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
        if (!useDamage.get() || !chunkBoxes.get()) {
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

    @Override
    protected List<Target> postProcess(List<Target> all) {
        Map<Long, Info> chunks = new HashMap<>();
        for (Target t : all) {
            int k = t.kind();
            if (k < REDSTONE || k > FIRE) continue;
            BlockPos p = t.pos();
            int cx = p.getX() >> 4, cz = p.getZ() >> 4;
            Info info = chunks.computeIfAbsent(chunkKey(cx, cz), key -> {
                Info i = new Info();
                i.cx = cx;
                i.cz = cz;
                return i;
            });
            if (k == TNT) info.damage += 3;
            else if (k == FIRE) info.damage += 2;
            else info.base += WEIGHT[k];
            info.minY = Math.min(info.minY, p.getY());
            info.maxY = Math.max(info.maxY, p.getY());
        }

        List<Target> out = new ArrayList<>();
        for (Target t : all) {
            boolean madeBlock = t.kind() >= REDSTONE && t.kind() <= FIRE;
            if (madeBlock && !showBlocks.get()) continue;
            out.add(t);
        }

        List<Label> newLabels = new ArrayList<>();
        if (chunkBoxes.get()) {
            for (Map.Entry<Long, Info> en : chunks.entrySet()) {
                Info i = en.getValue();
                Integer loose = itemCounts.get(en.getKey());
                if (loose != null) i.damage += loose;
                if (i.base < threshold.d()) continue;

                int kind;
                String text;
                int score = (int) Math.round(i.base);
                int dmg = (int) Math.round(i.damage);
                if (useDamage.get() && i.damage >= raidSensitivity.d()) {
                    kind = C_RAIDED;
                    text = "LIKELY RAIDED (base " + score + ", damage " + dmg + ")";
                } else if (i.base >= threshold.d() * 2) {
                    kind = C_BASE;
                    text = "Active base, looks intact (" + score + ")";
                } else {
                    kind = C_SUSPICIOUS;
                    text = "Suspicious (" + score + ")";
                }

                int x0 = i.cx * 16, z0 = i.cz * 16;
                AABB box = new AABB(x0, i.minY, z0, x0 + 16, i.maxY + 1, z0 + 16);
                out.add(new Target(new BlockPos(x0, i.minY, z0), box, kind));
                newLabels.add(new Label(new Vec3(x0 + 8, i.maxY + 4, z0 + 8), text, baseColor(kind)));
            }
        }
        labelList = newLabels;
        return out;
    }

    @Override
    protected void renderExtra(Minecraft mc, Vec3 cam) {
        if (!chunkBoxes.get() || !labels.get() || labelList.isEmpty()) return;
        List<Label> sorted = new ArrayList<>(labelList);
        sorted.sort(Comparator.comparingDouble(l -> l.pos().distanceToSqr(cam)));
        int n = Math.min(sorted.size(), maxLabels.i());
        for (int i = 0; i < n; i++) {
            Label l = sorted.get(i);
            double dist = l.pos().distanceTo(cam);
            float scale = labelSize.f() * (float) Math.max(1.0, dist / 8.0);
            EspDraw.text(l.pos(), l.text(), l.rgb(), scale, throughWalls.get());
        }
    }

    @Override
    protected int baseColor(int kind) {
        if (colorMode.get() != ColorMode.STATIC && kind < C_SUSPICIOUS) return blockColor.get();
        return switch (kind) {
            case BUDDING -> buddingColor.get();
            case SMALL -> smallColor.get();
            case MEDIUM -> mediumColor.get();
            case LARGE -> largeColor.get();
            case CLUSTER -> clusterColor.get();
            case C_SUSPICIOUS -> suspiciousColor.get();
            case C_BASE -> baseColorSetting.get();
            case C_RAIDED, TNT, FIRE -> raidedColor.get();
            default -> blockColor.get();
        };
    }
}
