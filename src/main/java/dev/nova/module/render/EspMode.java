package dev.nova.module.render;

public enum EspMode {
    OUTLINE("Outline"),
    FILLED("Filled"),
    BOTH("Outline + Fill"),
    CORNERS("Corners");

    private final String label;
    EspMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
