# EMF Compat: Player Animator

A small client-side mod for **Minecraft** that preserves **[Player Animator](https://modrinth.com/mod/playeranimator)** poses when **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations are active.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player-animation resource pack.

## What it does

Player Animator (used by Better Combat and other mods) produces full-body player animations. EMF resource-pack animations normally overwrite those poses. This addon captures the full body pose (`head`, `body`, `left_arm`, `right_arm`, `left_leg`, `right_leg`) while a Player Animator animation is active and lets EMF Compat Core restore it after EMF runs.

## Supported versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Dependencies

**NeoForge**
- [NeoForge](https://neoforged.net/) 21.1+
- [Player Animator](https://modrinth.com/mod/playeranimator) 2.0.4+

**General**
- [EMF Compat Core](../../core/README.md)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — when FPM is installed and enabled, pose capture and restoration are not skipped in first person, so Player Animator poses still apply to the visible body.

## Known Limitations

> The mod uses a pose save/restore workaround instead of the official EMF `pauseCustomAnimationsForThesePartsOfEntity` API. The API may not work correctly with ASM math compilation enabled (default in EMF 3.2.4+), though this is only a suspected issue on our end.

## Build

```bash
./gradlew :player-animator-neoforge-1.21.1:build
```
