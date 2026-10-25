package dev.nova.module.utility;

import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.module.render.EntityEsp;
import dev.nova.module.render.Trails;
import dev.nova.setting.Setting;
import dev.nova.util.Compat;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.List;

/**
 * A radial menu: hold the key, move the mouse toward a slice, let go to run it.
 * Slices are commands (/home, /sell, /rtp, /ah, /spawn), toggles (ESP, Trails) and a macro (Kit PvP).
 */
public class DonutWheel extends Module {
    private final Setting.Num size = add(new Setting.Num("Wheel Size", 120, 80, 180, 5, " px"));
    private final Setting.Bool closeOnClick = add(new Setting.Bool("Click Also Runs", true));

    public enum Kind {
        COMMAND("COMMAND", "RELEASE TO RUN"),
        TOGGLE("TOGGLE", "RELEASE TO TOGGLE"),
        MACRO("MACRO", "RELEASE TO RUN");

        public final String tag, hint;
        Kind(String tag, String hint) { this.tag = tag; this.hint = hint; }
    }

    /** One slice. {@code arg} is the command (no slash) for commands and macros. */
    public record Slice(String label, Kind kind, String arg, String icon) {}

    /** Clockwise from the right, matching the layout of the wheel. */
    public static final List<Slice> SLICES = List.of(
            new Slice("/rtp", Kind.COMMAND, "rtp", "dice"),
            new Slice("/ah", Kind.COMMAND, "ah", "tag"),
            new Slice("Kit PvP", Kind.MACRO, "kit pvp", "box"),
            new Slice("ESP", Kind.TOGGLE, "esp", "eye"),
            new Slice("Trails", Kind.TOGGLE, "trails", "trail"),
            new Slice("/spawn", Kind.COMMAND, "spawn", "pin"),
            new Slice("/home", Kind.COMMAND, "home", "home"),
            new Slice("/sell", Kind.COMMAND, "sell", "dollar"));

    private final ArrayDeque<String> macroQueue = new ArrayDeque<>();
    private int macroWait;

    public DonutWheel() {
        super("Donut Wheel", "Hold a key for a radial menu of server commands and toggles.", Category.UTILITY);
    }

    public static DonutWheel get() { return ModuleManager.get(DonutWheel.class); }

    public int radius() { return size.i(); }
    public boolean clickRuns() { return closeOnClick.get(); }

    public static boolean isOn(Slice s) {
        if (s.kind() != Kind.TOGGLE) return false;
        return switch (s.arg()) {
            case "esp" -> {
                EntityEsp e = ModuleManager.get(EntityEsp.class);
                yield e != null && e.enabled();
            }
            case "trails" -> {
                Trails t = Trails.get();
                yield t != null && t.enabled();
            }
            default -> false;
        };
    }

    /** Runs a slice. Call from the client thread. */
    public void run(Slice s) {
        Minecraft mc = Minecraft.getInstance();
        switch (s.kind()) {
            case COMMAND -> Compat.command(mc, s.arg());
            case MACRO -> {
                // a macro is a list of commands sent a few ticks apart; this one is a single kit command
                macroQueue.clear();
                for (String step : s.arg().split(";")) macroQueue.add(step.trim());
                macroWait = 0;
            }
            case TOGGLE -> {
                switch (s.arg()) {
                    case "esp" -> {
                        EntityEsp e = ModuleManager.get(EntityEsp.class);
                        if (e != null) e.toggle();
                    }
                    case "trails" -> {
                        Trails t = Trails.get();
                        if (t != null) t.toggle();
                    }
                    default -> {}
                }
            }
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (macroQueue.isEmpty()) return;
        if (macroWait > 0) { macroWait--; return; }
        String cmd = macroQueue.poll();
        if (cmd != null && !cmd.isEmpty()) Compat.command(mc, cmd);
        macroWait = 6;
    }
}
