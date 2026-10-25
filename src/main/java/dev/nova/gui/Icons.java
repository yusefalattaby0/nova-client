package dev.nova.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/** Tiny pixel icons (9x9) so the wheel and panels don't need any texture files. */
final class Icons {
    private Icons() {}

    private static final Map<String, String[]> ICONS = Map.ofEntries(
            Map.entry("home", new String[]{
                    "....#....", "...###...", "..#####..", ".#######.", "#########",
                    ".#.....#.", ".#.###.#.", ".#.###.#.", ".#######."}),
            Map.entry("dollar", new String[]{
                    "..#####..", ".#..#..#.", "#..###..#", "#.#.#....", "#..###..#",
                    "#....#.#.", "#..###..#", ".#..#..#.", "..#####.."}),
            Map.entry("dice", new String[]{
                    ".#######.", "#.......#", "#.#...#.#", "#.......#", "#...#...#",
                    "#.......#", "#.#...#.#", "#.......#", ".#######."}),
            Map.entry("tag", new String[]{
                    "...#####.", "..#....##", ".#..##..#", "#..#..#.#", "#.#..#..#",
                    "#..##...#", ".#.....#.", "..#...#..", "...###..."}),
            Map.entry("box", new String[]{
                    "#########", "#.......#", "#########", "#.#####.#", "#.#...#.#",
                    "#.#...#.#", "#.#####.#", "#.......#", "#########"}),
            Map.entry("eye", new String[]{
                    ".........", "..#####..", ".#.....#.", "#..###..#", "#.##.##.#",
                    "#..###..#", ".#.....#.", "..#####..", "........."}),
            Map.entry("trail", new String[]{
                    "........#", ".......#.", "......#..", ".....#...", "##..#....",
                    ".####....", ".........", ".........", "........."}),
            Map.entry("pin", new String[]{
                    "..#####..", ".#.....#.", ".#..#..#.", ".#.....#.", "..#...#..",
                    "...#.#...", "....#....", ".........", "........."}),
            Map.entry("target", new String[]{
                    "..#####..", ".#.....#.", "#..###..#", "#.#...#.#", "#.#.#.#.#",
                    "#.#...#.#", "#..###..#", ".#.....#.", "..#####.."}),
            Map.entry("window", new String[]{
                    "#########", "#.......#", "#########", "#...#...#", "#...#...#",
                    "#########", "#...#...#", "#...#...#", "#########"}),
            Map.entry("diamond", new String[]{
                    ".........", "..#####..", ".#######.", "#########", ".#######.",
                    "..#####..", "...###...", "....#....", "........."}),
            Map.entry("chest", new String[]{
                    ".........", ".#######.", "#########", "#...#...#", "#########",
                    "#...#...#", "#.......#", "#########", "........."}),
            Map.entry("skull", new String[]{
                    "..#####..", ".#######.", "#.#####.#", "##.###.##", ".#######.",
                    "..#.#.#..", "..#####..", "........."  , "........."}));

    /** Draws the icon centered on (cx, cy). {@code scale} is the pixel size. */
    static void draw(GuiGraphicsExtractor g, String name, int cx, int cy, int scale, int color) {
        String[] rows = ICONS.get(name);
        if (rows == null) return;
        int x0 = cx - (9 * scale) / 2;
        int y0 = cy - (9 * scale) / 2;
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == '#') g.fill(x0 + x * scale, y0 + y * scale, x0 + (x + 1) * scale, y0 + (y + 1) * scale, color);
            }
        }
    }
}
