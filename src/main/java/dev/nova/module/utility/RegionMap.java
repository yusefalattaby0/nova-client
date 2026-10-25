package dev.nova.module.utility;

import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.setting.Setting;

/** Pings a few world regions and shows which one is closest to you. Open it with its key. */
public class RegionMap extends Module {
    private final Setting.Num refresh = add(new Setting.Num("Refresh Every", 5, 2, 30, 1, " s"));
    private final Setting.Num good = add(new Setting.Num("Good Ping Below", 60, 10, 200, 5, " ms"));
    private final Setting.Num bad = add(new Setting.Num("Bad Ping Above", 150, 50, 400, 10, " ms"));

    public RegionMap() {
        super("Region Map", "Pings world regions so you can pick the server with the best ping.", Category.UTILITY);
    }

    public static RegionMap get() { return ModuleManager.get(RegionMap.class); }

    public int refreshMillis() { return refresh.i() * 1000; }
    public int goodBelow() { return good.i(); }
    public int badAbove() { return bad.i(); }
}
