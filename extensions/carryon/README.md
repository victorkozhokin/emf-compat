# EMF Compat: Carry On

A small client-side mod for **Minecraft** that preserves **[Carry On](https://modrinth.com/mod/carry-on)** carry poses when **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations are active.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player-animation resource pack.

## What it does

Carry On raises the player's arms while carrying blocks and entities. EMF resource-pack animations normally overwrite that pose. This addon:

- Captures the raised carry pose after Carry On sets it and restores it after EMF runs.
- Syncs carried objects in third person to the player's EMF-animated torso.
- Renders carried entities with the vanilla model so they don't inherit conflicting resource-pack animations.
- Keeps the carry pose visible in first person.

## Supported versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Forge | 1.20.1 |
| Fabric | 1.21.11 |

## Dependencies

**Fabric**
- [Fabric Loader](https://fabricmc.net) 0.19+
- [Carry On](https://modrinth.com/mod/carry-on) 2.9.0+

**Forge**
- [Forge](https://files.minecraftforge.net/) 47.3+
- [Carry On](https://www.curseforge.com/minecraft/mc-mods/carry-on) for 1.20.1

**NeoForge**
- [NeoForge](https://neoforged.net/) 21.1+
- [Carry On](https://modrinth.com/mod/carry-on) for NeoForge 1.21.1

**General**
- [EMF Compat Core](../../core/README.md)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — when FPM is installed and enabled, the carry pose stays visible on the visible body in first person.

## Cross-mod notes

- **[Corpse](https://modrinth.com/mod/corpse)** — Corpse dummy players/skeletons are excluded from Carry On tracking so they don't interfere with the carry logic.

## Known Limitations

> The mod uses a pose save/restore workaround instead of the official EMF `pauseCustomAnimationsForThesePartsOfEntity` API. The API may not work correctly with ASM math compilation enabled (default in EMF 3.2.4+), though this is only a suspected issue on our end.

## Build

```bash
./gradlew :carryon-neoforge-1.21.1:build
./gradlew :carryon-forge-1.20.1:build
./gradlew :carryon-fabric-1.21.11:build
```
