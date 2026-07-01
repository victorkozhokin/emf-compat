# EMF Compat: Better Combat

A small client-side mod for **Minecraft** that preserves **[Better Combat](https://modrinth.com/mod/better-combat)** attack poses when **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations are active.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player-animation resource pack.

## What it does

Better Combat replaces vanilla weapon-swing animations with dynamic attacks. When EMF resource-pack animations are active, those raised/charging arm poses can be overwritten. This addon captures the arm (and jacket) pose from Better Combat and lets EMF Compat Core restore it after EMF runs.

It also tones down two Better Combat effects that clash with EMF animations:

- Reduces the global pitch-based body adjustment during attacks (body contribution scaled from `0.75` to `0.05`).
- Reduces the aggressive torso rotation/translation of the mace slam animation to `35 %`.

## Supported versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Fabric | 1.21.11 |

## Dependencies

**Fabric**
- [Fabric Loader](https://fabricmc.net) 0.19+
- [Better Combat](https://modrinth.com/mod/better-combat) 3.0.1+
- [PlayerAnimator](https://modrinth.com/mod/playeranimator) 1.1.7+

**NeoForge**
- [NeoForge](https://neoforged.net/) 21.1+
- [Better Combat](https://modrinth.com/mod/better-combat) 2.3.2+
- [PlayerAnimator](https://modrinth.com/mod/playeranimator) 2.0.4+

**General**
- [EMF Compat Core](../../core/README.md)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — when FPM is installed and enabled, pose capture and restoration are not skipped in first person, so attack poses still apply to the visible body.

## Known Limitations

> The mod uses a pose save/restore workaround instead of the official EMF `pauseCustomAnimationsForThesePartsOfEntity` API. The API may not work correctly with ASM math compilation enabled (default in EMF 3.2.4+), though this is only a suspected issue on our end.

## Build

```bash
./gradlew :better-combat-neoforge-1.21.1:build
./gradlew :better-combat-fabric-1.21.11:build
```
