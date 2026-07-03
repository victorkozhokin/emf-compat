# EMF Compat: Create

## [Modrinth](https://modrinth.com/project/emf-compat-create)

A small client-side mod that makes **[Create](https://modrinth.com/mod/create)** player animations work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also works when grabbing physics objects!**

## Features

- Your arms and body stay in the Skyhook riding pose while flying on chains and ropes.
- Riding Create's chains and ropes looks right with animated EMF models.
- Grappling hook poses show correctly on NeoForge.
- Grabbed physics objects don't conflict with EMF animations.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

## Additional Compatibility

**[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)**

**[Climbable Ropes](https://modrinth.com/mod/create-aeronautics-climbable-rope)**

**[Create Grappling Hooks](https://modrinth.com/mod/create-grappling-hooks)**

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — Skyhook poses stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — Skyhook poses stay visible on your body in first person.

## Build

```bash
./gradlew :create-neoforge-1.21.1:build
./gradlew :create-fabric-1.21.11:build
./gradlew :create-fabric-26.1.2:build
./gradlew :create-fabric-26.2:build
```

enjoy ^_^
