package dev.nova.module.utility;

import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.setting.Setting;
import dev.nova.util.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Random teleports on autopilot. Sends /rtp, waits for the teleport, lets the chunks load, then checks the spot:
 *  - too close to where you started  -> TOO CLOSE
 *  - in water (optional)             -> WATER
 *  - player-made blocks nearby       -> CLAIMED (a guess from what your client can see)
 * and tries again until a spot passes.
 */
public class AutoRtp extends Module {
    private final Setting.Num minDistance = add(new Setting.Num("Min Distance", 10000, 0, 100000, 500, " blocks"));
    private final Setting.Bool avoidWater = add(new Setting.Bool("Avoid Water", true));
    private final Setting.Bool skipClaimed = add(new Setting.Bool("Skip Claimed Land", true));
    private final Setting.Num claimRadius = add(new Setting.Num("Claim Check Radius", 3, 1, 8, 1, " chunks"), skipClaimed::get);
    private final Setting.Num settle = add(new Setting.Num("Settle Time", 60, 20, 200, 5, " ticks"));
    private final Setting.Num retryDelay = add(new Setting.Num("Retry Delay", 40, 10, 400, 10, " ticks"));
    private final Setting.Num maxAttempts = add(new Setting.Num("Max Attempts (0 = unlimited)", 0, 0, 500, 5, ""));
    private final Setting.Bool showHud = add(new Setting.Bool("Show Panel", true));
    private final Setting.Bool showCoords = add(new Setting.Bool("Show Coordinates Box", true));

    public enum State { SEND, WAIT_TP, SETTLE, COOLDOWN, DONE, STOPPED }

    /** One line of the on-screen log. */
    public record Line(String text, String status, int rgb) {}

    private static final Set<Block> PLAYER_BLOCKS = Set.of(
            Blocks.BARREL, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.HOPPER, Blocks.FURNACE,
            Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.BEACON,
            Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.JUKEBOX);

    private State state = State.STOPPED;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 sentFrom = Vec3.ZERO;
    private int attempt;
    private int timer;
    private final ArrayDeque<Line> log = new ArrayDeque<>();

    public AutoRtp() {
        super("Auto RTP", "Random teleports on autopilot until it lands somewhere good.", Category.UTILITY);
    }

    public static AutoRtp get() { return ModuleManager.get(AutoRtp.class); }

    // ---- readouts for the HUD ----
    public State state() { return state; }
    public int attempt() { return attempt; }
    public List<Line> lines() { return new ArrayList<>(log); }
    public boolean panelOn() { return showHud.get(); }
    public boolean coordsOn() { return showCoords.get(); }
    public int minDistanceBlocks() { return minDistance.i(); }
    public boolean avoidsWater() { return avoidWater.get(); }

    private void say(String status, int rgb) {
        log.addLast(new Line("[Auto RTP] /rtp", status, rgb));
        while (log.size() > 4) log.pollFirst();
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        origin = mc.player.position();
        attempt = 0;
        timer = 0;
        log.clear();
        state = State.SEND;
    }

    @Override
    protected void onDisable() {
        state = State.STOPPED;
    }

    @Override
    public void onTick(Minecraft mc) {
        Player p = mc.player;
        if (p == null || mc.level == null) return;

        switch (state) {
            case SEND -> {
                if (maxAttempts.i() > 0 && attempt >= maxAttempts.i()) {
                    say("GAVE UP", 0xFF8A8A);
                    state = State.DONE;
                    return;
                }
                attempt++;
                sentFrom = p.position();
                if (!Compat.command(mc, "rtp")) {
                    say("COULD NOT SEND", 0xFF5555);
                    state = State.DONE;
                    return;
                }
                timer = 0;
                state = State.WAIT_TP;
            }
            case WAIT_TP -> {
                timer++;
                Vec3 now = p.position();
                double dx = now.x - sentFrom.x, dz = now.z - sentFrom.z;
                if (dx * dx + dz * dz > 24 * 24) {
                    timer = 0;
                    state = State.SETTLE;
                } else if (timer > 200) {
                    say("NO TELEPORT - RETRY", 0xFFB347);
                    timer = 0;
                    state = State.COOLDOWN;
                }
            }
            case SETTLE -> {
                timer++;
                if (timer >= settle.i()) evaluate(mc, p);
            }
            case COOLDOWN -> {
                timer++;
                if (timer >= retryDelay.i()) state = State.SEND;
            }
            default -> {}
        }
    }

    private void evaluate(Minecraft mc, Player p) {
        Vec3 now = p.position();
        double dx = now.x - origin.x, dz = now.z - origin.z;
        if (Math.sqrt(dx * dx + dz * dz) < minDistance.d()) {
            fail("TOO CLOSE - RETRY");
            return;
        }
        if (avoidWater.get() && inWater(mc, p)) {
            fail("WATER - RETRY");
            return;
        }
        if (skipClaimed.get() && looksClaimed(mc, p)) {
            fail("CLAIMED - RETRY");
            return;
        }
        say("FOUND LAND", 0x6BCB77);
        state = State.DONE;
    }

    private void fail(String status) {
        say(status, status.startsWith("CLAIMED") ? 0xFFB347 : 0xFF8A5C);
        timer = 0;
        state = State.COOLDOWN;
    }

    private static boolean inWater(Minecraft mc, Player p) {
        BlockPos feet = p.blockPosition();
        return p.isInWater()
                || mc.level.getBlockState(feet.below()).is(Blocks.WATER)
                || mc.level.getBlockState(feet.below(2)).is(Blocks.WATER);
    }

    private boolean looksClaimed(Minecraft mc, Player p) {
        BlockPos pos = p.blockPosition();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        int r = claimRadius.i();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkAccess access = mc.level.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    Block b = be.getBlockState().getBlock();
                    if (b instanceof BedBlock || b instanceof ChestBlock || b instanceof ShulkerBoxBlock
                            || PLAYER_BLOCKS.contains(b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
