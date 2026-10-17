package dev.nova.module.combat;

import dev.nova.module.Module;
import dev.nova.setting.Setting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Faster end crystal breaking.
 *  - Instant Break: the crystal disappears on your screen the moment you hit it, so the next one can be placed right away.
 *  - Auto Break: hits a crystal automatically while your crosshair is on it.
 */
public class FastCrystal extends Module {
    private final Setting.Bool instantBreak = new Setting.Bool("Instant Break", true);
    private final Setting.Bool autoBreak = new Setting.Bool("Auto Break (Crosshair)", false);
    private final Setting.Num breakDelay = new Setting.Num("Break Delay", 0, 0, 10, 1, " ticks");

    private int cooldown;

    public FastCrystal() {
        super("Fast Crystal", "Breaks end crystals faster.", Category.COMBAT);
        add(instantBreak);
        add(autoBreak);
        add(breakDelay, autoBreak::get);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (enabled() && instantBreak.get() && player == mc.player && isCrystal(entity)) {
                entity.discard(); // hide it right away; the server still gets the hit
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean isCrystal(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("end_crystal");
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (!autoBreak.get() || mc.gui.screen() != null) return;

        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr && isCrystal(ehr.getEntity())) {
            mc.gameMode.attack(p, ehr.getEntity());
            p.swing(InteractionHand.MAIN_HAND);
            cooldown = breakDelay.i();
        }
    }
}
