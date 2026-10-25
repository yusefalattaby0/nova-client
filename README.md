# Nova Client (Fabric, Minecraft 26.2)

Client-side Fabric mod with a ClickGUI, **Storage Finder** and **Spawner Finder**.

## Build

Needs JDK 25. This folder has no Gradle wrapper, so copy `gradlew`, `gradlew.bat` and `gradle/`
from https://github.com/FabricMC/fabric-example-mod (or run `gradle wrapper`), then:

    ./gradlew build        # jar in build/libs/
    ./gradlew runClient    # test in a dev client

Bump `fabric_version` in `gradle.properties` to the latest 26.2 build if needed.

## Use

- **Right Shift** opens/closes the ClickGUI (rebindable in Controls).
- Left-click a module to toggle it, right-click (or the arrow) to expand settings.
- Enum buttons: left-click next, right-click previous. Sliders drag. Color rows open an HSB picker.
- Settings save to `config/nova.json` when the GUI closes and on exit.

## Modules

**Storage Finder**: chests (incl. trapped/copper), ender chests, barrels, shulker boxes,
droppers, dispensers, hoppers. Per-type toggles and colors. Double chests draw as one box.

**Spawner Finder**: spawners and optional trial spawners.

**Ore Finder**: diamond, emerald, ancient debris, gold, iron, redstone, lapis, copper, coal, nether gold and quartz. Per-ore toggles and colors, height range, scan speed.

**Respawn Anchor Finder**: respawn anchors, with charged and uncharged in different colors.

**Base Finder**: groups player-made blocks into sites and guesses Stash / Base / Farm / Unknown with a confidence %. Shows a list in the top-left, markers in the world, and a tooltip when you aim at one. Tags possibly raided sites. Also shows amethyst growth stages.

**Ancient Debris Finder**: highlights ancient debris and groups nearby pieces into labeled areas with a count and Y range.

**Entity ESP**: players, zombie villagers, zombified piglins, villagers, hostile mobs, animals, water mobs, items, XP orbs, projectiles, boats/minecarts, armor stands, everything else. Each group is a toggle with its own color.

**Auto Totem** (Combat tab): keeps a totem in your offhand, refills right after a pop. Optional health threshold and delay.

**Fast Crystal** (Combat tab): Instant Break hides a crystal the moment you hit it so you can place the next one sooner. Optional Auto Break while your crosshair is on a crystal.

**Freecam**: detached camera. Press V (rebindable). WASD to fly, Space up, Shift down, Sprint to go faster. Your player stays still and clicks are blocked while it is on.

Shared customization: mode (Outline / Filled / Outline + Fill / Corners), outline opacity,
fill opacity, line width, color mode (Static / Rainbow / Pulse), box expand, range,
distance fade, through-walls, tracers (+ opacity), scan interval.

## New: map, wheel, region ping, auto RTP

- **Base Finder map** (press **B**): chunk map around you with three layers (Chunk Analysis, Live Players, Resources),
  a signals panel and a "Likely Base" box. Needs Base Finder on. Click the colored chips to hide a layer.
  Player names and dotted paths show up when **Trails** is on.
- **Donut Wheel** (hold **G**, point at a slice, let go): /rtp, /ah, Kit PvP (macro, sends `/kit pvp`), ESP (toggle),
  Trails (toggle), /spawn, /home, /sell. Commands go to the server exactly as if typed in chat.
- **Region Map** (press **N**): dotted world map with live pings for EU Central, NA East, NA West, Asia and Oceania,
  a ranked list and a Connect button. Put server addresses in `config/nova-regions.json` (created on first use).
  The ping is the connection time to a public cloud endpoint in each region.
- **Auto RTP** (Utility tab): sends /rtp, waits, checks the landing spot (min distance, water, player-made blocks nearby
  as a "claimed" guess) and retries. Panel top-right, log bottom-left.
- **Coords HUD**: XYZ and biome box. Auto RTP shows it too.
- **Trails** (Render tab): paths behind nearby players.

All keys can be changed in Controls under Nova Client.

## Version-sensitive spots (26.2 is new, fix here first if it fails to compile)

- `render/EspDraw.java`: all world drawing, via `net.minecraft.gizmos.Gizmos`.
- `NovaClient.java`: `LevelRenderEvents.END_EXTRACTION` hook and `KeyMapping` constructor.
  If gizmos throw "no collector", try `LevelRenderEvents.DEBUG_RENDER` instead.
- `gui/Draw.java` and `gui/ClickGui.java`: `GuiGraphicsExtractor` calls and the
  `MouseButtonEvent` / `KeyEvent` input signatures.
- `EspModule.render`: `Camera.position()`.
- `util/Compat.java`: calls made by name (send command, biome name, key lookup, clipboard). If one stops working it fails quietly.
- `util/RegionPing.java`: connecting to a server is also done by name, since that call changes a lot between versions.
- `gui/BaseMapScreen.java`: terrain colors use `BlockState.getMapColor` and `Level.getHeight`.

To check a real signature, run `javap -p` on the deobfuscated jar under
`~/.gradle/caches/fabric-loom/`.
