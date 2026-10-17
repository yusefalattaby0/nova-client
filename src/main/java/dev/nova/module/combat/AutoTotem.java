package dev.nova.module.combat;

import dev.nova.module.Module;
import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/** Keeps a Totem of Undying in your offhand, refilling it right after one pops. */
public class AutoTotem extends Module {
    private final Setting.Num health = new Setting.Num("Swap Below Health", 20, 1, 20, 1, " hp");
    private final Setting.Num delay = new Setting.Num("Delay", 0, 0, 10, 1, " ticks");
    private final Setting.Bool replaceItems = new Setting.Bool("Replace Offhand Item", true);
    private final Setting.Bool mainInvFirst = new Setting.Bool("Take From Inventory First", true);

    private int cooldown;

    public AutoTotem() {
        super("Auto Totem", "Keeps a totem in your offhand. Health 20 = always.", Category.COMBAT);
        add(health);
        add(delay);
        add(replaceItems);
        add(mainInvFirst);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gui.screen() != null) return;
        if (cooldown > 0) { cooldown--; return; }

        if (health.i() < 20 && p.getHealth() + p.getAbsorptionAmount() > health.d()) return;
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (!replaceItems.get() && !p.getOffhandItem().isEmpty()) return;

        int found = -1;
        if (mainInvFirst.get()) {
            for (int i = 9; i < 36 && found < 0; i++) if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) found = i;
            for (int i = 0; i < 9 && found < 0; i++) if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) found = i;
        } else {
            for (int i = 0; i < 36 && found < 0; i++) if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) found = i;
        }
        if (found < 0) return;

        // Inventory index -> player-inventory-menu slot (hotbar is 36..44), then swap with offhand (button 40).
        int menuSlot = found < 9 ? 36 + found : found;
        mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, menuSlot, 40, ContainerInput.SWAP, p);
        cooldown = delay.i();
    }
}
