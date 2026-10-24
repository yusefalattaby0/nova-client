
package dev.nova.module.render;

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
                "Fly a detached camera. Your player stays put.",
                Category.RENDER
        );
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;

        if (p == null || lvl == null) {
            setEnabled(false);
            return;
        }

        owner = p;
        level = lvl;

        savedYaw = p.getYRot();
        savedPitch = p.getXRot();

        camYaw = savedYaw;
        camPitch = savedPitch;

        Vec3 eye = p.getEyePosition();
        ex = eye.x;
        ey = eye.y;
        ez = eye.z;

        cam = new RemotePlayer(lvl, p.getGameProfile());
        placeCamera();

        savedInput = p.input;
        p.input = new ClientInput();

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
        lastNanos = 0L;
    }

    private boolean stillValid(Minecraft mc) {
        return owner != null
                && mc.player == owner
                && mc.level == level;
    }

    /**
     * Called by the mouse mixin instead of turning the real player.
     * Returns false when the detached camera is not active.
     */
    public boolean turnCamera(double deltaYaw, double deltaPitch) {
        if (cam == null || !enabled()) {
            return false;
        }

        cam.turn(deltaYaw, deltaPitch);

        camYaw = cam.getYRot();
        camPitch = Mth.clamp(cam.getXRot(), -90.0f, 90.0f);

        cam.setYRot(camYaw);
        cam.setXRot(camPitch);

        return true;
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

        LocalPlayer p = mc.player;

        mc.setCameraEntity(cam);

        // Read rotation from the camera only; never feed player rotation
        // back into the camera.
        camYaw = cam.getYRot();
        camPitch = Mth.clamp(cam.getXRot(), -90.0f, 90.0f);

        // Keep first-person held items aligned with the detached view.
        p.setYRot(camYaw);
        p.setXRot(camPitch);
        p.yRotO = camYaw;
        p.xRotO = camPitch;
        p.setYHeadRot(camYaw);
        p.yHeadRotO = camYaw;

        // Keep the real player's visible body facing its original direction.
        p.setYBodyRot(savedYaw);
        p.yBodyRotO = savedYaw;

        long now = System.nanoTime();
        double dt = Math.min(
                0.1,
                Math.max(0.0, (now - lastNanos) / 1.0e9)
        );
        lastNanos = now;

        if (mc.gui.screen() == null) {
            Options o = mc.options;

            double forward =
                    (o.keyUp.isDown() ? 1.0 : 0.0)
                    - (o.keyDown.isDown() ? 1.0 : 0.0);

            double strafe =
                    (o.keyRight.isDown() ? 1.0 : 0.0)
                    - (o.keyLeft.isDown() ? 1.0 : 0.0);

            double vertical =
                    (o.keyJump.isDown() ? 1.0 : 0.0)
                    - (o.keyShift.isDown() ? 1.0 : 0.0);

            double yaw = Math.toRadians(camYaw);
            double pitch = Math.toRadians(camPitch);

            double lookX = -Math.sin(yaw) * Math.cos(pitch);
            double lookY = -Math.sin(pitch);
            double lookZ = Math.cos(yaw) * Math.cos(pitch);

            double rightX = -Math.cos(yaw);
            double rightZ = -Math.sin(yaw);

            double mx = lookX * forward + rightX * strafe;
            double my = lookY * forward + vertical;
            double mz = lookZ * forward + rightZ * strafe;

            double len = Math.sqrt(
                    mx * mx + my * my + mz * mz
            );

            if (len > 1.0e-6) {
                double multiplier = o.keySprint.isDown()
                        ? boost.d()
                        : 1.0;

                double velocity = speed.d()
                        * multiplier
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

        cam.setPos(ex, ey - cam.getEyeHeight(), ez);
        cam.setYRot(camYaw);
        cam.setXRot(camPitch);
        cam.setYHeadRot(camYaw);
        cam.setYBodyRot(camYaw);
        cam.setOldPosAndRot();

        cam.yHeadRotO = camYaw;
        cam.yBodyRotO = camYaw;
    }

    private void restorePlayerRotation(LocalPlayer p) {
        p.setYRot(savedYaw);
        p.setXRot(savedPitch);

        p.yRotO = savedYaw;
        p.xRotO = savedPitch;

        p.setYHeadRot(savedYaw);
        p.yHeadRotO = savedYaw;

        p.setYBodyRot(savedYaw);
        p.yBodyRotO = savedYaw;
    }
}
