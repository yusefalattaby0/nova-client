package dev.nova.module.render;

import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Highlights entities: players, mobs, zombie villagers, items and more. Every group can be toggled. */
public class EntityEsp extends EspModule {
    public enum Cat {
        PLAYERS("Players", 0xFF4D4D, true),
        ZOMBIE_VILLAGER("Zombie Villagers", 0x6BCB77, true),
        ZOMBIFIED_PIGLIN("Zombified Piglins", 0xE88DA0, false),
        VILLAGERS("Villagers & Traders", 0xC98A4B, true),
        HOSTILE("Hostile Mobs", 0xFF8A1F, true),
        ANIMALS("Animals", 0xE6E6E6, false),
        WATER("Water Mobs", 0x4DA3FF, false),
        AMBIENT("Ambient (Bats)", 0x9AA0A6, false),
        ITEMS("Dropped Items", 0xFFD83B, false),
        XP("XP Orbs", 0x7CFF4D, false),
        PROJECTILES("Projectiles", 0xFF5555, false),
        VEHICLES("Boats & Minecarts", 0x55FFFF, false),
        DECOR("Armor Stands & Frames", 0xB26DFF, false),
        OTHER("Everything Else", 0xFFFFFF, false);

        public final String label;
        public final int color;
        public final boolean defaultOn;

        Cat(String label, int color, boolean defaultOn) {
            this.label = label;
            this.color = color;
            this.defaultOn = defaultOn;
        }
    }

    private final Setting.Bool[] enabledCats = new Setting.Bool[Cat.values().length];
    private final Setting.Col[] catColors = new Setting.Col[Cat.values().length];
    private final Setting.Bool colorByType = new Setting.Bool("Color By Type", true);
    private final Setting.Col singleColor = new Setting.Col("Color", 0x4DA3FF);

    public EntityEsp() {
        super("Entity ESP", "Highlights players, mobs, zombie villagers, items and more.");

        for (Cat c : Cat.values()) {
            enabledCats[c.ordinal()] = add(new Setting.Bool(c.label, c.defaultOn));
        }

        addAppearanceSettings();

        add(colorByType, () -> colorMode.get() == ColorMode.STATIC);
        add(singleColor, () -> colorMode.get() == ColorMode.PULSE
                || (colorMode.get() == ColorMode.STATIC && !colorByType.get()));
        for (Cat c : Cat.values()) {
            catColors[c.ordinal()] = add(new Setting.Col(c.label + " Color", c.color),
                    () -> colorMode.get() == ColorMode.STATIC && colorByType.get()
                            && enabledCats[c.ordinal()].get());
        }

        addBehaviorSettings();

        mode.set(EspMode.OUTLINE);
        range.set(128.0);
    }

    private static Cat classify(Entity e) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (e instanceof Player) return Cat.PLAYERS;
        if (id.equals("zombie_villager")) return Cat.ZOMBIE_VILLAGER;
        if (id.equals("zombified_piglin")) return Cat.ZOMBIFIED_PIGLIN;
        if (id.equals("villager") || id.equals("wandering_trader")) return Cat.VILLAGERS;
        if (e instanceof ItemEntity) return Cat.ITEMS;
        if (e instanceof ExperienceOrb) return Cat.XP;
        if (e instanceof Projectile) return Cat.PROJECTILES;
        if (id.contains("minecart") || id.contains("boat") || id.contains("raft")) return Cat.VEHICLES;
        if (id.equals("armor_stand") || id.contains("item_frame") || id.equals("painting")) return Cat.DECOR;

        MobCategory mc = e.getType().getCategory();
        if (mc == MobCategory.MONSTER) return Cat.HOSTILE;
        if (mc == MobCategory.CREATURE) return Cat.ANIMALS;
        if (mc == MobCategory.AMBIENT) return Cat.AMBIENT;
        String n = mc.name();
        if (n.equals("WATER_CREATURE") || n.equals("WATER_AMBIENT")
                || n.equals("UNDERGROUND_WATER_CREATURE") || n.equals("AXOLOTLS")) return Cat.WATER;
        return Cat.OTHER;
    }

    @Override
    protected void collect(Level level, BlockEntity be, List<Target> out) {
        // unused: this module scans entities, not block entities
    }

    @Override
    public void onTick(Minecraft mc) {
        // entities move every frame, so they are refreshed in onFrame instead
    }

    @Override
    protected void onDisable() {
        super.onDisable();
    }

    @Override
    public void onFrame(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            setTargets(List.of());
            return;
        }
        double r = range.d() > 0 ? range.d() : 512.0;
        Entity self = mc.player;
        Entity camera = mc.getCameraEntity();
        AABB area = self.getBoundingBox().inflate(r);
        float pt = partialTick(mc);

        List<Target> out = new ArrayList<>();
        for (Entity e : mc.level.getEntities(self, area, x -> true)) {
            if (e == self || e == camera || e.isRemoved()) continue;
            Cat c = classify(e);
            if (!enabledCats[c.ordinal()].get()) continue;
            // Slide the box to where the entity is drawn this frame, so it stays smooth while moving.
            AABB box = e.getBoundingBox().move(e.getPosition(pt).subtract(e.position()));
            out.add(new Target(e.blockPosition(), box, c.ordinal()));
        }
        setTargets(out);
    }

    @Override
    protected int baseColor(int kind) {
        if (colorMode.get() == ColorMode.STATIC && colorByType.get()) return catColors[kind].get();
        return singleColor.get();
    }
}
