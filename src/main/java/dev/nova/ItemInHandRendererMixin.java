package dev.nova.mixin;

import dev.nova.module.ModuleManager;
import dev.nova.module.render.Freecam;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Redirect(
            method = "submitHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"
            )
    )
    private float nova$useFreecamPitch(
            LocalPlayer player,
            float partialTick
    ) {
        Freecam freecam = ModuleManager.get(Freecam.class);

        if (freecam != null && freecam.enabled()) {
            return freecam.getCameraViewXRot(partialTick);
        }

        return player.getViewXRot(partialTick);
    }

    @Redirect(
            method = "submitHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"
            )
    )
    private float nova$useFreecamYaw(
            LocalPlayer player,
            float partialTick
    ) {
        Freecam freecam = ModuleManager.get(Freecam.class);

        if (freecam != null && freecam.enabled()) {
            return freecam.getCameraViewYRot(partialTick);
        }

        return player.getViewYRot(partialTick);
    }
}
