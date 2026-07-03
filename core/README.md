# EMF Compat Core

## [Modrinth](https://modrinth.com/project/emf-compat-core)

Shared framework for the **EMF Compat** family of mods.

EMF Compat Core provides the pose snapshot/restore system and first-person checks used by all addons. It does not add any visible gameplay features on its own, but it is **required** by:

- [EMF Compat: Create](../extensions/create/README.md)
- [EMF Compat: Not Enough Animations](../nea/README.md)

## What it does

When another mod plays its own player animation (for example Create Skyhook or Not Enough Animations), the addon captures the current pose of the player's arms before EMF overrides it, then restores that pose so the external animation is visible instead of the resource-pack animation.

The core module contains:

- `PoseSnapshot` / `SavedPoses` — immutable pose snapshots.
- `PoseManager` — per-entity pose storage and cleanup.
- `EMFCompatCore` — helpers such as first-person detection.
- Shared mixins that hook into EMF's pose application.

## Dependencies

- **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** 3.2.4+
- **[Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures)** (required by EMF)

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Fabric | 1.21.11, 26.1.2, 26.2 |

## Build

```bash
./gradlew :core-neoforge-1.21.1:build
./gradlew :core-fabric-1.21.11:build
./gradlew :core-fabric-26.1.2:build
./gradlew :core-fabric-26.2:build
```
