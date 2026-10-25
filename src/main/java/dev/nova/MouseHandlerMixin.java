package dev.nova.mixin;

import dev.nova.module.ModuleManager;
import dev.nova.module.render.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Redirect(
            method = "turnPlayer(D)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void nova$turnCamera(
            LocalPlayer player,
            double deltaYaw,
            double deltaPitch
    ) {
        Freecam freecam = ModuleManager.get(Freecam.class);

        if (freecam != null && freecam.enabled()) {
            freecam.turnCamera(deltaYaw, deltaPitch);
        } else {
            player.turn(deltaYaw, deltaPitch);
        }
    }

    @Inject(
            method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nova$blockClicksWhileFreecam(
            long window,
            MouseButtonInfo button,
            int action,
            CallbackInfo ci
    ) {
        Freecam freecam = ModuleManager.get(Freecam.class);

        if (freecam != null
                && freecam.enabled()
                && Minecraft.getInstance().gui.screen() == null) {
            ci.cancel();
        }
    }
}
