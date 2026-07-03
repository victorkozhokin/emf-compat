# Create: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while using **[Create](https://modrinth.com/mod/create)**'s **Skyhook** feature, including chain and rope riding from Aeronautics.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also added compat for Grabbing Physics Objects!**

## Features

- Pauses EMF player animations during Skyhook riding
- Works with chains and ropes from Create Skyhook mechanics
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**
- Should work with most player animation resource packs using EMF

## Additional Compatibility

**[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)**

**[Climbable Ropes](https://modrinth.com/mod/create-aeronautics-climbable-rope)**

**[Create Grappling Hooks](https://modrinth.com/mod/create-grappling-hooks)**

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — pose capture/restore continues while the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — Skyhook poses stay visible on the visible body in first person.

## Build

```bash
./gradlew :create-neoforge-1.21.1:build
./gradlew :create-fabric-1.21.11:build
./gradlew :create-fabric-26.1.2:build
./gradlew :create-fabric-26.2:build
```

enjoy ^_^
