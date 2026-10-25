package dev.nova.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Calls whose names or signatures may differ between Minecraft versions are made by name here,
 * so a mismatch fails quietly at runtime instead of breaking the build. If something stops
 * working after a Minecraft update, this is the first file to look at.
 */
public final class Compat {
    private Compat() {}

    /** Calls a public method by name and argument count. Throws if none fits. */
    public static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < args.length && ok; i++) ok = fits(types[i], args[i]);
            if (!ok) continue;
            m.setAccessible(true);
            return m.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean fits(Class<?> type, Object arg) {
        if (arg == null) return !type.isPrimitive();
        if (type.isPrimitive()) {
            return (type == boolean.class && arg instanceof Boolean)
                    || (type == int.class && arg instanceof Integer)
                    || (type == long.class && arg instanceof Long)
                    || (type == double.class && arg instanceof Double)
                    || (type == float.class && arg instanceof Float);
        }
        return type.isInstance(arg);
    }

    /** Runs a server command without the leading slash, like typing /rtp in chat. */
    public static boolean command(Minecraft mc, String cmd) {
        try {
            Object conn = invoke(mc, "getConnection");
            if (conn == null) return false;
            try {
                invoke(conn, "sendCommand", cmd);
            } catch (NoSuchMethodException e) {
                invoke(conn, "sendChat", "/" + cmd);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Biome name at the player, like "SNOWY TAIGA". */
    public static String biomeName(Minecraft mc) {
        if (mc.level == null || mc.player == null) return "UNKNOWN";
        try {
            BlockPos pos = mc.player.blockPosition();
            Object holder = mc.level.getBiome(pos);
            String id = String.valueOf(invoke(holder, "getRegisteredName"));
            int colon = id.indexOf(':');
            if (colon >= 0) id = id.substring(colon + 1);
            return id.replace('_', ' ').toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /** Copies text to the clipboard; returns false if this version does something different. */
    public static boolean clipboard(Minecraft mc, String text) {
        try {
            Object handler = Minecraft.class.getField("keyboardHandler").get(mc);
            invoke(handler, "setClipboard", text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The key a KeyMapping is currently bound to (GLFW code), or the fallback if it can't be read. */
    public static int boundKeyCode(KeyMapping mapping, int fallback) {
        try {
            Field pick = null;
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != InputConstants.Key.class) continue;
                if (f.getName().equals("key") || pick == null) pick = f;
            }
            if (pick == null) return fallback;
            pick.setAccessible(true);
            Object key = pick.get(mapping);
            try {
                return ((Number) invoke(key, "getValue")).intValue();
            } catch (NoSuchMethodException e) {
                return ((Number) invoke(key, "value")).intValue();
            }
        } catch (Exception e) {
            return fallback;
        }
    }
}
