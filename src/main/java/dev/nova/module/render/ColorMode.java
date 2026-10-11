package dev.nova.module.render;

public enum ColorMode {
    STATIC("Static"),
    RAINBOW("Rainbow"),
    PULSE("Pulse");

    private final String label;
    ColorMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
