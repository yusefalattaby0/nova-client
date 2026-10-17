package dev.nova.module;

import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class Module {
    public enum Category {
        RENDER("Render"),
        COMBAT("Combat"),
        CLIENT("Client");

        public final String label;
        Category(String label) { this.label = label; }
    }

    public final String name;
    public final String description;
    public final Category category;

    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    protected <S extends Setting<?>> S add(S setting, BooleanSupplier visibleWhen) {
        setting.visibleWhen(visibleWhen);
        return add(setting);
    }

    public List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }

    public boolean toggleable() { return true; }
    public boolean enabled() { return enabled; }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean on) {
        if (!toggleable()) on = true;
        if (on == enabled) return;
        enabled = on;
        if (on) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick(Minecraft mc) {}
    /** Runs at the very start of each client tick. */
    public void onPreTick(Minecraft mc) {}
    /** Runs once per rendered frame while the module is on. */
    public void onFrame(Minecraft mc) {}
}
