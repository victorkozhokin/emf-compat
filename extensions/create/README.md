# EMF Compat: Create

## [Modrinth](https://modrinth.com/mod/create-emf-compat-skyhook)

A small client-side mod for **Minecraft** that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while using various **Create** features.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player-animation resource pack.

## Video

### Skyhook and Grab Physics Objects
[![EMF Skyhook Animation Pause](https://img.youtube.com/vi/072A-CVufho/maxresdefault.jpg)](https://youtu.be/072A-CVufho?si=IvY1qwvvB6avab2r)

### Aeronautics update
[![Create Aeronautics EMF Compat](https://img.youtube.com/vi/YHm2dS88qz8/maxresdefault.jpg)](https://youtu.be/YHm2dS88qz8)

## Features

- Pauses EMF player animations during **Skyhook** riding.
- Works with chains, ropes and related Create Skyhook mechanics.
- Compatible with **Fresh Animations: Player Extension**.
- Also pauses animations while **grabbing physics objects**.

## Additional compatibility (NeoForge 1.21.1 only)

- **[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)**
- **[Climbable Ropes](https://modrinth.com/mod/create-aeronautics-climbable-rope)**
- **[Create Grappling Hooks](https://modrinth.com/mod/create-grappling-hooks)**
- **[Sable Player Ragdoll](https://github.com/Leo-T22/sable-player-ragdoll)**

## Supported versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Fabric | 1.21.11, 26.1.2, 26.2 |

## Dependencies

**Fabric**
- [Fabric Loader](https://fabricmc.net) 0.19+
- [Create Fly](https://modrinth.com/mod/create-fly) 6.0.9+

**NeoForge**
- [NeoForge](https://neoforged.net/) 21.1+
- [Create](https://modrinth.com/mod/create) 6.0.10+

**General**
- [EMF Compat Core](../../core/README.md)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — when FPM is installed and enabled, pose capture and restoration are not skipped in first person, so third-person animations still apply to the visible body.

## Known Limitations

> The mod uses a pose save/restore workaround instead of the official EMF `pauseCustomAnimationsForThesePartsOfEntity` API. The API may not work correctly with ASM math compilation enabled (default in EMF 3.2.4+), though this is only a suspected issue on our end.

## Build

```bash
./gradlew :create-neoforge-1.21.1:build
./gradlew :create-fabric-1.21.11:build
./gradlew :create-fabric-26.1.2:build
./gradlew :create-fabric-26.2:build
```
