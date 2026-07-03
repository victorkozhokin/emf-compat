# EMF Compat: Corpse

A small client-side mod that makes **[Corpse](https://modrinth.com/mod/corpse)** death bodies stay still when **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models are active.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Features

- Corpses keep their intended death pose instead of twitching or walking.
- Prevents resource-pack player animations from leaking onto dead bodies.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — death bodies keep their pose even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — corpse poses stay correct when FPM is enabled.

## Build

```bash
./gradlew :corpse-neoforge-1.21.1:build
./gradlew :corpse-forge-1.20.1:build
```

enjoy ^_^
