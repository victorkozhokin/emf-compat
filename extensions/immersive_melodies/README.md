# EMF Compat: Immersive Melodies

A small client-side mod for **Minecraft** that preserves **[Immersive Melodies](https://modrinth.com/mod/immersive-melodies)** instrument-playing arm poses when **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations are active.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player-animation resource pack.

## What it does

Immersive Melodies animates the player's arms while holding or playing instruments. EMF resource-pack animations normally overwrite those poses. This addon captures the arm poses after Immersive Melodies sets them and restores them after EMF runs, so the instrument posture stays visible.

## Supported versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Forge | 1.20.1 |

## Dependencies

**Forge**
- [Forge](https://files.minecraftforge.net/) 47.3+
- [Immersive Melodies](https://modrinth.com/mod/immersive-melodies) for Forge 1.20.1

**NeoForge**
- [NeoForge](https://neoforged.net/) 21.1+
- [Immersive Melodies](https://modrinth.com/mod/immersive-melodies) for NeoForge 1.21.1

**General**
- [EMF Compat Core](../../core/README.md)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — when FPM is installed and enabled, pose capture and restoration are not skipped in first person, so instrument poses still apply to the visible body.

## Known Limitations

> The mod uses a pose save/restore workaround instead of the official EMF `pauseCustomAnimationsForThesePartsOfEntity` API. The API may not work correctly with ASM math compilation enabled (default in EMF 3.2.4+), though this is only a suspected issue on our end.

## Build

```bash
./gradlew :immersive-melodies-neoforge-1.21.1:build
./gradlew :immersive-melodies-forge-1.20.1:build
```
