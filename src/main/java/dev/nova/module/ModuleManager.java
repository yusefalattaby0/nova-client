package dev.nova.module;

import dev.nova.module.client.GuiSettings;
import dev.nova.module.combat.AutoTotem;
import dev.nova.module.combat.FastCrystal;
import dev.nova.module.render.AncientDebrisFinder;
import dev.nova.module.render.BaseFinder;
import dev.nova.module.render.EntityEsp;
import dev.nova.module.render.EspModule;
import dev.nova.module.render.Freecam;
import dev.nova.module.render.OreFinder;
import dev.nova.module.render.RespawnAnchorFinder;
import dev.nova.module.render.SpawnerFinder;
import dev.nova.module.render.StorageFinder;
import dev.nova.module.render.Trails;
import dev.nova.module.utility.AutoRtp;
import dev.nova.module.utility.DonutWheel;
import dev.nova.module.utility.InfoHud;
import dev.nova.module.utility.RegionMap;
import dev.nova.util.Config;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();

    private ModuleManager() {}

    public static void init() {
        register(new StorageFinder());
        register(new EntityEsp());
        register(new BaseFinder());
        register(new AncientDebrisFinder());
        register(new SpawnerFinder());
        register(new OreFinder());
        register(new RespawnAnchorFinder());
        register(new AutoTotem());
        register(new FastCrystal());
        register(new Freecam());
        register(new Trails());
        register(new AutoRtp());
        register(new DonutWheel());
        register(new RegionMap());
        register(new InfoHud());
        register(new GuiSettings());
        // On by default: their keys only work while the module is on. A saved config can still turn them off.
        get(DonutWheel.class).setEnabled(true);
        get(RegionMap.class).setEnabled(true);
        Config.load();
    }

    private static void register(Module m) {
        MODULES.add(m);
        if (!m.toggleable()) m.setEnabled(true);
    }

    public static List<Module> all() { return MODULES; }

    public static List<Module> in(Module.Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.category == category) out.add(m);
        return out;
    }

    public static <T extends Module> T get(Class<T> type) {
        for (Module m : MODULES) if (type.isInstance(m)) return type.cast(m);
        return null;
    }

    public static void tick(Minecraft mc) {
        for (Module m : MODULES) if (m.enabled()) m.onTick(mc);
    }

    public static void preTick(Minecraft mc) {
        for (Module m : new ArrayList<>(MODULES)) if (m.enabled()) m.onPreTick(mc);
    }

    public static void render(Minecraft mc) {
        for (Module m : new ArrayList<>(MODULES)) {
            if (!m.enabled()) continue;
            m.onFrame(mc);
            if (m instanceof EspModule esp) esp.render(mc);
        }
    }
}
