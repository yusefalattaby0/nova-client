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

/**
 * Detached camera. Your real player stays where it is (and stays still);
 * the view is moved by a client-only stand-in entity. The camera position is never sent to the server,
 * but your player does turn to face where you look (the server sees that rotation).
 *
 * How it works, in plain terms:
 *  - the player's movement input is swapped for an empty one, so WASD does not move the player
 *  - the camera looks wherever the mouse turns (the player turns with it; your original look direction is restored on exit)
 *  - clicks (attack / use / pick block) are swallowed so you can't interact from the camera
 */
public class Freecam extends Module {
    private final Setting.Num speed = add(new Setting.Num("Speed", 12, 1, 60, 1, " b/s"));
    private final Setting.Num boost = add(new Setting.Num("Sprint Boost", 2.5, 1, 6, 0.5, "x"));

    private RemotePlayer cam;
    private LocalPlayer owner;
    private ClientLevel level;
    private ClientInput savedInput;

    private float savedYaw, savedPitch;
    private float camYaw, camPitch;
    private double ex, ey, ez;
    private long lastNanos;

    public Freecam() {
        super("Freecam", "Fly a detached camera. Your player stays put.", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;
        if (p == null || lvl == null) {
            setEnabled(false); // can't start outside a world (also covers config load at startup)
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
        if (owner != null && savedInput != null) owner.input = savedInput;
        if (owner != null) restorePlayerRotation(owner);
        if (mc.player != null) mc.setCameraEntity(mc.player);
        cam = null;
        owner = null;
        level = null;
        savedInput = null;
    }

    private boolean stillValid(Minecraft mc) {
        return mc.player == owner && mc.level == level && owner != null;
    }

    /** Start of every tick: stop clicks from reaching the world. */
    @Override
    public void onPreTick(Minecraft mc) {
        if (cam == null) return;
        Options o = mc.options;
        o.keyAttack.setDown(false);
        o.keyUse.setDown(false);
        while (o.keyAttack.consumeClick()) { }
        while (o.keyUse.consumeClick()) { }
        while (o.keyPickItem.consumeClick()) { }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (cam != null && !stillValid(mc)) setEnabled(false);
    }

    /** Every rendered frame: apply mouse look and movement. */
    @Override
    public void onFrame(Minecraft mc) {
        if (cam == null) return;
        if (!stillValid(mc)) {
            setEnabled(false);
            return;
        }
        LocalPlayer p = mc.player;

        // keep the game looking through our camera (cheap; guards against anything resetting it)
        mc.setCameraEntity(cam);

        // Mouse look. The player's rotation is the single source of truth: the mouse normally turns the
        // player, so we read it from there. If the game turned the camera entity instead, fold that in too.
        float newYaw = p.getYRot() + (cam.getYRot() - camYaw);
        float newPitch = Mth.clamp(p.getXRot() + (cam.getXRot() - camPitch), -90f, 90f);
        p.setYRot(newYaw);
        p.setXRot(newPitch);
        camYaw = newYaw;
        camPitch = newPitch;

        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;

        if (mc.gui.screen() == null) {
            Options o = mc.options;
            double forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
            double strafe = (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0);
            double up = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);

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

            double len = Math.sqrt(mx * mx + my * my + mz * mz);
            if (len > 1.0e-6) {
                double v = speed.d() * (o.keySprint.isDown() ? boost.d() : 1.0) * dt / Math.max(1.0, len);
                ex += mx * v;
                ey += my * v;
                ez += mz * v;
            }
        }

        placeCamera();
    }

    private void placeCamera() {
        cam.setPos(ex, ey - cam.getEyeHeight(), ez);
        cam.setYRot(camYaw);
        cam.setXRot(camPitch);
        cam.setOldPosAndRot(); // no interpolation lag: the camera is exactly where we put it
    }

    private void restorePlayerRotation(LocalPlayer p) {
        p.setYRot(savedYaw);
        p.setXRot(savedPitch);
        p.yRotO = savedYaw;
        p.xRotO = savedPitch;
    }
}
