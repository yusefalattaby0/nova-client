package dev.nova.module.render;

import dev.nova.module.Module;
import dev.nova.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * Detached camera. Your real body stays where it is, keeps facing the same way, and you can see it
 * standing there. The view is moved by a client-only stand-in entity (never sent to the server).
 *
 *  - WASD flies the camera; the player's own movement input is swapped for an empty one
 *  - the mouse turns only the camera; the body keeps its original look direction
 *  - while the game runs a tick, the view is handed back to your body, so mining and placing
 *    still happen exactly where your body is facing
 */
public class Freecam extends Module {
    private final Setting.Num speed = add(new Setting.Num("Speed", 12, 1, 60, 1, " b/s"));
    private final Setting.Num boost = add(new Setting.Num("Sprint Boost", 2.5, 1, 6, 0.5, "x"));
    private final Setting.Bool useBody = add(new Setting.Bool("Mine & Place From Body", true));
    private final Setting.Bool hideHand = add(new Setting.Bool("Hide First-Person Hand", true));

    private RemotePlayer cam;
    private LocalPlayer owner;
    private ClientLevel level;
    private ClientInput savedInput;

    private static Field handField;

    private float savedYaw, savedPitch; // the body's look direction, kept for the whole session
    private float camYaw, camPitch;
    private double ex, ey, ez;
    private long lastNanos;

    public Freecam() {
        super("Freecam", "Fly a detached camera. Your body stays put, visible, and keeps mining.", Category.RENDER);
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
        setHandVisible(mc, true);
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

    /** Start of every tick: hand the view back to the body so clicks act where the body faces. */
    @Override
    public void onPreTick(Minecraft mc) {
        if (cam == null || !stillValid(mc)) return;
        LocalPlayer p = mc.player;
        Options o = mc.options;
        if (useBody.get()) {
            lockBody(p);
            mc.setCameraEntity(p);
        } else {
            o.keyAttack.setDown(false);
            o.keyUse.setDown(false);
            while (o.keyAttack.consumeClick()) { }
            while (o.keyUse.consumeClick()) { }
            while (o.keyPickItem.consumeClick()) { }
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (cam == null) return;
        if (!stillValid(mc)) {
            setEnabled(false);
            return;
        }
        mc.setCameraEntity(cam); // the tick is over: look through the camera again
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

        // Mouse look. The game turns the player; we move that turn onto the camera and put the body back.
        camYaw += p.getYRot() - savedYaw;
        camPitch = Mth.clamp(camPitch + (p.getXRot() - savedPitch), -90f, 90f);
        lockBody(p);
        setHandVisible(mc, !hideHand.get());

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
        // A living entity's view looks along its HEAD rotation, so that has to follow too.
        cam.setYHeadRot(camYaw);
        cam.setYBodyRot(camYaw);
        cam.setOldPosAndRot(); // no interpolation lag: the camera is exactly where we put it
        cam.yHeadRotO = camYaw;
        cam.yBodyRotO = camYaw;
    }

    /** Put the body back to the direction it was facing when Freecam started. */
    private void lockBody(LocalPlayer p) {
        p.setYRot(savedYaw);
        p.setXRot(savedPitch);
        p.yRotO = savedYaw;
        p.xRotO = savedPitch;
    }

    private void restorePlayerRotation(LocalPlayer p) {
        lockBody(p);
    }

    /** Best effort: the hand overlay is switched by a private flag, so this quietly does nothing if it is missing. */
    private static void setHandVisible(Minecraft mc, boolean visible) {
        try {
            if (handField == null) {
                handField = GameRenderer.class.getDeclaredField("renderHand");
                handField.setAccessible(true);
            }
            handField.setBoolean(mc.gameRenderer, visible);
        } catch (Throwable ignored) {
            // field not found in this version: leave the hand alone
        }
    }
}
