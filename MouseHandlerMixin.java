
package dev.nova.mixin;

import dev.nova.module.ModuleManager;
import dev.nova.module.render.Freecam;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Redirect(
            method = "turnPlayer(D)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void nova$redirectMouseTurn(
            LocalPlayer player,
            double deltaYaw,
            double deltaPitch
    ) {
        Freecam freecam = ModuleManager.get(Freecam.class);

        if (freecam != null
                && freecam.enabled()
                && freecam.turnCamera(deltaYaw, deltaPitch)) {
            return;
        }

        player.turn(deltaYaw, deltaPitch);
    }
}
