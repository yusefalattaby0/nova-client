package dev.nova.util;

public final class Colors {
    private Colors() {}

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Packs an alpha (0-255) and an RGB int into ARGB. */
    public static int argb(int alpha, int rgb) {
        return (clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int hsb(float h, float s, float b) {
        h = h - (float) Math.floor(h);
        float hh = h * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = b * (1f - s);
        float q = b * (1f - s * f);
        float t = b * (1f - s * (1f - f));
        float r, g, bl;
        switch (i % 6) {
            case 0 -> { r = b; g = t; bl = p; }
            case 1 -> { r = q; g = b; bl = p; }
            case 2 -> { r = p; g = b; bl = t; }
            case 3 -> { r = p; g = q; bl = b; }
            case 4 -> { r = t; g = p; bl = b; }
            default -> { r = b; g = p; bl = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(bl * 255);
    }

    public static float[] toHsb(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = (((b - r) / d) + 2f) / 6f;
        else h = (((r - g) / d) + 4f) / 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static int rainbow() {
        return hsb((System.currentTimeMillis() % 5000L) / 5000f, 0.7f, 1f);
    }

    /** Breathes the brightness of a color. */
    public static int pulse(int rgb) {
        float[] c = toHsb(rgb);
        float wave = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 350.0));
        float k = 0.6f + 0.4f * wave;
        return hsb(c[0], c[1], Math.max(0.15f, c[2] * k));
    }

    /** Per-channel lerp of two ARGB colors. */
    public static int lerp(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >> shift) & 255;
            int cb = (b >> shift) & 255;
            out |= (Math.round(ca + (cb - ca) * t) & 255) << shift;
        }
        return out;
    }
}
