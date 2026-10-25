package dev.nova.module.render;

import com.mojang.authlib.GameProfile;
import dev.nova.module.Module;
import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class Freecam extends Module {

    private final Setting.Num speed =
            add(new Setting.Num("Speed", 12, 1, 60, 1, " b/s"));

    private final Setting.Num boost =
            add(new Setting.Num("Sprint Boost", 2.5, 1, 6, 0.5, "x"));

    private RemotePlayer cam;
    private LocalPlayer owner;
    private ClientLevel level;
    private ClientInput savedInput;

    private float savedYaw;
    private float savedPitch;

    private float camYaw;
    private float camPitch;

    private double ex;
    private double ey;
    private double ez;

    private long lastNanos;

    public Freecam() {
        super(
                "Freecam",
                "Fly a detached camera while your player stays put.",
                Category.RENDER
        );
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();

        LocalPlayer player = mc.player;
        ClientLevel clientLevel = mc.level;

        if (player == null || clientLevel == null) {
            setEnabled(false);
            return;
        }

        owner = player;
        level = clientLevel;

        savedYaw = player.getYRot();
        savedPitch = player.getXRot();

        camYaw = savedYaw;
        camPitch = savedPitch;

        Vec3 eye = player.getEyePosition();

        ex = eye.x;
        ey = eye.y;
        ez = eye.z;

        // Give the camera a different profile so it is not confused
        // with the actual player.
        GameProfile cameraProfile = new GameProfile(
                UUID.randomUUID(),
                "NovaFreecam"
        );

        cam = new RemotePlayer(clientLevel, cameraProfile);

        placeCamera();

        // Freeze normal player movement without moving the player.
        savedInput = player.input;
        player.input = new ClientInput();

        mc.setCameraEntity(cam);

        lastNanos = System.nanoTime();
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();

        if (owner != null && savedInput != null) {
            owner.input = savedInput;
        }

        if (owner != null) {
            restorePlayerRotation(owner);
        }

        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }

        cam = null;
        owner = null;
        level = null;
        savedInput = null;
    }

    private boolean stillValid(Minecraft mc) {
        return owner != null
                && mc.player == owner
                && mc.level == level;
    }

    /**
     * Rotate only the free camera.
     * The 0.15 multiplier matches normal Minecraft mouse rotation scaling.
     */
    public void turnCamera(double deltaYaw, double deltaPitch) {
        if (cam == null) {
            return;
        }

        camYaw += (float) (deltaYaw * 0.15);

        camPitch = Mth.clamp(
                camPitch + (float) (deltaPitch * 0.15),
                -90.0f,
                90.0f
        );

        placeCamera();
    }

    /**
     * These methods let the first-person hand renderer use
     * the free camera's view instead of the real player's view.
     */
    public float getCameraViewXRot(float partialTick) {
        return cam == null
                ? savedPitch
                : cam.getViewXRot(partialTick);
    }

    public float getCameraViewYRot(float partialTick) {
        return cam == null
                ? savedYaw
                : cam.getViewYRot(partialTick);
    }

    @Override
    public void onPreTick(Minecraft mc) {
        if (cam == null) {
            return;
        }

        Options options = mc.options;

        options.keyAttack.setDown(false);
        options.keyUse.setDown(false);

        while (options.keyAttack.consumeClick()) {
        }

        while (options.keyUse.consumeClick()) {
        }

        while (options.keyPickItem.consumeClick()) {
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (cam != null && !stillValid(mc)) {
            setEnabled(false);
        }
    }

    @Override
    public void onFrame(Minecraft mc) {
        if (cam == null) {
            return;
        }

        if (!stillValid(mc)) {
            setEnabled(false);
            return;
        }

        // Keep the camera detached from the real player.
        mc.setCameraEntity(cam);

        camYaw = cam.getYRot();
        camPitch = Mth.clamp(
                cam.getXRot(),
                -90.0f,
                90.0f
        );

        // IMPORTANT:
        // Do not copy the camera's rotation onto the real player.
        // The real player's position and rotation should stay put.

        long now = System.nanoTime();

        double dt = Math.min(
                0.1,
                (now - lastNanos) / 1.0e9
        );

        lastNanos = now;

        if (mc.gui.screen() == null) {
            Options options = mc.options;

            double forward =
                    (options.keyUp.isDown() ? 1 : 0)
                    - (options.keyDown.isDown() ? 1 : 0);

            double strafe =
                    (options.keyRight.isDown() ? 1 : 0)
                    - (options.keyLeft.isDown() ? 1 : 0);

            double up =
                    (options.keyJump.isDown() ? 1 : 0)
                    - (options.keyShift.isDown() ? 1 : 0);

            double yaw = Math.toRadians(camYaw);
            double pitch = Math.toRadians(camPitch);

            double lookX = -Math.sin(yaw) * Math.cos(pitch);
            double lookY = -Math.sin(pitch);
            double lookZ = Math.cos(yaw) * Math.cos(pitch);

            double rightX = -Math.cos(yaw);
            double rightZ = -Math.sin(yaw);

            double mx = lookX * forward + rightX * strafe;
            double my = lookY * forward + up;
            double mz = lookZ * forward + rightZ * strafe;

            double len = Math.sqrt(
                    mx * mx + my * my + mz * mz
            );

            if (len > 1.0e-6) {
                double velocity =
                        speed.d()
                        * (options.keySprint.isDown()
                                ? boost.d()
                                : 1.0)
                        * dt
                        / Math.max(1.0, len);

                ex += mx * velocity;
                ey += my * velocity;
                ez += mz * velocity;
            }
        }

        placeCamera();
    }

    private void placeCamera() {
        if (cam == null) {
            return;
        }

        cam.setPos(
                ex,
                ey - cam.getEyeHeight(),
                ez
        );

        cam.setYRot(camYaw);
        cam.setXRot(camPitch);

        cam.setYHeadRot(camYaw);
        cam.setYBodyRot(camYaw);

        cam.setOldPosAndRot();

        cam.yHeadRotO = camYaw;
        cam.yBodyRotO = camYaw;
    }

    private void restorePlayerRotation(LocalPlayer player) {
        player.setYRot(savedYaw);
        player.setXRot(savedPitch);

        player.yRotO = savedYaw;
        player.xRotO = savedPitch;
    }
}
