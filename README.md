# Hawkeye (Fabric, Minecraft 26.3)

Client-side utility mod. Open the menu in-game with **Right Shift**.
Minecraft 26.x is unobfuscated, so this project uses Mojang's official names and needs **Java 25**.

## Features
- **Ore ESP**: ores glow through walls (filled box + glow halo + outline, optional pulse and tracers). Per-ore toggle and colour.
- **Terrain Xray**: hides every block except the ores you enabled (key: X). Does not work with Sodium installed.
- **Block ESP**: highlight any blocks you choose through walls (obsidian, TNT, end portal frames, vaults, ... 30+ presets). Add any other block by aiming at it and pressing the "Block ESP: add / toggle looked-at block" key (set it in Controls), or open the menu while aiming at it and click the add button. Each block has its own colour and on/off switch, and custom blocks can be removed with the x.
- **Storage ESP**: outlines + tracers for chests, trapped chests, ender chests, barrels, shulker boxes (dye-coloured), hoppers, dispensers/droppers, furnaces, brewing stands, beacons, enchanting tables, spawners, crafters, decorated pots, chiseled bookshelves. Every type has its own Show and Tracer switch and colour.
- **Base Finder**: clusters nearby containers; clusters above a threshold get a light pillar, footprint box, tracer and a HUD readout with distance and direction.
- **Entity ESP**: players, hostile mobs, animals/villagers, dropped items.
- **Fullbright**, **Auto Sprint**, **Flight** (creative-style; the server must allow it).
- Module list on the HUD, JSON config at `config/hawkeye.json`, PANIC button.

All through-wall drawing uses Minecraft's built-in gizmo system with "always on top", so there is no custom shader code to break between versions.

## Build
Needs JDK 25.

    ./gradlew build

The jar is in `build/libs/` (use the one without `-sources`). Or push to GitHub: the included workflow
(`.github/workflows/build.yml`) builds it and uploads the jar as an artifact.

## Install in the Modrinth App
Create/open a Fabric 26.3 profile with **Fabric API**, then drop the jar into the profile's `mods` folder.
