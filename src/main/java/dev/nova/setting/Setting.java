package dev.nova.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nova.util.Colors;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    public final String name;
    protected T value;
    private BooleanSupplier visible = () -> true;

    protected Setting(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public T get() { return value; }
    public void set(T v) { this.value = v; }

    public void visibleWhen(BooleanSupplier s) { this.visible = s; }
    public boolean isVisible() { return visible.getAsBoolean(); }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);

    // ------------------------------------------------------------------

    public static final class Bool extends Setting<Boolean> {
        public Bool(String name, boolean def) { super(name, def); }
        public void toggle() { value = !value; }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { value = e.getAsBoolean(); }
    }

    public static final class Num extends Setting<Double> {
        public final double min, max, step;
        public final String suffix;

        public Num(String name, double def, double min, double max, double step, String suffix) {
            super(name, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix;
        }

        @Override
        public void set(Double v) {
            double snapped = Math.round(v / step) * step;
            snapped = Math.round(snapped * 10000.0) / 10000.0;
            value = Math.max(min, Math.min(max, snapped));
        }

        public double d() { return value; }
        public float f() { return value.floatValue(); }
        public int i() { return (int) Math.round(value); }
        public double progress() { return (value - min) / (max - min); }

        public String display() {
            String num;
            if (step >= 1) num = Integer.toString(i());
            else if (step >= 0.1) num = String.format("%.1f", value);
            else num = String.format("%.2f", value);
            return num + suffix;
        }

        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { set(e.getAsDouble()); }
    }

    public static final class Enm<E extends Enum<E>> extends Setting<E> {
        private final E[] all;

        public Enm(String name, E def) {
            super(name, def);
            this.all = def.getDeclaringClass().getEnumConstants();
        }

        public void cycle(int dir) {
            int i = (value.ordinal() + dir + all.length) % all.length;
            value = all[i];
        }

        @Override public JsonElement save() { return new JsonPrimitive(value.name()); }
        @Override public void load(JsonElement e) {
            String n = e.getAsString();
            for (E c : all) if (c.name().equals(n)) { value = c; return; }
        }
    }

    public static final class Col extends Setting<Integer> {
        private float h, s, b;

        public Col(String name, int rgb) {
            super(name, rgb & 0xFFFFFF);
            sync();
        }

        private void sync() {
            float[] c = Colors.toHsb(value);
            h = c[0]; s = c[1]; b = c[2];
        }

        @Override public void set(Integer v) { value = v & 0xFFFFFF; sync(); }

        public float[] hsb() { return new float[]{h, s, b}; }

        public void setHsb(float h, float s, float b) {
            this.h = h; this.s = s; this.b = b;
            this.value = Colors.hsb(h, s, b);
        }

        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { set(e.getAsInt()); }
    }
}
