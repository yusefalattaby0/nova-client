package dev.nova.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nova.NovaClient;
import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves and loads module state and every setting to config/nova.json. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nova.json");
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject mod = new JsonObject();
            mod.addProperty("enabled", m.enabled());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.settings()) settings.add(s.name, s.save());
            mod.add("settings", settings);
            root.add(m.name, mod);
        }
        try {
            Files.writeString(file(), GSON.toJson(root));
        } catch (IOException e) {
            NovaClient.LOGGER.warn("Could not save config", e);
        }
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(f), JsonObject.class);
            if (root == null) return;
            for (Module m : ModuleManager.all()) {
                if (!root.has(m.name)) continue;
                JsonObject mod = root.getAsJsonObject(m.name);
                if (mod.has("enabled")) m.setEnabled(mod.get("enabled").getAsBoolean());
                if (!mod.has("settings")) continue;
                JsonObject settings = mod.getAsJsonObject("settings");
                for (Setting<?> s : m.settings()) {
                    JsonElement e = settings.get(s.name);
                    if (e == null) continue;
                    try {
                        s.load(e);
                    } catch (RuntimeException bad) {
                        NovaClient.LOGGER.warn("Ignoring bad value for {}.{}", m.name, s.name);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            NovaClient.LOGGER.warn("Could not read config", e);
        }
    }
}
