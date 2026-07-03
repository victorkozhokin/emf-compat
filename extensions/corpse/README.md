# Corpse: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while **[Corpse](https://modrinth.com/mod/corpse)** is rendering a dead body.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also forces the vanilla model so bodies stay perfectly still!**

## Features

- Pauses EMF player animations during Corpse rendering
- Forces the vanilla player model for Corpse bodies
- Prevents EMF animations from making corpses twitch or move
- Keeps Corpse's intended death pose
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**
- Should work with most player animation resource packs using EMF

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — pose capture/restore continues while the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — death bodies keep their intended pose even when FPM is enabled.

## Build

```bash
./gradlew :corpse-neoforge-1.21.1:build
./gradlew :corpse-forge-1.20.1:build
```

enjoy ^_^
