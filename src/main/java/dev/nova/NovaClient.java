package dev.nova;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nova.gui.ClickGui;
import dev.nova.module.ModuleManager;
import dev.nova.module.render.Freecam;
import dev.nova.util.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NovaClient implements ClientModInitializer {
    public static final String ID = "nova";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));

    public static final KeyMapping OPEN_GUI = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.nova.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

    public static final KeyMapping FREECAM_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.nova.freecam", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

    @Override
    public void onInitializeClient() {
        ModuleManager.init();

        ClientTickEvents.START_CLIENT_TICK.register(ModuleManager::preTick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_GUI.consumeClick()) {
                if (mc.gui.screen() == null) mc.gui.setScreen(new ClickGui());
            }
            while (FREECAM_KEY.consumeClick()) {
                if (mc.gui.screen() == null) ModuleManager.get(Freecam.class).toggle();
            }
            ModuleManager.tick(mc);
        });

        // Gizmos are collected while the level frame is built, so draw during extraction.
        LevelRenderEvents.END_EXTRACTION.register(ctx -> ModuleManager.render(Minecraft.getInstance()));

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        LOGGER.info("Nova Client loaded. Press Right Shift to open the ClickGUI.");
    }
}
