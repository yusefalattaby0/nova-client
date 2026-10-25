package dev.nova.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.nova.NovaClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Measures the connection time to a handful of world regions and can connect to a server you set per region.
 *
 * The ping is the time to open a connection to a public cloud endpoint in each region, which is a good
 * stand-in for how far away that part of the world is from you. Server addresses live in
 * config/nova-regions.json (blank until you fill them in).
 */
public final class RegionPing {
    private RegionPing() {}

    public static final class Region {
        public final String name, shortName, host;
        public final double lon, lat;
        public volatile int ping = -1;   // ms, -1 = not measured yet
        public volatile String address = "";

        Region(String name, String shortName, String host, double lon, double lat) {
            this.name = name;
            this.shortName = shortName;
            this.host = host;
            this.lon = lon;
            this.lat = lat;
        }
    }

    public static final List<Region> REGIONS = List.of(
            new Region("EU CENTRAL", "EU", "ec2.eu-central-1.amazonaws.com", 8.7, 50.1),
            new Region("NA EAST", "NA EAST", "ec2.us-east-1.amazonaws.com", -77.4, 38.9),
            new Region("NA WEST", "NA WEST", "ec2.us-west-1.amazonaws.com", -122.0, 37.4),
            new Region("ASIA", "ASIA", "ec2.ap-southeast-1.amazonaws.com", 103.8, 1.3),
            new Region("OCEANIA", "OCEANIA", "ec2.ap-southeast-2.amazonaws.com", 151.2, -33.9));

    private static final ExecutorService POOL = Executors.newFixedThreadPool(REGIONS.size(), r -> {
        Thread t = new Thread(r, "nova-region-ping");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean busy;
    private static volatile long lastRefresh;
    private static boolean loaded;

    /** Starts a new round of pings unless one is already running. */
    public static void refresh() {
        loadAddresses();
        if (busy) return;
        busy = true;
        lastRefresh = System.currentTimeMillis();
        final int[] left = {REGIONS.size()};
        for (Region r : REGIONS) {
            POOL.execute(() -> {
                r.ping = measure(r.host);
                synchronized (left) {
                    if (--left[0] == 0) busy = false;
                }
            });
        }
    }

    public static long millisSinceRefresh() { return System.currentTimeMillis() - lastRefresh; }

    private static int measure(String host) {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, 443), 2500);
                best = Math.min(best, (int) ((System.nanoTime() - t0) / 1_000_000L));
            } catch (IOException e) {
                // unreachable this time
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    /** Regions with a result, fastest first; unmeasured regions go last. */
    public static List<Region> ranked() {
        List<Region> out = new ArrayList<>(REGIONS);
        out.sort(Comparator.comparingInt(r -> r.ping < 0 ? Integer.MAX_VALUE : r.ping));
        return out;
    }

    public static Region best() {
        Region b = ranked().get(0);
        return b.ping < 0 ? null : b;
    }

    // ---- server addresses ----

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nova-regions.json");
    }

    private static synchronized void loadAddresses() {
        if (loaded) return;
        loaded = true;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path f = file();
        JsonObject root = new JsonObject();
        try {
            if (Files.exists(f)) {
                JsonObject read = gson.fromJson(Files.readString(f), JsonObject.class);
                if (read != null) root = read;
            }
        } catch (IOException | RuntimeException e) {
            NovaClient.LOGGER.warn("Could not read nova-regions.json", e);
        }
        boolean changed = false;
        for (Region r : REGIONS) {
            if (root.has(r.name)) {
                r.address = root.get(r.name).getAsString();
            } else {
                root.addProperty(r.name, "");
                changed = true;
            }
        }
        if (changed) {
            try {
                Files.writeString(f, gson.toJson(root));
            } catch (IOException e) {
                NovaClient.LOGGER.warn("Could not write nova-regions.json", e);
            }
        }
    }

    /**
     * Connects to the region's server. Returns a short message for the screen.
     * The connect call is made by name because its parameters change between versions.
     */
    public static String connect(Region r) {
        loadAddresses();
        String addr = r.address == null ? "" : r.address.trim();
        if (addr.isEmpty()) return "Put a server address for " + r.name + " in config/nova-regions.json";
        Minecraft mc = Minecraft.getInstance();
        try {
            Class<?> addrCls = Class.forName("net.minecraft.client.multiplayer.resolver.ServerAddress");
            Class<?> dataCls = Class.forName("net.minecraft.client.multiplayer.ServerData");
            Class<?> connCls = Class.forName("net.minecraft.client.gui.screens.ConnectScreen");
            Object serverAddress = addrCls.getMethod("parseString", String.class).invoke(null, addr);
            Object serverData = newServerData(dataCls, r.name, addr);
            Screen parent = new TitleScreen();

            if (mc.level != null) disconnect(mc, parent);

            for (Method m : connCls.getMethods()) {
                if (!m.getName().equals("startConnecting") || !Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] types = m.getParameterTypes();
                Object[] args = new Object[types.length];
                boolean ok = true;
                for (int i = 0; i < types.length && ok; i++) {
                    Class<?> t = types[i];
                    if (Screen.class.isAssignableFrom(t)) args[i] = parent;
                    else if (t == Minecraft.class) args[i] = mc;
                    else if (t == addrCls) args[i] = serverAddress;
                    else if (t == dataCls) args[i] = serverData;
                    else if (t == boolean.class) args[i] = false;
                    else if (!t.isPrimitive()) args[i] = null;
                    else ok = false;
                }
                if (!ok) continue;
                m.invoke(null, args);
                return "Connecting to " + r.name + "...";
            }
            throw new NoSuchMethodException("startConnecting");
        } catch (Exception e) {
            NovaClient.LOGGER.warn("Region connect failed", e);
            Compat.clipboard(mc, addr);
            return "Could not connect from here. Address copied: " + addr;
        }
    }

    private static Object newServerData(Class<?> dataCls, String name, String addr) throws Exception {
        for (Constructor<?> c : dataCls.getConstructors()) {
            Class<?>[] t = c.getParameterTypes();
            if (t.length == 3 && t[0] == String.class && t[1] == String.class) {
                if (t[2] == boolean.class) return c.newInstance(name, addr, false);
                if (t[2].isEnum()) {
                    Object pick = t[2].getEnumConstants()[0];
                    for (Object k : t[2].getEnumConstants()) if (k.toString().equals("OTHER")) pick = k;
                    return c.newInstance(name, addr, pick);
                }
            }
        }
        throw new NoSuchMethodException("ServerData(String, String, ...)");
    }

    private static void disconnect(Minecraft mc, Screen parent) throws Exception {
        Method best = null;
        for (Method m : Minecraft.class.getMethods()) {
            if (!m.getName().equals("disconnect")) continue;
            if (best == null || m.getParameterCount() < best.getParameterCount()) best = m;
        }
        if (best == null) throw new NoSuchMethodException("disconnect");
        Class<?>[] types = best.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (Screen.class.isAssignableFrom(types[i])) args[i] = parent;
            else if (types[i] == boolean.class) args[i] = false;
        }
        best.invoke(mc, args);
    }
}
