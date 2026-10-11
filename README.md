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

**Spawner Finder**: spawners, optional trial spawners, chat alert on new finds.

Shared customization: mode (Outline / Filled / Outline + Fill / Corners), outline opacity,
fill opacity, line width, color mode (Static / Rainbow / Pulse), box expand, range,
distance fade, through-walls, tracers (+ opacity), scan interval.

## Version-sensitive spots (26.2 is new, fix here first if it fails to compile)

- `render/EspDraw.java`: all world drawing, via `net.minecraft.gizmos.Gizmos`.
- `NovaClient.java`: `LevelRenderEvents.END_EXTRACTION` hook and `KeyMapping` constructor.
  If gizmos throw "no collector", try `LevelRenderEvents.DEBUG_RENDER` instead.
- `gui/Draw.java` and `gui/ClickGui.java`: `GuiGraphicsExtractor` calls and the
  `MouseButtonEvent` / `KeyEvent` input signatures.
- `EspModule.render`: `Camera.position()`.

To check a real signature, run `javap -p` on the deobfuscated jar under
`~/.gradle/caches/fabric-loom/`.
